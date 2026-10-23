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

import io.netty.channel.Channel;
import java.util.concurrent.CompletableFuture;

public interface Event {

  class ChannelIdle implements Event {

    /**
     * The ConnectFuture of the connection whose channel went idle, or {@code null} if this event
     * applies to the current connection.
     */
    final ChannelFsm.ConnectFuture connectFuture;

    public ChannelIdle() {
      this(null);
    }

    ChannelIdle(ChannelFsm.ConnectFuture connectFuture) {
      this.connectFuture = connectFuture;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class ChannelInactive implements Event {

    /**
     * The ConnectFuture of the connection whose channel closed, or {@code null} if this event
     * applies to the current connection.
     */
    final ChannelFsm.ConnectFuture connectFuture;

    public ChannelInactive() {
      this(null);
    }

    ChannelInactive(ChannelFsm.ConnectFuture connectFuture) {
      this.connectFuture = connectFuture;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class Connect implements Event {

    public final CompletableFuture<Channel> channelFuture = new CompletableFuture<>();

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class ConnectSuccess implements Event {

    public final Channel channel;

    public ConnectSuccess(Channel channel) {
      this.channel = channel;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class ConnectFailure implements Event {

    public final Throwable failure;

    public ConnectFailure(Throwable failure) {
      this.failure = failure;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class Disconnect implements Event {

    public final CompletableFuture<Void> disconnectFuture = new CompletableFuture<>();

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class DisconnectSuccess implements Event {

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class GetChannel implements Event {

    public final CompletableFuture<Channel> channelFuture = new CompletableFuture<>();

    public final boolean waitForReconnect;

    GetChannel() {
      this(true);
    }

    GetChannel(boolean waitForReconnect) {
      this.waitForReconnect = waitForReconnect;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class KeepAliveFailure implements Event {

    public final Throwable failure;

    /**
     * The ConnectFuture of the connection the keep-alive was sent on, or {@code null} if this
     * event applies to the current connection.
     */
    final ChannelFsm.ConnectFuture connectFuture;

    KeepAliveFailure(Throwable failure) {
      this(failure, null);
    }

    KeepAliveFailure(Throwable failure, ChannelFsm.ConnectFuture connectFuture) {
      this.failure = failure;
      this.connectFuture = connectFuture;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class ReconnectDelayElapsed implements Event {

    /**
     * The ConnectFuture of the wait that scheduled this event, or {@code null} if this event
     * applies to the current wait.
     */
    final ChannelFsm.ConnectFuture connectFuture;

    public ReconnectDelayElapsed() {
      this(null);
    }

    ReconnectDelayElapsed(ChannelFsm.ConnectFuture connectFuture) {
      this.connectFuture = connectFuture;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

}
