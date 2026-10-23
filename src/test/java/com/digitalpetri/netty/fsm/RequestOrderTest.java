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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.fsm.FsmContext;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The FSM applies connect() and disconnect() calls in the order they were made, so the last call
 * decides where it ends up, even when a call arrives while an earlier one is still in progress.
 */
class RequestOrderTest {

  private final QueuedExecutor executor = new QueuedExecutor();
  private final ManualScheduler scheduler = new ManualScheduler();
  private final List<EmbeddedChannel> channels = new ArrayList<>();
  private final List<CompletableFuture<Void>> pendingDisconnects =
      new CopyOnWriteArrayList<>();

  // The disconnect action completes when the test says so, like Milo's, which waits for the
  // server to close the channel after CloseSecureChannelRequest.
  private final RecordingChannelActions actions = new RecordingChannelActions() {
    @Override
    public CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel) {
      var disconnect = new CompletableFuture<Void>();
      disconnect.thenRun(channel::close);
      pendingDisconnects.add(disconnect);
      return disconnect;
    }
  };

  @AfterEach
  void releaseChannels() {
    channels.forEach(EmbeddedChannel::finishAndReleaseAll);
  }

  @Test
  @DisplayName("connect(), disconnect(), connect() while CONNECTING ends CONNECTED")
  void connectAfterDisconnectWhileConnecting() {
    connectAfterDisconnectWhileAttemptInProgress(State.NotConnected, State.Connecting);
  }

  @Test
  @DisplayName("connect(), disconnect(), connect() while RECONNECTING ends CONNECTED")
  void connectAfterDisconnectWhileReconnecting() {
    connectAfterDisconnectWhileAttemptInProgress(State.Idle, State.Reconnecting);
  }

  @Test
  @DisplayName("disconnect(), connect(), disconnect() while DISCONNECTING ends NOT_CONNECTED")
  void disconnectAfterConnectWhileDisconnecting() {
    ChannelFsm fsm = newFsm(State.NotConnected);
    fsm.connect();
    settle();
    assertEquals(State.Connected, fsm.getState());

    final CompletableFuture<Void> first = fsm.disconnect();
    executor.drain();
    assertEquals(State.Disconnecting, fsm.getState());

    final CompletableFuture<Channel> connected = fsm.connect();
    final CompletableFuture<Void> last = fsm.disconnect();
    settle();

    assertTrue(first.isDone());
    assertTrue(connected.isDone());
    assertTrue(last.isDone());
    assertEquals(State.NotConnected, fsm.getState(), "the last call was disconnect()");
    assertEquals(List.of(), openChannels());
  }

  // Milo's ChannelActions.connect fails an attempt it finds after transport.disconnect(). A
  // Connect replayed after the last disconnect() then puts the persistent FSM into a reconnect
  // loop that only another disconnect() ends.
  @Test
  @DisplayName(
      "A Milo-style transport stays disconnected after disconnect(), connect(), disconnect()")
  void miloStyleTransportStaysDisconnected() {
    var connectionRequested = new AtomicBoolean();
    var miloActions = new RecordingChannelActions() {
      @Override
      public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
        CompletableFuture<Channel> future = super.connect(ctx);
        if (connectionRequested.get()) {
          future.complete(newChannel());
        } else {
          future.completeExceptionally(new Exception("Bad_ConnectionClosed"));
        }
        return future;
      }

      @Override
      public CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel) {
        return actions.disconnect(ctx, channel);
      }
    };
    ChannelFsm fsm = new ChannelFsmFactory(
        stepConfig(executor, scheduler, miloActions).build()
    ).newChannelFsm();

    connectionRequested.set(true);
    fsm.connect();
    executor.drain();
    assertEquals(State.Connected, fsm.getState());

    connectionRequested.set(false);
    fsm.disconnect();
    executor.drain();
    connectionRequested.set(true);
    fsm.connect();
    executor.drain();
    connectionRequested.set(false);
    final CompletableFuture<Void> last = fsm.disconnect();
    executor.drain();

    // The server closes the channel in response to CloseSecureChannelRequest.
    pendingDisconnects.get(0).complete(null);
    executor.drain();
    final int attempts = miloActions.connects.size();

    for (int i = 0; i < 3; i++) {
      scheduler.runPending();
      executor.drain();
    }

    assertTrue(last.isDone());
    assertEquals(State.NotConnected, fsm.getState());
    assertEquals(attempts, miloActions.connects.size(), "attempts after the last disconnect()");
  }

  private void connectAfterDisconnectWhileAttemptInProgress(State initial, State attempting) {
    ChannelFsm fsm = newFsm(initial);
    fsm.connect();
    executor.drain();
    assertEquals(attempting, fsm.getState());

    final CompletableFuture<Void> disconnected = fsm.disconnect();
    final CompletableFuture<Channel> reconnected = fsm.connect();
    settle();

    assertTrue(disconnected.isDone());
    assertEquals(State.Connected, fsm.getState(), "the last call was connect()");
    assertTrue(reconnected.join().isOpen(), "connect() completes with the open channel");
  }

  private ChannelFsm newFsm(State initial) {
    ChannelFsmConfig config = stepConfig(executor, scheduler, actions).build();

    return new ChannelFsmFactory(config).newChannelFsm(initial);
  }

  /**
   * Complete every pending connect attempt with a new channel and every pending disconnect, and
   * run executor tasks, until nothing is pending.
   */
  private void settle() {
    executor.drain();
    for (int i = 0; i < 10; i++) {
      boolean completed = false;
      for (CompletableFuture<Channel> connect : actions.connects) {
        if (!connect.isDone()) {
          completed |= connect.complete(newChannel());
        }
      }
      for (CompletableFuture<Void> disconnect : pendingDisconnects) {
        completed |= disconnect.complete(null);
      }
      executor.drain();
      if (!completed) {
        return;
      }
    }
    throw new IllegalStateException("connects and disconnects did not settle");
  }

  private EmbeddedChannel newChannel() {
    var channel = new EmbeddedChannel();
    channels.add(channel);
    return channel;
  }

  private List<Channel> openChannels() {
    List<Channel> open = new ArrayList<>();
    for (EmbeddedChannel channel : channels) {
      if (channel.isOpen()) {
        open.add(channel);
      }
    }
    return open;
  }

}
