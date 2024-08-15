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

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class ChannelInactive implements Event {

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class Connect implements Event {

    final CompletableFuture<Channel> channelFuture = new CompletableFuture<>();

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class ConnectSuccess implements Event {

    final Channel channel;

    public ConnectSuccess(Channel channel) {
      this.channel = channel;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class ConnectFailure implements Event {

    final Throwable failure;

    public ConnectFailure(Throwable failure) {
      this.failure = failure;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class Disconnect implements Event {

    final CompletableFuture<Void> disconnectFuture = new CompletableFuture<>();

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

    final CompletableFuture<Channel> channelFuture = new CompletableFuture<>();

    final boolean waitForReconnect;

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

    final Throwable failure;

    KeepAliveFailure(Throwable failure) {
      this.failure = failure;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  class ReconnectDelayElapsed implements Event {

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

}
