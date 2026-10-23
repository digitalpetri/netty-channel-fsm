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

import static com.digitalpetri.netty.fsm.FsmTestSupport.stepConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.fsm.FsmContext;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The FSM closes a channel when an exception reaches the end of its pipeline.
 */
class ChannelExceptionTest {

  private final QueuedExecutor executor = new QueuedExecutor();
  private final EmbeddedChannel channel = new EmbeddedChannel();

  @AfterEach
  void releaseChannel() {
    channel.finishAndReleaseAll();
  }

  @Test
  @DisplayName("An exception while CONNECTED closes the channel")
  void exceptionWhileConnectedClosesChannel() {
    var actions = new RecordingChannelActions();
    final ChannelFsm fsm = connect(actions);

    channel.pipeline().fireExceptionCaught(new Exception("decoder failed"));
    executor.drain();

    assertFalse(channel.isOpen());
    assertEquals(State.ReconnectWait, fsm.getState());
  }

  // The disconnect action may wait for the peer to close the channel, as Milo's does after
  // sending CloseSecureChannelRequest. An exception in the meantime must not leave it open.
  @Test
  @DisplayName("An exception while DISCONNECTING closes the channel")
  void exceptionWhileDisconnectingClosesChannel() {
    var disconnect = new CompletableFuture<Void>();
    var actions = new RecordingChannelActions() {
      @Override
      public CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel) {
        disconnects.add(channel);
        channel.closeFuture().addListener(f -> disconnect.complete(null));
        return disconnect;
      }
    };
    ChannelFsm fsm = connect(actions);

    fsm.disconnect();
    executor.drain();
    assertEquals(State.Disconnecting, fsm.getState());

    channel.pipeline().fireExceptionCaught(new Exception("decoder failed"));
    executor.drain();

    assertFalse(channel.isOpen());
    assertTrue(disconnect.isDone());
    assertEquals(State.NotConnected, fsm.getState());
  }

  private ChannelFsm connect(RecordingChannelActions actions) {
    ChannelFsmConfig config = stepConfig(executor, new ManualScheduler(), actions).build();
    ChannelFsm fsm = new ChannelFsmFactory(config).newChannelFsm();

    fsm.connect();
    executor.drain();
    actions.lastConnect().complete(channel);
    executor.drain();
    assertEquals(State.Connected, fsm.getState());

    return fsm;
  }

}
