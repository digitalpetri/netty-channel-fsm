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

import java.util.Map;
import java.util.concurrent.Executor;

public interface ChannelFsmConfig {

  /**
   * {@code true} if the ChannelFsm should be lazy, i.e. after an unintentional channel disconnect
   * it waits in an Idle state until the Channel is requested via {@link ChannelFsm#connect()} or
   * {@link ChannelFsm#getChannel()}.
   *
   * @return {@code true} if the ChannelFsm should be lazy,
   */
  boolean isLazy();

  /**
   * {@code true} if the ChannelFsm should be persistent in its initial connect attempt, i.e. if the
   * initial attempt to connect initiated by {@link ChannelFsm#connect()}} fails, it will
   * immediately move into a reconnecting state and continue to try and establish a connection.
   *
   * <p>Each time a connection attempt fails, including the first, the outstanding
   * {@link java.util.concurrent.CompletableFuture}s will be completed exceptionally.
   *
   * @return {@code true} if the ChannelFsm should be persistent in its initial connect attempt.
   */
  boolean isPersistent();

  /**
   * Get the maximum amount of time, in seconds, before a keep alive occurs on an idle channel.
   *
   * <p>An idle channel is one that that hasn't read any bytes within the time defined by this
   * value.
   *
   * <p>Return 0 to disable keep alives.
   *
   * @return the maximum amount of time, in seconds, before a keep alive occurs on an idle channel.
   */
  int getMaxIdleSeconds();

  /**
   * Get the maximum delay to occur between reconnect attempts. Will be rounded up to the nearest
   * power of 2.
   *
   * <p>The delay is increased exponentially starting at 1 second until the maximum delay, e.g.
   * (1, 2, 4, 8, 16, 32, 32, 32, 32...).
   *
   * @return the maximum delay to occur between reconnect attempts.
   */
  int getMaxReconnectDelaySeconds();

  /**
   * Get the {@link ChannelActions} delegate.
   *
   * @return the {@link ChannelActions} delegate.
   */
  ChannelActions getChannelActions();

  /**
   * Get the {@link Executor} to use.
   *
   * @return the {@link Executor} to use.
   */
  Executor getExecutor();

  /**
   * Get the {@link Scheduler} to use.
   *
   * @return the {@link Scheduler} to use.
   */
  Scheduler getScheduler();

  /**
   * Get the logger name the FSM should use.
   *
   * @return the logger name the FSM should use.
   */
  String getLoggerName();

  /**
   * Get the logging context Map a {@link ChannelFsm} instance will use.
   *
   * <p>Keys and values in the Map will be set on the SLF4J {@link org.slf4j.MDC} when logging.
   *
   * @return the logging context Map a {@link ChannelFsm} instance will use.
   */
  Map<String, String> getLoggingContext();

  /**
   * Get the user-configurable context associated with this ChannelFsm.
   *
   * @return the user-configurable context associated with this ChannelFsm.
   */
  Object getUserContext();

  /**
   * Create a new {@link ChannelFsmConfigBuilder}.
   *
   * @return a new {@link ChannelFsmConfigBuilder}.
   */
  static ChannelFsmConfigBuilder newBuilder() {
    return new ChannelFsmConfigBuilder();
  }

}
