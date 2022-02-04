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
import java.util.concurrent.TimeUnit;

import com.digitalpetri.netty.fsm.ChannelFsm.ConnectFuture;
import com.digitalpetri.netty.fsm.ChannelFsm.DisconnectFuture;
import com.digitalpetri.netty.fsm.Scheduler.Cancellable;
import com.digitalpetri.strictmachine.FsmContext;
import com.digitalpetri.strictmachine.dsl.ActionContext;
import com.digitalpetri.strictmachine.dsl.FsmBuilder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_CF;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_DF;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_RD;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_RDF;
import static com.digitalpetri.netty.fsm.util.CompletionBuilders.completeAsync;

public class ChannelFsmFactory {

    private final ChannelFsmConfig config;

    public ChannelFsmFactory(ChannelFsmConfig config) {
        this.config = config;
    }

    /**
     * Create a new {@link ChannelFsm} instance.
     *
     * @return a new {@link ChannelFsm} instance.
     */
    public ChannelFsm newChannelFsm() {
        return newChannelFsm(State.NotConnected);
    }

    ChannelFsm newChannelFsm(State initialState) {
        FsmBuilder<State, Event> builder = new FsmBuilder<>(
            config.getExecutor(),
            config.getLoggerName(),
            config.getLoggingContext()
        );

        configureChannelFsm(builder, config);

        return new ChannelFsm(builder, initialState);
    }

    /**
     * Create a new {@link ChannelFsm} instance from {@code config}.
     *
     * @param config a {@link ChannelFsmConfig}.
     * @return a new {@link ChannelFsm} from {@code config}.
     */
    public static ChannelFsm newChannelFsm(ChannelFsmConfig config) {
        return new ChannelFsmFactory(config).newChannelFsm();
    }

    private static void configureChannelFsm(FsmBuilder<State, Event> fb, ChannelFsmConfig config) {
        configureNotConnectedState(fb, config);
        configureIdleState(fb, config);
        configureConnectingState(fb, config);
        configureConnectedState(fb, config);
        configureDisconnectingState(fb, config);
        configureReconnectWaitState(fb, config);
        configureReconnectingState(fb, config);
    }

    private static void configureNotConnectedState(FsmBuilder<State, Event> fb, ChannelFsmConfig config) {
        fb.when(State.NotConnected)
            .on(Event.Connect.class)
            .transitionTo(State.Connecting);

        fb.onInternalTransition(State.NotConnected)
            .via(Event.Disconnect.class)
            .execute(ctx -> {
                Event.Disconnect disconnectEvent = (Event.Disconnect) ctx.event();

                config.getExecutor().execute(() ->
                    disconnectEvent.disconnectFuture.complete(null)
                );
            });

        fb.onInternalTransition(State.NotConnected)
            .via(Event.GetChannel.class)
            .execute(ctx -> {
                Event.GetChannel getChannelEvent = (Event.GetChannel) ctx.event();

                config.getExecutor().execute(() ->
                    getChannelEvent.channelFuture
                        .completeExceptionally(new Exception("not connected"))
                );
            });
    }

    private static void configureIdleState(FsmBuilder<State, Event> fb, ChannelFsmConfig config) {
        fb.when(State.Idle)
            .on(Event.Connect.class)
            .transitionTo(State.Reconnecting);

        fb.when(State.Idle)
            .on(Event.GetChannel.class)
            .transitionTo(State.Reconnecting);

        fb.when(State.Idle)
            .on(Event.Disconnect.class)
            .transitionTo(State.NotConnected);

        fb.onTransitionFrom(State.Idle)
            .to(State.NotConnected)
            .via(Event.Disconnect.class)
            .execute(ctx -> {
                Event.Disconnect disconnect = (Event.Disconnect) ctx.event();
                config.getExecutor().execute(() ->
                    disconnect.disconnectFuture.complete(null)
                );
            });
    }

