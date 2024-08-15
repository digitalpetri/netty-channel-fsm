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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ChannelFsmConfigBuilder {

  static int DEFAULT_MAX_RECONNECT_DELAY_SECONDS = 32;

  private boolean lazy = false;
  private boolean persistent = true;
  private int maxIdleSeconds = 15;
  private int maxReconnectDelaySeconds = DEFAULT_MAX_RECONNECT_DELAY_SECONDS;
  private ChannelActions channelActions;
  private Executor executor;
  private Scheduler scheduler;
  private Object context;

  /**
   * @param lazy {@code true} if the ChannelFsm should be lazy,
   * @return this {@link ChannelFsmConfigBuilder}.
   * @see ChannelFsmConfig#isLazy()
   */
  public ChannelFsmConfigBuilder setLazy(boolean lazy) {
    this.lazy = lazy;
    return this;
  }

  /**
   * @param persistent {@code true} if the ChannelFsm should be persistent in its initial
   *     connect attempt.
   * @return this {@link ChannelFsmConfigBuilder}.
   * @see ChannelFsmConfig#isPersistent()
   */
  public ChannelFsmConfigBuilder setPersistent(boolean persistent) {
    this.persistent = persistent;
    return this;
  }

  /**
   * @param maxIdleSeconds the maximum amount of time, in seconds, before a keep alive occurs on
   *     an idle channel.
   * @return this {@link ChannelFsmConfigBuilder}.
   * @see ChannelFsmConfig#getMaxIdleSeconds()
   */
  public ChannelFsmConfigBuilder setMaxIdleSeconds(int maxIdleSeconds) {
    this.maxIdleSeconds = maxIdleSeconds;
    return this;
  }

  /**
   * @param maxReconnectDelaySeconds the maximum delay to occur between reconnect attempts.
   * @return this {@link ChannelFsmConfigBuilder}.
   * @see ChannelFsmConfig#getMaxReconnectDelaySeconds()
   */
  public ChannelFsmConfigBuilder setMaxReconnectDelaySeconds(int maxReconnectDelaySeconds) {
    this.maxReconnectDelaySeconds = maxReconnectDelaySeconds;
    return this;
  }

  /**
   * @param channelActions the {@link ChannelActions} delegate.
   * @return this {@link ChannelFsmConfigBuilder}.
   * @see ChannelFsmConfig#getChannelActions()
   */
  public ChannelFsmConfigBuilder setChannelActions(ChannelActions channelActions) {
    this.channelActions = channelActions;
    return this;
  }

  /**
   * @param executor the {@link Executor} to use.
   * @return this {@link ChannelFsmConfigBuilder}.
   * @see ChannelFsmConfig#getExecutor()
   */
  public ChannelFsmConfigBuilder setExecutor(Executor executor) {
    this.executor = executor;
    return this;
  }

  /**
   * @param scheduler the {@link Scheduler} to use.
   * @return this {@link ChannelFsmConfigBuilder}.
   * @see ChannelFsmConfig#getScheduler()
   */
  public ChannelFsmConfigBuilder setScheduler(Scheduler scheduler) {
    this.scheduler = scheduler;
    return this;
  }

  /**
   * @param scheduledExecutor the {@link ScheduledExecutorService} to use.
   * @return this {@link ChannelFsmConfigBuilder}.
   * @see ChannelFsmConfig#getScheduler()
   */
  public ChannelFsmConfigBuilder setScheduler(ScheduledExecutorService scheduledExecutor) {
    this.scheduler = Scheduler.fromScheduledExecutor(scheduledExecutor);
    return this;
  }

  /**
   * @param context the user-configurable context associated with this ChannelFsm.
   * @return this {@link ChannelFsmConfigBuilder}.
   * @see ChannelFsmConfig#getContext()
   */
  public ChannelFsmConfigBuilder setContext(Object context) {
    this.context = context;
    return this;
  }

  public ChannelFsmConfig build() {
    if (channelActions == null) {
      throw new IllegalArgumentException("channelActions must be non-null");
    }
    if (maxReconnectDelaySeconds < 1) {
      maxReconnectDelaySeconds = DEFAULT_MAX_RECONNECT_DELAY_SECONDS;
    }
    if (executor == null) {
      executor = SharedExecutor.INSTANCE;
    }
    if (scheduler == null) {
      scheduler = SharedScheduler.INSTANCE;
    }

    return new ChannelFsmConfigImpl(
        lazy,
        persistent,
        maxIdleSeconds,
        maxReconnectDelaySeconds,
        channelActions,
        executor,
        scheduler,
        context
    );
  }

  private static class SharedExecutor {

    private static final ExecutorService INSTANCE = Executors.newSingleThreadExecutor();
  }

  private static class SharedScheduler {

    private static final Scheduler INSTANCE =
        Scheduler.fromScheduledExecutor(Executors.newSingleThreadScheduledExecutor());
  }

  private static class ChannelFsmConfigImpl implements ChannelFsmConfig {

    private final boolean lazy;
    private final boolean persistent;
    private final int maxIdleSeconds;
    private final int maxReconnectDelaySeconds;
    private final ChannelActions channelActions;
    private final Executor executor;
    private final Scheduler scheduler;
    private final Object context;

    ChannelFsmConfigImpl(
        boolean lazy,
        boolean persistent,
        int maxIdleSeconds,
        int maxReconnectDelaySeconds,
        ChannelActions channelActions,
        Executor executor,
        Scheduler scheduler,
        Object context
    ) {

      this.lazy = lazy;
      this.persistent = persistent;
      this.maxIdleSeconds = maxIdleSeconds;
      this.maxReconnectDelaySeconds = maxReconnectDelaySeconds;
      this.channelActions = channelActions;
      this.executor = executor;
      this.scheduler = scheduler;
      this.context = context;
    }

    @Override
    public boolean isLazy() {
      return lazy;
    }

    @Override
    public boolean isPersistent() {
      return persistent;
    }

    @Override
    public int getMaxIdleSeconds() {
      return maxIdleSeconds;
    }

    @Override
    public int getMaxReconnectDelaySeconds() {
      return maxReconnectDelaySeconds;
    }

    @Override
    public ChannelActions getChannelActions() {
      return channelActions;
    }

    @Override
    public Executor getExecutor() {
      return executor;
    }

    @Override
    public Scheduler getScheduler() {
      return scheduler;
    }

    @Override
    public Object getContext() {
      return context;
    }

  }

}
