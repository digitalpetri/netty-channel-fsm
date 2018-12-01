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
import com.digitalpetri.strictmachine.Fsm;
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

import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_CF;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_DF;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_RD;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_RDF;
import static com.digitalpetri.netty.fsm.util.CompletionBuilders.complete;

public class ChannelFsmFactory {

    private final ChannelFsmConfig config;

    public ChannelFsmFactory(ChannelFsmConfig config) {
        this.config = config;
    }

    public ChannelFsm newChannelFsm() {
        return newChannelFsm(State.NotConnected);
    }

    ChannelFsm newChannelFsm(State initialState) {
        FsmBuilder<State, Event> builder = new FsmBuilder<>(
            config.getExecutor(),
            config.getLoggerName()
        );

        configureChannelFsm(builder, config);

        Fsm<State, Event> fsm = builder.build(initialState);

        return new ChannelFsm(fsm);
    }

    private static void configureChannelFsm(FsmBuilder<State, Event> fb, ChannelFsmConfig config) {
        configureNotConnectedState(fb);
        configureIdleState(fb);
        configureConnectingState(fb, config);
        configureConnectedState(fb, config);
        configureDisconnectingState(fb, config);
        configureReconnectWaitState(fb, config);
        configureReconnectingState(fb, config);
    }

    private static void configureNotConnectedState(FsmBuilder<State, Event> fb) {
        fb.when(State.NotConnected)
            .on(Event.Connect.class)
            .transitionTo(State.Connecting);

        fb.onInternalTransition(State.NotConnected)
            .via(Event.Disconnect.class)
            .execute(ctx -> {
                Event.Disconnect disconnectEvent = (Event.Disconnect) ctx.event();
                disconnectEvent.disconnectFuture.complete(null);
            });

        fb.onInternalTransition(State.NotConnected)
            .via(Event.GetChannel.class)
            .execute(ctx -> {
                Event.GetChannel getChannelEvent = (Event.GetChannel) ctx.event();
                getChannelEvent.channelFuture.completeExceptionally(new Exception("not connected"));
            });
    }

    private static void configureIdleState(FsmBuilder<State, Event> fb) {
        fb.when(State.Idle)
            .on(Event.Connect.class)
            .transitionTo(State.Connecting);

        fb.when(State.Idle)
            .on(Event.GetChannel.class)
            .transitionTo(State.Connecting);

        fb.when(State.Idle)
            .on(Event.Disconnect.class)
            .transitionTo(State.NotConnected);

        fb.onTransitionFrom(State.Idle)
            .to(State.NotConnected)
            .via(Event.Disconnect.class)
            .execute(ctx -> {
                Event.Disconnect disconnect = (Event.Disconnect) ctx.event();
                disconnect.disconnectFuture.complete(null);
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
            .via(e -> e.getClass() == Event.Connect.class || e.getClass() == Event.GetChannel.class)
            .execute(ctx -> {
                ConnectFuture cf = new ConnectFuture();
                KEY_CF.set(ctx, cf);

                Event event = ctx.event();

                if (event instanceof Event.Connect) {
                    handleConnectEvent(ctx);
                } else if (event instanceof Event.GetChannel) {
                    handleGetChannelEvent(ctx);
                }

                connect(config.getChannelActions(), ctx);
            });

        fb.onInternalTransition(State.Connecting)
            .via(Event.Connect.class)
            .execute(ChannelFsmFactory::handleConnectEvent);

        fb.onInternalTransition(State.Connecting)
            .via(Event.GetChannel.class)
            .execute(ChannelFsmFactory::handleGetChannelEvent);

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
            .execute(ChannelFsmFactory::handleConnectFailureEvent);
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
                        logger.debug(
                            "[{}] channelInactive() local={}, remote={}",
                            ctx.getInstanceId(),
                            channelContext.channel().localAddress(),
                            channelContext.channel().remoteAddress()
                        );

                        if (ctx.currentState() == State.Connected) {
                            ctx.fireEvent(new Event.ChannelInactive());
                        }

                        super.channelInactive(channelContext);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext channelContext, Throwable cause) {
                        logger.debug(
                            "[{}] exceptionCaught() local={}, remote={}",
                            ctx.getInstanceId(),
                            channelContext.channel().localAddress(),
                            channelContext.channel().remoteAddress(),
                            cause
                        );

                        if (ctx.currentState() == State.Connected) {
                            channelContext.close();
                        }
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext channelContext, Object evt) throws Exception {
                        if (evt instanceof IdleStateEvent) {
                            IdleState idleState = ((IdleStateEvent) evt).state();

                            if (idleState == IdleState.READER_IDLE) {
                                logger.debug(
                                    "[{}] channel idle, maxIdleSeconds={}",
                                    ctx.getInstanceId(), config.getMaxIdleSeconds()
                                );

                                ctx.fireEvent(new Event.ChannelIdle());
                            }
                        }

                        super.userEventTriggered(channelContext, evt);
                    }
                });

