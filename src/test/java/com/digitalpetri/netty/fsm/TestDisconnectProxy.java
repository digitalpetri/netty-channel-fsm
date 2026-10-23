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

class TestDisconnectProxy implements DisconnectProxy {

  private CompletableFuture<Void> future = new CompletableFuture<>();

  @Override
  public synchronized CompletableFuture<Void> disconnect(
      FsmContext<State, Event> ctx,
      Channel channel
  ) {

    return future;
  }

  synchronized void success() {
    future.complete(null);
  }

  synchronized void failure() {
    future.completeExceptionally(new Exception("failed"));
  }

  synchronized void reset() {
    future = new CompletableFuture<>();
  }

}
