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

import com.digitalpetri.netty.fsm.Event.Connect;
import com.digitalpetri.netty.fsm.Event.Disconnect;
import com.digitalpetri.netty.fsm.Event.GetChannel;
import com.digitalpetri.netty.fsm.Scheduler.Cancellable;
import com.digitalpetri.strictmachine.Fsm;
import com.digitalpetri.strictmachine.FsmContext;
import io.netty.channel.Channel;

import static com.digitalpetri.netty.fsm.util.CompletionBuilders.complete;

public class ChannelFsm {

    private final Fsm<State, Event> fsm;

    ChannelFsm(Fsm<State, Event> fsm) {
        this.fsm = fsm;
    }

    Fsm<State, Event> getFsm() {
        return fsm;
    }

    /**
     * Fire a {@link Connect} event and return a {@link CompletableFuture} that completes successfully with the
     * {@link Channel} if a successful connection is made, or already exists, and completes exceptionally otherwise.
     *
     * @return a {@link CompletableFuture} that completes successfully with the {@link Channel} if a successful
     * connection was made, or already exists, and completes exceptionally otherwise.
     */
    public CompletableFuture<Channel> connect() {
        Connect connect = new Connect();

        fsm.fireEvent(connect);

        CompletableFuture<Channel> future = new CompletableFuture<>();

        return complete(future)
            .with(connect.channelFuture);
    }

    /**
     * Fire a {@link Disconnect} event and return a {@link CompletableFuture} that completes successfully when the
     * {@link Channel} has been closed.
     *
     * @return a {@link CompletableFuture} that completes successfully when the {@link Channel} has been closed.
     */
    public CompletableFuture<Void> disconnect() {
        Disconnect disconnect = new Disconnect();

        fsm.fireEvent(disconnect);

        return complete(new CompletableFuture<Void>())
            .with(disconnect.disconnectFuture);
    }

    /**
     * Fire a {@link GetChannel} event and return a {@link CompletableFuture} that completes successfully when the
     * {@link Channel} is available and completes exceptionally if the FSM is not currently connected or the connection
     * attempt failed.
     * <p>
     * {@link #connect()} must have been called at least once before attempting to get a Channel. Whether further calls
     * are necessary depends on whether the FSM is configured to be persistent in its connection attempts or not.
     * <p>
     * The returned CompletableFuture always fails exceptionally if the FSM is not connected.
     *
     * @return a {@link CompletableFuture} that completes successfully when the {@link Channel} is available and
     * completes exceptionally if the FSM is not currently connected or the connection attempt failed.
     */
    public CompletableFuture<Channel> getChannel() {
        CompletableFuture<Channel> future = fsm.getFromContext(ctx -> {
            State state = ctx.currentState();

            if (state == State.Connected) {
                ConnectFuture cf = KEY_CF.get(ctx);

                assert cf != null;

                return cf.future;
            } else {
                return null;
            }
        });

        if (future != null) {
            return future;
        } else {
            // "Slow" path... not connected yet.
            GetChannel getChannel = new GetChannel();

            fsm.fireEvent(getChannel);

            return complete(new CompletableFuture<Channel>())
                .with(getChannel.channelFuture);
        }
    }

    /**
     * Get the current {@link State} of the {@link ChannelFsm}.
     *
     * @return the current {@link State} of the {@link ChannelFsm}.
     */
    public State getState() {
        return fsm.getFromContext(FsmContext::currentState);
    }

    static final FsmContext.Key<ConnectFuture> KEY_CF =
        new FsmContext.Key<>("connectFuture", ConnectFuture.class);

    static final FsmContext.Key<DisconnectFuture> KEY_DF =
        new FsmContext.Key<>("disconnectFuture", DisconnectFuture.class);

    static final FsmContext.Key<Long> KEY_RD =
        new FsmContext.Key<>("reconnectDelay", Long.class);

    static final FsmContext.Key<Cancellable> KEY_RDF =
        new FsmContext.Key<>("reconnectDelayCancellable", Cancellable.class);

    static class ConnectFuture {
        final CompletableFuture<Channel> future = new CompletableFuture<>();
    }

    static class DisconnectFuture {
        final CompletableFuture<Void> future = new CompletableFuture<>();
    }

}