                ConnectFuture cf = KEY_CF.get(ctx);
                cf.future.complete(channel);
            });

        fb.onInternalTransition(State.Connected)
            .via(Event.Connect.class)
            .execute(ChannelFsmFactory::handleConnectEvent);

        fb.onInternalTransition(State.Connected)
            .via(Event.GetChannel.class)
            .execute(ChannelFsmFactory::handleGetChannelEvent);

        fb.onInternalTransition(State.Connected)
            .via(Event.ChannelIdle.class)
            .execute(ctx -> {
                ConnectFuture cf = KEY_CF.get(ctx);

                cf.future.thenAccept(ch -> {
                    CompletableFuture<Void> keepAliveFuture =
                        config.getChannelActions().keepAlive(ctx, ch);

                    keepAliveFuture.whenComplete((v, ex) -> {
                        if (ex != null) {
                            ctx.fireEvent(new Event.KeepAliveFailure(ex));
                        }
                    });
                });
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

                complete(event.disconnectFuture).with(df.future);

                disconnect(config.getChannelActions(), ctx);
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

                    complete(event.disconnectFuture).with(df.future);
                }
            });

        fb.onTransitionFrom(State.Disconnecting)
            .to(s -> s != State.Disconnecting)
            .via(Event.DisconnectSuccess.class)
            .execute(ctx -> {
                DisconnectFuture df = KEY_DF.remove(ctx);

                if (df != null) {
                    df.future.complete(null);
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
            .execute(ChannelFsmFactory::handleConnectFailureEvent);

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
            .execute(ChannelFsmFactory::handleConnectEvent);

        fb.onInternalTransition(State.ReconnectWait)
            .via(Event.GetChannel.class)
            .execute(ChannelFsmFactory::handleGetChannelEvent);

        fb.onTransitionFrom(State.ReconnectWait)
            .to(State.NotConnected)
            .via(Event.Disconnect.class)
            .execute(ctx -> {
                ConnectFuture connectFuture = KEY_CF.remove(ctx);
                if (connectFuture != null) {
                    connectFuture.future.completeExceptionally(
                        new Exception("client disconnected")
                    );
                }

                KEY_RD.remove(ctx);

                Cancellable reconnectDelayCancellable = KEY_RDF.remove(ctx);
                if (reconnectDelayCancellable != null) {
                    reconnectDelayCancellable.cancel();
                }

                Event.Disconnect disconnect = (Event.Disconnect) ctx.event();
                disconnect.disconnectFuture.complete(null);
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
            .execute(ctx -> connect(config.getChannelActions(), ctx));

        fb.onInternalTransition(State.Reconnecting)
            .via(Event.Connect.class)
            .execute(ChannelFsmFactory::handleConnectEvent);

        fb.onInternalTransition(State.Reconnecting)
            .via(Event.GetChannel.class)
            .execute(ChannelFsmFactory::handleGetChannelEvent);

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
        ChannelActions channelActions,
        ActionContext<State, Event> ctx) {

        channelActions.connect(ctx).whenComplete((channel, ex) -> {
            if (channel != null) {
                ctx.fireEvent(new Event.ConnectSuccess(channel));
            } else {
                ctx.fireEvent(new Event.ConnectFailure(ex));
            }
        });
    }

    private static void disconnect(
        ChannelActions channelActions,
        ActionContext<State, Event> ctx) {

        ConnectFuture connectFuture = KEY_CF.get(ctx);

        if (connectFuture != null && connectFuture.future.isDone()) {
            CompletableFuture<Void> disconnectFuture = channelActions.disconnect(
                ctx,
                connectFuture.future.getNow(null)
            );

            disconnectFuture.whenComplete((v, ex) -> ctx.fireEvent(new Event.DisconnectSuccess()));
        } else {
            ctx.fireEvent(new Event.DisconnectSuccess());
        }
    }

    private static void handleConnectEvent(ActionContext<State, Event> ctx) {
        CompletableFuture<Channel> channelFuture = KEY_CF.get(ctx).future;

        Event.Connect connectEvent = (Event.Connect) ctx.event();
        complete(connectEvent.channelFuture).with(channelFuture);
    }

    private static void handleGetChannelEvent(ActionContext<State, Event> ctx) {
        CompletableFuture<Channel> channelFuture = KEY_CF.get(ctx).future;

        Event.GetChannel getChannelEvent = (Event.GetChannel) ctx.event();
        complete(getChannelEvent.channelFuture).with(channelFuture);
    }

    private static void handleConnectFailureEvent(ActionContext<State, Event> ctx) {
        ConnectFuture cf = KEY_CF.remove(ctx);

        if (cf != null) {
            Event.ConnectFailure connectFailureEvent = (Event.ConnectFailure) ctx.event();
            cf.future.completeExceptionally(connectFailureEvent.failure);
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
