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

interface DisconnectProxy {

  /**
   * Perform any disconnect logic and then close {@code channel}, returning a
   * {@link CompletableFuture} that completes successfully when the Channel has disconnected or
   * completes exceptionally if the channel could not be disconnected for any reason.
   *
   * <p>The state machine advances the same way regardless of how the future is completed.
   *
   * @param ctx the {@link FsmContext}.
   * @param channel the {@link Channel} to disconnect.
   * @return a {@link CompletableFuture} that completes successfully when the Channel has
   *     disconnected or completes exceptionally if the channel could not be disconnected for any
   *     reason.
   * @see ChannelActions#disconnect(FsmContext, Channel)
   */
  CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel);

}
