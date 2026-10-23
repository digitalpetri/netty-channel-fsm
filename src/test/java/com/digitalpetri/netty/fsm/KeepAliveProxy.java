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

interface KeepAliveProxy {

  /**
   * Perform a keep-alive action because the Channel has been idle for longer than
   * {@code maxIdleSeconds}.
   *
   * <p>Although the keep-alive action is implementation dependent the intended usage would be to do
   * something send a request that tests the Channel to make sure it's still valid.
   *
   * @param ctx the {@link FsmContext}
   * @param channel the {@link Channel} to send the keep-alive on.
   * @return a {@link CompletableFuture} that completes successfully if the channel is still valid
   *     and completes exceptionally otherwise.
   * @see ChannelActions#keepAlive(FsmContext, Channel)
   */
  CompletableFuture<Void> keepAlive(FsmContext<State, Event> ctx, Channel channel);

}