    private static void configureConnectingState(FsmBuilder<State, Event> fb, ChannelFsmConfig config) {
        if (config.isPersistent()) {
            if (config.isLazy()) {
                fb.when(State.Connecting)
                    .on(Event.ConnectFailure.class)
                    .transitionTo(State.Idle);
            } else {
                fb.when(State.Connecting)
                    .on(Event.ConnectFailure.class)
                    .transitionTo(State.ReconnectWait);
            }
        } else {
            fb.when(State.Connecting)
                .on(Event.ConnectFailure.class)
                .transitionTo(State.NotConnected);
        }

        fb.when(State.Connecting)
            .on(Event.ConnectSuccess.class)
            .transitionTo(State.Connected);

        fb.onTransitionTo(State.Connecting)
            .from(s -> s != State.Connecting)
            .via(e -> e.getClass() == Event.Connect.class)
            .execute(ctx -> {
                ConnectFuture cf = new ConnectFuture();
                KEY_CF.set(ctx, cf);

                handleConnectEvent(ctx, config);

                connect(ctx, config);
            });

        fb.onInternalTransition(State.Connecting)
            .via(Event.Connect.class)
            .execute(ctx -> handleConnectEvent(ctx, config));

        fb.onInternalTransition(State.Connecting)
            .via(Event.GetChannel.class)
            .execute(ctx -> handleGetChannelEvent(ctx, config));

        fb.onInternalTransition(State.Connecting)
            .via(Event.Disconnect.class)
            .execute(ctx -> ctx.shelveEvent(ctx.event()));

        fb.onTransitionFrom(State.Connecting)
            .to(s -> s != State.Connecting)
            .viaAny()
            .execute(FsmContext::processShelvedEvents);

        fb.onTransitionFrom(State.Connecting)
            .to(s -> s != State.Connecting)
            .via(Event.ConnectFailure.class)
            .execute(ctx -> handleConnectFailureEvent(ctx, config));
    }

