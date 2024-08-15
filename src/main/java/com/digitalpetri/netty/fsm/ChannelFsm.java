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

import com.digitalpetri.fsm.Fsm;
import com.digitalpetri.fsm.FsmContext;
import com.digitalpetri.fsm.dsl.ActionContext;
import com.digitalpetri.fsm.dsl.FsmBuilder;
import com.digitalpetri.fsm.dsl.TransitionAction;
import com.digitalpetri.netty.fsm.Event.Connect;
import com.digitalpetri.netty.fsm.Event.Disconnect;
import com.digitalpetri.netty.fsm.Event.GetChannel;
import com.digitalpetri.netty.fsm.Scheduler.Cancellable;
import io.netty.channel.Channel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChannelFsm {

  private final List<TransitionListener> transitionListeners = new CopyOnWriteArrayList<>();

  private final Fsm<State, Event> fsm;

  ChannelFsm(FsmBuilder<State, Event> builder, State initialState) {
    builder.addTransitionAction(new TransitionAction<State, Event>() {
      @Override
      public void execute(ActionContext<State, Event> context) {
        transitionListeners.forEach(
            listener ->
                listener.onStateTransition(context.from(), context.to(), context.event())
        );
      }

      @Override
      public boolean matches(State from, State to, Event event) {
        return true;
      }
    });

    this.fsm = builder.build(initialState);
  }

  public Fsm<State, Event> getFsm() {
    return fsm;
  }

  /**
   * Fire a {@link Connect} event and return a {@link CompletableFuture} that completes successfully
   * with the {@link Channel} if a successful connection is made, or already exists, and completes
   * exceptionally otherwise.
   *
   * @return a {@link CompletableFuture} that completes successfully with the {@link Channel} if a
   *     successful connection was made, or already exists, and completes exceptionally otherwise.
   */
  public CompletableFuture<Channel> connect() {
    Connect connect = new Connect();

    fsm.fireEvent(connect);

    return connect.channelFuture;
  }

  /**
   * Fire a {@link Disconnect} event and return a {@link CompletableFuture} that completes
   * successfully when the {@link Channel} has been closed.
   *
   * @return a {@link CompletableFuture} that completes successfully when the {@link Channel} has
   *     been closed.
   */
  public CompletableFuture<Void> disconnect() {
    Disconnect disconnect = new Disconnect();

    fsm.fireEvent(disconnect);

    return disconnect.disconnectFuture;
  }

  /**
   * Fire a {@link GetChannel} event and return a {@link CompletableFuture} that completes
   * successfully when the {@link Channel} is available and completes exceptionally if the FSM is
   * not currently connected or the connection attempt failed.
   *
   * <p>{@link #connect()} must have been called at least once before attempting to get a Channel.
   * Whether further calls are necessary depends on whether the FSM is configured to be persistent
   * in its connection attempts or not.
   *
   * <p>The returned CompletableFuture always fails exceptionally if the FSM is not connected.
   *
   * <p>This method is equivalent to {@code getChannel(true)} - if the state machine is
   * reconnecting it will wait for the result.
   *
   * @return a {@link CompletableFuture} that completes successfully when the {@link Channel} is
   *     available and completes exceptionally if the FSM is not currently connected or the
   *     connection attempt failed.
   */
  public CompletableFuture<Channel> getChannel() {
    return getChannel(true);
  }

  /**
   * Fire a {@link GetChannel} event and return a {@link CompletableFuture} that completes
   * successfully when the {@link Channel} is available and completes exceptionally if the FSM is
   * not currently connected or the connection attempt failed.
   *
   * <p>{@link #connect()} must have been called at least once before attempting to get a Channel.
   * Whether further calls are necessary depends on whether the FSM is configured to be persistent
   * in its connection attempts or not.
   *
   * <p>The returned CompletableFuture always fails exceptionally if the FSM is not connected.
   *
   * @param waitForReconnect when {@code true} and the state machine is in
   *     {@link State#ReconnectWait} the future will not be completed until the result of the
   *     subsequent reconnect attempt has been obtained. When {@code false} and the state machine is
   *     in {@link State#ReconnectWait} the future is failed immediately. This parameter has no
   *     effect in other states.
   * @return a {@link CompletableFuture} that completes successfully when the {@link Channel} is
   *     available and *completes exceptionally if the FSM is not currently connected or the
   *     connection attempt failed.
   */
  public CompletableFuture<Channel> getChannel(boolean waitForReconnect) {
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
      GetChannel getChannel = new GetChannel(waitForReconnect);

      fsm.fireEvent(getChannel);

      return getChannel.channelFuture;
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

  /**
   * Add a {@link TransitionListener}.
   *
   * @param transitionListener the {@link TransitionListener}.
   */
  public void addTransitionListener(TransitionListener transitionListener) {
    transitionListeners.add(transitionListener);
  }

  /**
   * Remove a previously registered {@link TransitionListener}.
   *
   * @param transitionListener the {@link TransitionListener}.
   */
  public void removeTransitionListener(TransitionListener transitionListener) {
    transitionListeners.remove(transitionListener);
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

  public interface TransitionListener {

    /**
     * A state transition has occurred.
     *
     * <p>Transitions may be internal, i.e. the {@code from} and {@code to} state are the same.
     *
     * <p>Listener notification is implemented as a {@link TransitionAction}, so take care not to
     * block in this callback as it will block the state machine evaluation as well.
     *
     * @param from the {@link State} transitioned from.
     * @param to the {@link State} transitioned to.
     * @param via the {@link Event} that caused the transition.
     */
    void onStateTransition(State from, State to, Event via);

  }

}
