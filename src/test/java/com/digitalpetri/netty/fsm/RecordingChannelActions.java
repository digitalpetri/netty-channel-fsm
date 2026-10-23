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

import com.digitalpetri.fsm.FsmContext;
import io.netty.channel.Channel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link ChannelActions} for tests that drive the FSM step by step. Every call is recorded.
 *
 * <p>connect() and keepAlive() return pending futures that the test completes. disconnect()
 * closes the channel and completes immediately. Override a method to change its behavior.
 */
class RecordingChannelActions implements ChannelActions {

  /** The futures returned by connect(), in call order. */
  final List<CompletableFuture<Channel>> connects = new CopyOnWriteArrayList<>();

  /** The channels passed to disconnect(), in call order. */
  final List<Channel> disconnects = new CopyOnWriteArrayList<>();

  /** The futures returned by keepAlive(), in call order. */
  final List<CompletableFuture<Void>> keepAlives = new CopyOnWriteArrayList<>();

  @Override
  public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
    var future = new CompletableFuture<Channel>();
    connects.add(future);
    return future;
  }

  @Override
  public CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel) {
    disconnects.add(channel);
    channel.close();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> keepAlive(FsmContext<State, Event> ctx, Channel channel) {
    var future = new CompletableFuture<Void>();
    keepAlives.add(future);
    return future;
  }

  CompletableFuture<Channel> lastConnect() {
    return connects.get(connects.size() - 1);
  }

}