    private static void configureConnectedState(FsmBuilder<State, Event> fb, ChannelFsmConfig config) {
        Logger logger = LoggerFactory.getLogger(config.getLoggerName());

        fb.when(State.Connected)
            .on(Event.Disconnect.class)
            .transitionTo(State.Disconnecting);

        if (config.isLazy()) {
            fb.when(State.Connected)
                .on(e ->
                    e.getClass() == Event.ChannelInactive.class ||
                        e.getClass() == Event.KeepAliveFailure.class)
                .transitionTo(State.Idle);
        } else {
            fb.when(State.Connected)
                .on(e ->
                    e.getClass() == Event.ChannelInactive.class ||
                        e.getClass() == Event.KeepAliveFailure.class)
                .transitionTo(State.ReconnectWait);
        }

        fb.onTransitionTo(State.Connected)
            .from(s -> s != State.Connected)
            .via(Event.ConnectSuccess.class)
            .execute(ctx -> {
                Event.ConnectSuccess event = (Event.ConnectSuccess) ctx.event();
                Channel channel = event.channel;

                if (config.getMaxIdleSeconds() > 0) {
                    channel.pipeline().addFirst(new IdleStateHandler(config.getMaxIdleSeconds(), 0, 0));
                }

                channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelInactive(ChannelHandlerContext channelContext) throws Exception {
                        config.getLoggingContext().forEach(MDC::put);
                        try {
                            logger.debug(
                                "[{}] channelInactive() local={}, remote={}",
                                ctx.getInstanceId(),
                                channelContext.channel().localAddress(),
                                channelContext.channel().remoteAddress()
                            );
                        } finally {
                            config.getLoggingContext().keySet().forEach(MDC::remove);
                        }

                        if (ctx.currentState() == State.Connected) {
                            ctx.fireEvent(new Event.ChannelInactive());
                        }

                        super.channelInactive(channelContext);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext channelContext, Throwable cause) {
                        config.getLoggingContext().forEach(MDC::put);
                        try {
                            logger.debug(
                                "[{}] exceptionCaught() local={}, remote={}",
                                ctx.getInstanceId(),
                                channelContext.channel().localAddress(),
                                channelContext.channel().remoteAddress(),
                                cause
                            );
                        } finally {
                            config.getLoggingContext().keySet().forEach(MDC::remove);
                        }

                        if (ctx.currentState() == State.Connected) {
                            channelContext.close();
                        }
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext channelContext, Object evt) throws Exception {
                        if (evt instanceof IdleStateEvent) {
                            IdleState idleState = ((IdleStateEvent) evt).state();

                            if (idleState == IdleState.READER_IDLE) {
                                config.getLoggingContext().forEach(MDC::put);
                                try {
                                    logger.debug(
                                        "[{}] channel idle, maxIdleSeconds={}",
                                        ctx.getInstanceId(), config.getMaxIdleSeconds()
                                    );
                                } finally {
                                    config.getLoggingContext().keySet().forEach(MDC::remove);
                                }

                                ctx.fireEvent(new Event.ChannelIdle());
                            }
                        }

                        super.userEventTriggered(channelContext, evt);
                    }
                });


                ConnectFuture cf = KEY_CF.get(ctx);
                config.getExecutor().execute(() -> cf.future.complete(channel));
            });

        fb.onInternalTransition(State.Connected)
            .via(Event.Connect.class)
            .execute(ctx -> handleConnectEvent(ctx, config));

        fb.onInternalTransition(State.Connected)
            .via(Event.GetChannel.class)
            .execute(ctx -> handleGetChannelEvent(ctx, config));

        fb.onInternalTransition(State.Connected)
            .via(Event.ChannelIdle.class)
            .execute(ctx -> {
                ConnectFuture cf = KEY_CF.get(ctx);

                cf.future.thenAcceptAsync(ch -> {
                    CompletableFuture<Void> keepAliveFuture =
                        config.getChannelActions().keepAlive(ctx, ch);

                    keepAliveFuture.whenComplete((v, ex) -> {
                        if (ex != null) {
                            ctx.fireEvent(new Event.KeepAliveFailure(ex));
                        }
                    });
                }, config.getExecutor());
            });

        fb.onTransitionFrom(State.Connected)
            .to(s -> s == State.Idle || s == State.ReconnectWait)
            .via(Event.KeepAliveFailure.class)
            .execute(ctx -> {
                ConnectFuture cf = KEY_CF.get(ctx);

                cf.future.thenAccept(Channel::close);
            });
    }

    private static void configureDisconnectingState(FsmBuilder<State, Event> fb, ChannelFsmConfig config) {
        fb.when(State.Disconnecting)
            .on(Event.DisconnectSuccess.class)
            .transitionTo(State.NotConnected);

        fb.onTransitionTo(State.Disconnecting)
            .from(State.Connected)
            .via(Event.Disconnect.class)
            .execute(ctx -> {
                DisconnectFuture df = new DisconnectFuture();
                KEY_DF.set(ctx, df);

                Event.Disconnect event = (Event.Disconnect) ctx.event();

                completeAsync(event.disconnectFuture, config.getExecutor()).with(df.future);

                disconnect(ctx, config);
            });

        fb.onInternalTransition(State.Disconnecting)
            .via(e -> e.getClass() == Event.Connect.class || e.getClass() == Event.GetChannel.class)
            .execute(ctx -> ctx.shelveEvent(ctx.event()));

        fb.onInternalTransition(State.Disconnecting)
            .via(Event.Disconnect.class)
            .execute(ctx -> {
                DisconnectFuture df = KEY_DF.get(ctx);

                if (df != null) {
                    Event.Disconnect event = (Event.Disconnect) ctx.event();

                    completeAsync(event.disconnectFuture, config.getExecutor()).with(df.future);
                }
            });

        fb.onTransitionFrom(State.Disconnecting)
            .to(s -> s != State.Disconnecting)
            .via(Event.DisconnectSuccess.class)
            .execute(ctx -> {
                DisconnectFuture df = KEY_DF.remove(ctx);

                if (df != null) {
                    config.getExecutor().execute(() -> df.future.complete(null));
                }
            });

        fb.onTransitionFrom(State.Disconnecting)
            .to(s -> s != State.Disconnecting)
            .viaAny()
            .execute(FsmContext::processShelvedEvents);
    }

    private static void configureReconnectWaitState(FsmBuilder<State, Event> fb, ChannelFsmConfig config) {
        fb.when(State.ReconnectWait)
            .on(Event.ReconnectDelayElapsed.class)
            .transitionTo(State.Reconnecting);

        fb.when(State.ReconnectWait)
            .on(Event.Disconnect.class)
            .transitionTo(State.NotConnected);

        // This needs to be defined before the action after it so the previous
        // ConnectFuture can be notified before a new ConnectFuture is set.
        fb.onTransitionTo(State.ReconnectWait)
            .from(State.Reconnecting)
            .via(Event.ConnectFailure.class)
            .execute(ctx -> handleConnectFailureEvent(ctx, config));

        fb.onTransitionTo(State.ReconnectWait)
            .from(s -> s != State.ReconnectWait)
            .viaAny()
            .execute(ctx -> {
                KEY_CF.set(ctx, new ConnectFuture());

                Long delay = KEY_RD.get(ctx);
                if (delay == null) {
                    delay = 1L;
                } else {
                    delay = Math.min(getMaxReconnectDelay(config), delay << 1);
                }
                KEY_RD.set(ctx, delay);

                Cancellable reconnectDelayFuture = config.getScheduler().schedule(
                    () ->
                        ctx.fireEvent(new Event.ReconnectDelayElapsed()),
                    delay,
                    TimeUnit.SECONDS
                );

                KEY_RDF.set(ctx, reconnectDelayFuture);
            });

        fb.onInternalTransition(State.ReconnectWait)
            .via(Event.Connect.class)
            .execute(ctx -> handleConnectEvent(ctx, config));

        fb.onInternalTransition(State.ReconnectWait)
            .via(Event.GetChannel.class)
            .execute(ctx -> {
                Event.GetChannel event = (Event.GetChannel) ctx.event();

                if (event.waitForReconnect) {
                    handleGetChannelEvent(ctx, config);
                } else {
                    config.getExecutor().execute(() ->
                        event.channelFuture
                            .completeExceptionally(new Exception("not reconnected"))
                    );
                }
            });

        fb.onTransitionFrom(State.ReconnectWait)
            .to(State.NotConnected)
            .via(Event.Disconnect.class)
            .execute(ctx -> {
                ConnectFuture connectFuture = KEY_CF.remove(ctx);
                if (connectFuture != null) {
                    config.getExecutor().execute(() ->
                        connectFuture.future
                            .completeExceptionally(new Exception("client disconnected"))
                    );
                }

                KEY_RD.remove(ctx);

                Cancellable reconnectDelayCancellable = KEY_RDF.remove(ctx);
                if (reconnectDelayCancellable != null) {
                    reconnectDelayCancellable.cancel();
                }

                Event.Disconnect disconnect = (Event.Disconnect) ctx.event();
                config.getExecutor().execute(() ->
                    disconnect.disconnectFuture.complete(null)
                );
            });
    }

    private static void configureReconnectingState(FsmBuilder<State, Event> fb, ChannelFsmConfig config) {
        fb.when(State.Reconnecting)
            .on(Event.ConnectFailure.class)
            .transitionTo(State.ReconnectWait);

        fb.when(State.Reconnecting)
            .on(Event.ConnectSuccess.class)
            .transitionTo(State.Connected);

        fb.onTransitionTo(State.Reconnecting)
            .from(State.ReconnectWait)
            .via(Event.ReconnectDelayElapsed.class)
            .execute(ctx -> connect(ctx, config));

        fb.onTransitionTo(State.Reconnecting)
            .from(State.Idle)
            .via(e -> e.getClass() == Event.Connect.class || e.getClass() == Event.GetChannel.class)
            .execute(ctx -> {
                ConnectFuture cf = new ConnectFuture();
                KEY_CF.set(ctx, cf);

                Event event = ctx.event();

                if (event instanceof Event.Connect) {
                    handleConnectEvent(ctx, config);
                } else if (event instanceof Event.GetChannel) {
                    handleGetChannelEvent(ctx, config);
                }

                connect(ctx, config);
            });

        fb.onInternalTransition(State.Reconnecting)
            .via(Event.Connect.class)
            .execute(ctx -> handleConnectEvent(ctx, config));

        fb.onInternalTransition(State.Reconnecting)
            .via(Event.GetChannel.class)
            .execute(ctx -> handleGetChannelEvent(ctx, config));

        fb.onInternalTransition(State.Reconnecting)
            .via(Event.Disconnect.class)
            .execute(ctx -> ctx.shelveEvent(ctx.event()));

        fb.onTransitionFrom(State.Reconnecting)
            .to(s -> s != State.Reconnecting)
            .viaAny()
            .execute(FsmContext::processShelvedEvents);

        fb.onTransitionFrom(State.Reconnecting)
            .to(State.Connected)
            .via(Event.ConnectSuccess.class)
            .execute(ctx -> {
                KEY_RD.remove(ctx);
                KEY_RDF.remove(ctx);
            });
    }

    private static void connect(
        ActionContext<State, Event> ctx,
        ChannelFsmConfig config
    ) {

        config.getExecutor().execute(() ->
            config.getChannelActions().connect(ctx).whenComplete((channel, ex) -> {
                if (channel != null) {
                    ctx.fireEvent(new Event.ConnectSuccess(channel));
                } else {
                    ctx.fireEvent(new Event.ConnectFailure(ex));
                }
            })
        );
    }

    private static void disconnect(
        ActionContext<State, Event> ctx,
        ChannelFsmConfig config
    ) {

        ConnectFuture connectFuture = KEY_CF.get(ctx);

        if (connectFuture != null && connectFuture.future.isDone()) {
            config.getExecutor().execute(() -> {
                CompletableFuture<Void> disconnectFuture = config.getChannelActions().disconnect(
                    ctx,
                    connectFuture.future.getNow(null)
                );

                disconnectFuture.whenComplete((v, ex) -> ctx.fireEvent(new Event.DisconnectSuccess()));
            });
        } else {
            ctx.fireEvent(new Event.DisconnectSuccess());
        }
    }

    private static void handleConnectEvent(ActionContext<State, Event> ctx, ChannelFsmConfig config) {
        CompletableFuture<Channel> channelFuture = KEY_CF.get(ctx).future;

        Event.Connect connectEvent = (Event.Connect) ctx.event();
        completeAsync(connectEvent.channelFuture, config.getExecutor()).with(channelFuture);
    }

    private static void handleGetChannelEvent(ActionContext<State, Event> ctx, ChannelFsmConfig config) {
        CompletableFuture<Channel> channelFuture = KEY_CF.get(ctx).future;

        Event.GetChannel getChannelEvent = (Event.GetChannel) ctx.event();
        completeAsync(getChannelEvent.channelFuture, config.getExecutor()).with(channelFuture);
    }

    private static void handleConnectFailureEvent(ActionContext<State, Event> ctx, ChannelFsmConfig config) {
        ConnectFuture cf = KEY_CF.remove(ctx);

        if (cf != null) {
            Event.ConnectFailure connectFailureEvent = (Event.ConnectFailure) ctx.event();

            config.getExecutor().execute(() ->
                cf.future.completeExceptionally(connectFailureEvent.failure)
            );
        }
    }

    private static int getMaxReconnectDelay(ChannelFsmConfig config) {
        int maxReconnectDelay = config.getMaxReconnectDelaySeconds();

        if (maxReconnectDelay < 1) {
            maxReconnectDelay = ChannelFsmConfigBuilder.DEFAULT_MAX_RECONNECT_DELAY_SECONDS;
        }

        int highestOneBit = Integer.highestOneBit(maxReconnectDelay);

        if (maxReconnectDelay == highestOneBit) {
            return maxReconnectDelay;
        } else {
            return highestOneBit << 1;
        }
    }

}
