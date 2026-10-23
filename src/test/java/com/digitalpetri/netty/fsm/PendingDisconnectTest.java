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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.fsm.FsmContext;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingDisconnectTest {

  @Test
  @DisplayName("Disconnect closes a pending initial connection before completing")
  void disconnectClosesPendingInitialConnection() {
    pendingDisconnect(State.NotConnected, State.Connecting);
  }

  @Test
  @DisplayName("Disconnect closes a pending reconnection before completing")
  void disconnectClosesPendingReconnection() {
    pendingDisconnect(State.Idle, State.Reconnecting);
  }

  private static void pendingDisconnect(State initial, State connecting) {
    var executor = new QueuedExecutor();
    var channel = new EmbeddedChannel();
    var connection = new CompletableFuture<Channel>();
    var closure = new CompletableFuture<Void>();
    var disconnectCalls = new AtomicInteger();

    var actions = new ChannelActions() {
      @Override
      public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
        return connection;
      }

      @Override
      public CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel target) {
        assertSame(channel, target);
        disconnectCalls.incrementAndGet();
        target.close();
        return closure;
      }
    };

    ChannelFsmConfig config = ChannelFsmConfig.newBuilder()
        .setExecutor(executor)
        .setMaxIdleSeconds(0)
        .setLazy(true)
        .setChannelActions(actions)
        .build();
    ChannelFsm fsm = new ChannelFsmFactory(config).newChannelFsm(initial);

    try {
      final CompletableFuture<Channel> connected = fsm.connect();
      executor.drain();
      assertEquals(connecting, fsm.getState());

      CompletableFuture<Void> disconnected = fsm.disconnect();
      executor.drain();
      assertFalse(disconnected.isDone());

      connection.complete(channel);
      executor.runFirst();
      assertEquals(State.Connected, fsm.getState());

      // A pool can execute the shelved Disconnect before the separately scheduled task
      // that publishes the connected channel. Keep FSM events serialized, but run the
      // independent executor tasks in that order.
      executor.drain(true);

      assertEquals(1, disconnectCalls.get());
      assertFalse(channel.isOpen());
      assertTrue(connected.isDone());
      assertFalse(disconnected.isDone(), "disconnect must await the channel action's completion");
      assertEquals(State.Disconnecting, fsm.getState());

      closure.complete(null);
      executor.drain();

      assertTrue(disconnected.isDone());
      assertFalse(disconnected.isCompletedExceptionally());
      assertEquals(State.NotConnected, fsm.getState());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

}
