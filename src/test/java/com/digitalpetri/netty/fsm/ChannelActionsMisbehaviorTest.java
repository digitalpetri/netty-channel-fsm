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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.fsm.FsmContext;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ChannelActions implementations can throw, return null, or complete their futures with null.
 * The FSM must treat each of those as a failure, never get stuck, and keep its own futures
 * completing.
 */
class ChannelActionsMisbehaviorTest {

  private final QueuedExecutor executor = new QueuedExecutor().captureFailures();
  private final ManualScheduler scheduler = new ManualScheduler();

  @AfterEach
  void noExecutorTaskFailed() {
    assertEquals(List.of(), executor.failures(), "executor tasks that threw");
  }

  // Milo's and modbus's connect() build an InetSocketAddress, which throws for a port outside
  // 0-65535, e.g. from an endpoint URL like opc.tcp://host:70000.
  @Test
  @DisplayName("ChannelActions.connect that throws fails connect() and lets disconnect() finish")
  void connectThatThrows() {
    var actions = new RecordingChannelActions() {
      @Override
      public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
        new InetSocketAddress("localhost", 70000);
        throw new AssertionError("unreachable");
      }
    };
    ChannelFsm fsm = newFsm(actions);

    CompletableFuture<Channel> connected = fsm.connect();
    executor.drain();

    assertConnectFailed(connected, IllegalArgumentException.class);
    assertEquals(State.ReconnectWait, fsm.getState());

    CompletableFuture<Void> disconnected = fsm.disconnect();
    executor.drain();

    assertTrue(disconnected.isDone());
    assertEquals(State.NotConnected, fsm.getState());
  }

  @Test
  @DisplayName("ChannelActions.connect that throws while reconnecting keeps reconnecting")
  void connectThatThrowsWhileReconnecting() {
    var actions = new RecordingChannelActions() {
      @Override
      public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
        throw new IllegalStateException("bootstrap customizer failed");
      }
    };
    ChannelFsm fsm = newFsm(actions);

    fsm.connect();
    executor.drain();
    assertEquals(State.ReconnectWait, fsm.getState());

    CompletableFuture<Channel> channel = fsm.getChannel();
    scheduler.runPending();
    executor.drain();

    assertConnectFailed(channel, IllegalStateException.class);
    assertEquals(State.ReconnectWait, fsm.getState());
    assertEquals(1, scheduler.pending().size(), "another reconnect is scheduled");
  }

  @Test
  @DisplayName("ChannelActions.connect that returns null fails connect()")
  void connectThatReturnsNull() {
    var actions = new RecordingChannelActions() {
      @Override
      public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
        return null;
      }
    };
    ChannelFsm fsm = newFsm(actions);

    CompletableFuture<Channel> connected = fsm.connect();
    executor.drain();

    assertConnectFailed(connected, NullPointerException.class);
    assertEquals(State.ReconnectWait, fsm.getState());
  }

  @Test
  @DisplayName("ChannelActions.connect that completes with a null Channel fails connect()")
  void connectThatCompletesWithNull() {
    var actions = new RecordingChannelActions();
    ChannelFsm fsm = newFsm(actions);

    final CompletableFuture<Channel> connected = fsm.connect();
    executor.drain();
    actions.lastConnect().complete(null);
    executor.drain();

    assertConnectFailed(connected, IllegalStateException.class);
    assertEquals(State.ReconnectWait, fsm.getState());
  }

  private ChannelFsm newFsm(ChannelActions actions) {
    ChannelFsmConfig config = stepConfig(executor, scheduler, actions).build();

    return new ChannelFsmFactory(config).newChannelFsm();
  }

  private static void assertConnectFailed(
      CompletableFuture<Channel> future,
      Class<? extends Throwable> cause
  ) {

    assertTrue(future.isCompletedExceptionally(), "the future must fail, done=" + future.isDone());

    ExecutionException e = assertThrows(ExecutionException.class, future::get);
    assertInstanceOf(cause, e.getCause());
  }

}
