/*
 * Copyright (c) 2026 Kevin Herron
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.digitalpetri.netty.fsm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.digitalpetri.fsm.FsmContext;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChannelClosedTest {

  private final QueuedExecutor executor = new QueuedExecutor();
  private final TestScheduler scheduler = new TestScheduler();
  private final ArrayDeque<CompletableFuture<Channel>> connections = new ArrayDeque<>();
  private final EmbeddedChannel channel = new EmbeddedChannel();

  @AfterEach
  void releaseChannel() {
    channel.finishAndReleaseAll();
  }

  @Test
  @DisplayName("Closing the channel while CONNECTED transitions to RECONNECT_WAIT")
  void closingChannelWhileConnectedTransitionsToReconnectWait() {
    ChannelFsm fsm = newChannelFsm(State.NotConnected);

    fsm.connect();
    executor.drain();
    connections.removeFirst().complete(channel);
    executor.drain();
    assertEquals(State.Connected, fsm.getState());

    channel.close();
    executor.drain();

    assertEquals(State.ReconnectWait, fsm.getState());
  }

  // The peer can close the connection while its last handshake message is still being handled,
  // e.g. when its FIN arrives together with that message. ConnectSuccess is evaluated on the
  // executor, so the channel may already be closed when the FSM enters CONNECTED with it. The FSM
  // must still leave CONNECTED; otherwise it hands out the closed channel until disconnected.
  @Test
  @DisplayName(
      "Channel closed before ConnectSuccess is evaluated still reconnects after connecting")
  void channelClosedBeforeConnectSuccessReconnectsAfterConnecting() {
    closedBeforeConnectSuccess(State.NotConnected, new Event.Connect());
  }

  @Test
  @DisplayName(
      "Channel closed before ConnectSuccess is evaluated still reconnects after reconnecting")
  void channelClosedBeforeConnectSuccessReconnectsAfterReconnecting() {
    closedBeforeConnectSuccess(State.ReconnectWait, new Event.ReconnectDelayElapsed());
  }

  private void closedBeforeConnectSuccess(State initial, Event startConnecting) {
    ChannelFsm fsm = newChannelFsm(initial);

    fsm.getFsm().fireEvent(startConnecting);
    executor.drain();

    // Completing the connect future queues ConnectSuccess on the executor. The channel closes
    // before the executor gets to evaluate it.
    connections.removeFirst().complete(channel);
    channel.close();
    executor.drain();

    assertEquals(State.ReconnectWait, fsm.getState());

    scheduler.execute();
    executor.drain();

    assertEquals(State.Reconnecting, fsm.getState());
    assertEquals(1, connections.size(), "a new connection attempt was started");
  }

  private ChannelFsm newChannelFsm(State initial) {
    var actions = new ChannelActions() {
      @Override
      public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
        var connection = new CompletableFuture<Channel>();
        connections.addLast(connection);
        return connection;
      }

      @Override
      public CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel) {
        return CompletableFuture.completedFuture(null);
      }
    };

    ChannelFsmConfig config = ChannelFsmConfig.newBuilder()
        .setExecutor(executor)
        .setScheduler(scheduler)
        .setLazy(false)
        .setPersistent(true)
        .setMaxIdleSeconds(0)
        .setChannelActions(actions)
        .build();

    return new ChannelFsmFactory(config).newChannelFsm(initial);
  }

}
