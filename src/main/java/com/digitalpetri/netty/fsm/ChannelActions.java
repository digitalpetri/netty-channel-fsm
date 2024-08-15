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

import com.digitalpetri.fsm.FsmContext;
import io.netty.channel.Channel;
import java.util.concurrent.CompletableFuture;

public interface ChannelActions {

  /**
   * Bootstrap a new {@link Channel} and return a {@link CompletableFuture} that completes
   * successfully when the Channel is ready to use or completes exceptionally if the Channel could
   * not be created or made ready to use for any reason.
   *
   * @param ctx the {@link FsmContext}.
   * @return a {@link CompletableFuture} that completes successfully when the Channel is ready to
   *     use or completes exceptionally if the Channel could not be created or made ready to use for
   *     any reason.
   */
  CompletableFuture<Channel> connect(FsmContext<State, Event> ctx);

  /**
   * Perform any disconnect actions and then close {@code channel}, returning a
   * {@link CompletableFuture} that completes successfully when the Channel has disconnected or
   * completes exceptionally if the channel could not be disconnected for any reason.
   *
   * <p>The state machine advances the same way regardless of how the future is completed.
   *
   * @param ctx the {@link FsmContext}.
   * @param channel the {@link Channel} to disconnect.
   * @return a {@link CompletableFuture} that completes successfully when the Channel * has
   *     disconnected or completes exceptionally if the channel could not be disconnected for any
   *     reason.
   */
  CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel);

  /**
   * Perform a keep-alive action because the Channel has been idle for longer than
   * {@code maxIdleSeconds}.
   *
   * <p>Although the keep-alive action is implementation dependent the intended usage would be to
   * do something send a request that tests the Channel to make sure it's still valid.
   *
   * @param ctx the {@link FsmContext}
   * @param channel the {@link Channel} to send the keep-alive on.
   * @return a {@link CompletableFuture} that completes successfully if the channel is still valid
   *     and completes exceptionally otherwise.
   */
  default CompletableFuture<Void> keepAlive(FsmContext<State, Event> ctx, Channel channel) {
    return CompletableFuture.completedFuture(null);
  }

}
