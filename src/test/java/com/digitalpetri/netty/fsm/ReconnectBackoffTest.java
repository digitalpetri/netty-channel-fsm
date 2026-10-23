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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The delay between reconnect attempts doubles from 1 second up to maxReconnectDelaySeconds,
 * rounded up to a power of 2.
 */
class ReconnectBackoffTest {

  private final QueuedExecutor executor = new QueuedExecutor();
  private final ManualScheduler scheduler = new ManualScheduler();

  private final RecordingChannelActions failingActions = new RecordingChannelActions() {
    @Override
    public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
      CompletableFuture<Channel> future = super.connect(ctx);
      future.completeExceptionally(new Exception("connection refused"));
      return future;
    }
  };

  @Test
  @DisplayName("Backoff with maxReconnectDelaySeconds = 2^30 doubles from 1 second")
  void backoffWithLargestPowerOfTwo() {
    assertEquals(List.of(1L, 2L, 4L, 8L, 16L, 32L), delaysWithMax(1 << 30));
  }

  // Integer.MAX_VALUE is a natural way to ask for no cap.
  @Test
  @DisplayName("Backoff with maxReconnectDelaySeconds above 2^30 doubles from 1 second")
  void backoffAboveLargestPowerOfTwo() {
    assertEquals(List.of(1L, 2L, 4L, 8L, 16L, 32L), delaysWithMax((1 << 30) + 1));
    assertEquals(List.of(1L, 2L, 4L, 8L, 16L, 32L), delaysWithMax(Integer.MAX_VALUE));
  }

  // ScheduledFuture.cancel(false) returns true for a task that has already started, and the task
  // keeps running, so its ReconnectDelayElapsed can arrive during a later ReconnectWait.
  @Test
  @DisplayName("A ReconnectDelayElapsed from an earlier wait does not end the current wait")
  void staleReconnectDelayElapsed() {
    ChannelFsmConfig config = stepConfig(executor, scheduler, failingActions).build();
    ChannelFsm fsm = new ChannelFsmFactory(config).newChannelFsm();

    fsm.connect();
    executor.drain();
    assertEquals(State.ReconnectWait, fsm.getState());
    final ManualScheduler.Scheduled firstWait = scheduler.all().get(0);

    fsm.disconnect();
    executor.drain();
    assertEquals(State.NotConnected, fsm.getState());
    assertTrue(firstWait.isCancelled());

    fsm.connect();
    executor.drain();
    assertEquals(State.ReconnectWait, fsm.getState());
    assertEquals(2, failingActions.connects.size());

    // The first wait's timer was already running when disconnect() cancelled it.
    firstWait.run();
    executor.drain();

    assertEquals(2, failingActions.connects.size(), "the second wait has not elapsed");
    assertEquals(State.ReconnectWait, fsm.getState());
  }

  // e.g. a ScheduledExecutorService that was shut down while the FSM was still reconnecting
  @Test
  @DisplayName("A Scheduler that throws does not leave the FSM waiting to reconnect forever")
  void schedulerThatThrows() {
    Scheduler rejecting = (command, delay, unit) -> {
      throw new RejectedExecutionException("scheduler shut down");
    };
    ChannelFsmConfig config = stepConfig(executor, rejecting, failingActions).build();
    ChannelFsm fsm = new ChannelFsmFactory(config).newChannelFsm();

    fsm.connect();
    executor.drain();

    CompletableFuture<Channel> channel = fsm.getChannel();
    executor.drain();

    assertTrue(
        channel.isCompletedExceptionally(),
        "getChannel() must not wait for a reconnect that was never scheduled"
    );
    assertEquals(State.NotConnected, fsm.getState());
  }

  private List<Long> delaysWithMax(int maxReconnectDelaySeconds) {
    var delayScheduler = new ManualScheduler();
    ChannelFsmConfig config = stepConfig(executor, delayScheduler, failingActions)
        .setMaxReconnectDelaySeconds(maxReconnectDelaySeconds)
        .build();
    ChannelFsm fsm = new ChannelFsmFactory(config).newChannelFsm();

    fsm.connect();
    executor.drain();
    for (int i = 0; i < 5; i++) {
      delayScheduler.runPending();
      executor.drain();
    }

    assertEquals(State.ReconnectWait, fsm.getState());
    return delayScheduler.delays();
  }

}
