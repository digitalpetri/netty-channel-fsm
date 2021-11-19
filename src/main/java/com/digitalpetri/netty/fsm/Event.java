/*
 * Copyright 2018 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.netty.fsm;

import java.util.concurrent.CompletableFuture;

import io.netty.channel.Channel;

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
