/*
 * Copyright (c) 2024 Kevin Herron
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.digitalpetri.netty.fsm;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.digitalpetri.fsm.FsmContext;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.function.Executable;

final class FsmTestSupport {

  private FsmTestSupport() {}

  static void assertWithTimeout(Executable executable) {
    assertWithTimeout(Duration.ofSeconds(1), executable);
  }

  static void assertWithTimeout(Duration timeout, Executable executable) {
    assertTimeoutPreemptively(timeout, executable);
  }

  static void assertEventualState(ChannelFsm fsm, State state) {
    assertWithTimeout(() -> {
      while (fsm.getFsm().getState() != state) {
        Thread.sleep(1);
      }
    });
  }

  /**
   * Start building a {@link ChannelFsmFactory} with test defaults: not lazy, not persistent, keep
   * alive disabled, connect and disconnect succeed immediately, a single thread executor, and a
   * scheduler that ignores the requested delay.
   *
   * @return a new {@link FactoryBuilder}.
   */
  static FactoryBuilder factory() {
    return new FactoryBuilder();
  }

  static ConnectProxy connectDelegate(boolean success) {
    return ctx -> success
        ? CompletableFuture.completedFuture(new EmbeddedChannel())
        : CompletableFuture.failedFuture(new Exception("failed"));
  }

  static DisconnectProxy disconnectDelegate(boolean success) {
    return (ctx, channel) -> success
        ? CompletableFuture.completedFuture(null)
        : CompletableFuture.failedFuture(new Exception("failed"));
  }

  private static Scheduler.Cancellable scheduleImmediately(
      Runnable command,
      long delay,
      TimeUnit unit
  ) {

    ScheduledFuture<?> future =
        Executors.newSingleThreadScheduledExecutor().schedule(command, 0, unit);

    return () -> future.cancel(false);
  }

  static final class FactoryBuilder {

    private boolean lazy = false;
    private boolean persistent = false;
    private int maxIdleSeconds = 0;
    private ConnectProxy connectProxy = connectDelegate(true);
    private DisconnectProxy disconnectProxy = disconnectDelegate(true);
    private KeepAliveProxy keepAliveProxy =
        (ctx, channel) -> CompletableFuture.completedFuture(null);
    private Executor executor;
    private Scheduler scheduler;

    private FactoryBuilder() {}

    FactoryBuilder lazy(boolean lazy) {
      this.lazy = lazy;
      return this;
    }

    FactoryBuilder persistent(boolean persistent) {
      this.persistent = persistent;
      return this;
    }

    FactoryBuilder maxIdleSeconds(int maxIdleSeconds) {
      this.maxIdleSeconds = maxIdleSeconds;
      return this;
    }

    FactoryBuilder connectProxy(ConnectProxy connectProxy) {
      this.connectProxy = connectProxy;
      return this;
    }

    FactoryBuilder disconnectProxy(DisconnectProxy disconnectProxy) {
      this.disconnectProxy = disconnectProxy;
      return this;
    }

    FactoryBuilder keepAliveProxy(KeepAliveProxy keepAliveProxy) {
      this.keepAliveProxy = keepAliveProxy;
      return this;
    }

    FactoryBuilder executor(Executor executor) {
      this.executor = executor;
      return this;
    }

    FactoryBuilder scheduler(Scheduler scheduler) {
      this.scheduler = scheduler;
      return this;
    }

    ChannelFsmFactory build() {
      ConnectProxy connectProxy = this.connectProxy;
      DisconnectProxy disconnectProxy = this.disconnectProxy;
      KeepAliveProxy keepAliveProxy = this.keepAliveProxy;

      var channelActions = new ChannelActions() {
        @Override
        public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
          return connectProxy.connect(ctx);
        }

        @Override
        public CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel) {
          return disconnectProxy.disconnect(ctx, channel);
        }

        @Override
        public CompletableFuture<Void> keepAlive(FsmContext<State, Event> ctx, Channel channel) {
          return keepAliveProxy.keepAlive(ctx, channel);
        }
      };

      ChannelFsmConfig config = ChannelFsmConfig.newBuilder()
          .setLazy(lazy)
          .setPersistent(persistent)
          .setMaxIdleSeconds(maxIdleSeconds)
          .setChannelActions(channelActions)
          .setExecutor(executor != null ? executor : Executors.newSingleThreadExecutor())
          .setScheduler(scheduler != null ? scheduler : FsmTestSupport::scheduleImmediately)
          .build();

      return new ChannelFsmFactory(config);
    }

  }

}
