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

interface ConnectProxy {

  /**
   * Bootstrap a new {@link Channel} and return a {@link CompletableFuture} that completes
   * successfully when the Channel is ready to use or completes exceptionally if the Channel could
   * not be created or made ready to use for any reason.
   *
   * @param ctx the {@link FsmContext}.
   * @return a {@link CompletableFuture} that completes successfully when the Channel is ready to
   *     use or completes exceptionally if the Channel could not be created or made ready to use for
   *     any reason.
   * @see ChannelActions#connect(FsmContext)
   */
  CompletableFuture<Channel> connect(FsmContext<State, Event> ctx);

}
