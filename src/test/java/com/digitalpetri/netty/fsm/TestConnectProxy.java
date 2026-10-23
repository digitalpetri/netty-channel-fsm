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
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.CompletableFuture;

class TestConnectProxy implements ConnectProxy {

  private CompletableFuture<Channel> future = new CompletableFuture<>();

  @Override
  public synchronized CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
    return future;
  }

  synchronized void success() {
    future.complete(new EmbeddedChannel());
  }

  synchronized void failure() {
    future.completeExceptionally(new Exception("failed"));
  }

  synchronized void reset() {
    future = new CompletableFuture<>();
  }

}
