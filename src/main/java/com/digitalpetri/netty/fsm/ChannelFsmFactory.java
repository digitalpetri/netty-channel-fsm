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

import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_CF;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_DF;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_RD;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_RDF;
import static com.digitalpetri.netty.fsm.CompletionBuilders.completeAsync;

import com.digitalpetri.fsm.FsmContext;
import com.digitalpetri.fsm.dsl.ActionContext;
import com.digitalpetri.fsm.dsl.FsmBuilder;
import com.digitalpetri.fsm.dsl.Transition;
import com.digitalpetri.netty.fsm.ChannelFsm.ConnectFuture;
import com.digitalpetri.netty.fsm.ChannelFsm.DisconnectFuture;
import com.digitalpetri.netty.fsm.Scheduler.Cancellable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
    var builder = new FsmBuilder<State, Event>(
        config.getLoggerName(),
        config.getLoggingContext(),
        config.getExecutor(),
        config.getUserContext()
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

  private static void configureNotConnectedState(
      FsmBuilder<State, Event> fb,
      ChannelFsmConfig config
  ) {

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

  private static void configureConnectingState(
      FsmBuilder<State, Event> fb,
      ChannelFsmConfig config
  ) {

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

  private static void configureConnectedState(
      FsmBuilder<State, Event> fb,
      ChannelFsmConfig config
  ) {

    Logger logger = LoggerFactory.getLogger(config.getLoggerName());

    fb.when(State.Connected)
        .on(Event.Disconnect.class)
        .transitionTo(State.Disconnecting);

    // A channel's close or keep-alive failure can arrive after the FSM has left that connection
    // and connected another one, e.g. when SslHandler holds the close until close_notify is
    // flushed, or when a keep-alive request times out. Such an event only ends the connection it
    // came from, which is checked when the event is evaluated.
    State connectionLostTarget = config.isLazy() ? State.Idle : State.ReconnectWait;

    fb.addTransition(new Transition<>() {
      @Override
      public State target() {
        return connectionLostTarget;
      }

      @Override
      public boolean matches(FsmContext<State, Event> ctx, State state, Event event) {
        return state == State.Connected && isConnectionLost(ctx, event);
      }
    });

    fb.onTransitionTo(State.Connected)
        .from(s -> s != State.Connected)
        .via(Event.ConnectSuccess.class)
        .execute(ctx -> {
          Event.ConnectSuccess event = (Event.ConnectSuccess) ctx.event();
          Channel channel = event.channel;
          ConnectFuture cf = KEY_CF.get(ctx);

          if (config.getMaxIdleSeconds() > 0) {
            channel.pipeline()
                .addFirst(new IdleStateHandler(config.getMaxIdleSeconds(), 0, 0));
          }

          // The channel can close before this action runs: the event loop may process the peer's
          // FIN, delivered together with the last handshake message, before the executor gets to
          // ConnectSuccess. A handler added after that never sees channelInactive, but a
          // closeFuture listener is notified even when it's added after the close.
          //
          // The listener runs on the event loop, which must not wait for the FSM's lock, so it
          // doesn't check the FSM's state. The FSM ignores the event unless it's still connected
          // with this channel.
          channel.closeFuture().addListener(future -> {
            config.getLoggingContext().forEach(MDC::put);
            try {
              logger.debug(
                  "channel closed, local={}, remote={}",
                  channel.localAddress(),
                  channel.remoteAddress()
              );
            } finally {
              config.getLoggingContext().keySet().forEach(MDC::remove);
            }

            ctx.fireEvent(new Event.ChannelInactive(cf));
          });

          channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(
                ChannelHandlerContext channelContext,
                Throwable cause
            ) {

              config.getLoggingContext().forEach(MDC::put);
              try {
                logger.debug(
                    "exceptionCaught() local={}, remote={}",
                    channelContext.channel().localAddress(),
                    channelContext.channel().remoteAddress(),
                    cause
                );
              } finally {
                config.getLoggingContext().keySet().forEach(MDC::remove);
              }

              // Close whatever the FSM's state: the FSM is done with this channel or is about to
              // be, and the event loop must not wait for the FSM's lock to check.
              channelContext.close();
            }

            @Override
            public void userEventTriggered(
                ChannelHandlerContext channelContext,
                Object evt
            ) throws Exception {

              if (evt instanceof IdleStateEvent) {
                IdleState idleState = ((IdleStateEvent) evt).state();

                if (idleState == IdleState.READER_IDLE) {
                  config.getLoggingContext().forEach(MDC::put);
                  try {
                    logger.debug("channel idle, maxIdleSeconds={}", config.getMaxIdleSeconds());
                  } finally {
                    config.getLoggingContext().keySet().forEach(MDC::remove);
                  }

                  ctx.fireEvent(new Event.ChannelIdle(cf));
                }
              }

              super.userEventTriggered(channelContext, evt);
            }
          });

          if (cf != null) {
            config.getExecutor().execute(() -> cf.future.complete(channel));
          }
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
          // A channel the FSM has left behind can still go idle, e.g. while its close is held.
          Event.ChannelIdle event = (Event.ChannelIdle) ctx.event();
          if (!isCurrent(ctx, event.connectFuture)) {
            return;
          }

          ConnectFuture cf = KEY_CF.get(ctx);

          cf.future.thenAcceptAsync(ch -> {
            CompletableFuture<Void> keepAliveFuture =
                config.getChannelActions().keepAlive(ctx, ch);

            keepAliveFuture.whenComplete((v, ex) -> {
              if (ex != null) {
                ctx.fireEvent(new Event.KeepAliveFailure(ex, cf));
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

  private static void configureDisconnectingState(
      FsmBuilder<State, Event> fb,
      ChannelFsmConfig config
  ) {

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

  private static void configureReconnectWaitState(
      FsmBuilder<State, Event> fb,
      ChannelFsmConfig config
  ) {

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

  private static void configureReconnectingState(
      FsmBuilder<State, Event> fb,
      ChannelFsmConfig config
  ) {

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

    if (connectFuture != null) {
      // The Connected transition publishes the channel on the executor. A shelved Disconnect
      // can run before that task, so wait for the channel rather than skipping its close.
      connectFuture.future
          .thenComposeAsync(
              channel -> config.getChannelActions().disconnect(ctx, channel),
              config.getExecutor()
          )
          .whenComplete((v, ex) -> ctx.fireEvent(new Event.DisconnectSuccess()));
    } else {
      ctx.fireEvent(new Event.DisconnectSuccess());
    }
  }

  /**
   * Check whether {@code event} ends the current connection: a ChannelInactive or
   * KeepAliveFailure from the current connection, or one that doesn't name a connection.
   *
   * @param ctx the {@link FsmContext}.
   * @param event the event being evaluated.
   * @return {@code true} if {@code event} ends the current connection.
   */
  private static boolean isConnectionLost(FsmContext<State, Event> ctx, Event event) {
    ConnectFuture source;

    if (event.getClass() == Event.ChannelInactive.class) {
      source = ((Event.ChannelInactive) event).connectFuture;
    } else if (event.getClass() == Event.KeepAliveFailure.class) {
      source = ((Event.KeepAliveFailure) event).connectFuture;
    } else {
      return false;
    }

    return isCurrent(ctx, source);
  }

  /**
   * Check whether an event that names the ConnectFuture it came from belongs to the current
   * connection. An event that names none, e.g. one fired at the FSM directly, does.
   *
   * @param ctx the {@link FsmContext}.
   * @param source the ConnectFuture the event came from, or {@code null}.
   * @return {@code true} if the event belongs to the current connection.
   */
  private static boolean isCurrent(FsmContext<State, Event> ctx, ConnectFuture source) {
    return source == null || source == KEY_CF.get(ctx);
  }

  private static void handleConnectEvent(
      ActionContext<State, Event> ctx,
      ChannelFsmConfig config
  ) {

    CompletableFuture<Channel> channelFuture = KEY_CF.get(ctx).future;

    Event.Connect connectEvent = (Event.Connect) ctx.event();
    completeAsync(connectEvent.channelFuture, config.getExecutor()).with(channelFuture);
  }

  private static void handleGetChannelEvent(
      ActionContext<State, Event> ctx,
      ChannelFsmConfig config
  ) {

    CompletableFuture<Channel> channelFuture = KEY_CF.get(ctx).future;

    Event.GetChannel getChannelEvent = (Event.GetChannel) ctx.event();
    completeAsync(getChannelEvent.channelFuture, config.getExecutor()).with(channelFuture);
  }

  private static void handleConnectFailureEvent(
      ActionContext<State, Event> ctx,
      ChannelFsmConfig config
  ) {

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
