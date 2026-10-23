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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * Holds a close until the test releases it, the way SslHandler holds a close until its
 * close_notify is flushed, or SO_LINGER delays it.
 */
class HeldClose extends ChannelOutboundHandlerAdapter {

  private ChannelHandlerContext ctx;
  private ChannelPromise promise;

  @Override
  public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
    this.ctx = ctx;
    this.promise = promise;
  }

  boolean isHolding() {
    return promise != null;
  }

  void release() {
    ctx.close(promise);
  }

}
