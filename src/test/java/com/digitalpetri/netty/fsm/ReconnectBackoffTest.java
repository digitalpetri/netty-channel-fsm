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

import com.digitalpetri.fsm.FsmContext;
import io.netty.channel.Channel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The delay between reconnect attempts doubles from 1 second up to maxReconnectDelaySeconds,
 * rounded up to a power of 2.
 */
class ReconnectBackoffTest {

  private final QueuedExecutor executor = new QueuedExecutor();

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
