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

import static com.digitalpetri.netty.fsm.FsmTestSupport.stepConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A channel's close and its keep-alive result can arrive after the FSM has left that channel
 * behind and connected another one. Those events must not affect the new connection.
 */
class StaleChannelEventTest {

  private final QueuedExecutor executor = new QueuedExecutor();
  private final ManualScheduler scheduler = new ManualScheduler();
  private final RecordingChannelActions actions = new RecordingChannelActions();
  private final List<EmbeddedChannel> channels = new ArrayList<>();

  @AfterEach
  void releaseChannels() {
    channels.forEach(EmbeddedChannel::finishAndReleaseAll);
  }

  // KeepAliveFailure closes the channel without waiting for the close to finish. SslHandler holds
  // a close until close_notify is flushed, and SO_LINGER delays it, so a lazy FSM can reconnect
  // before the old channel's close completes.
  @Test
  @DisplayName("A late close after KeepAliveFailure does not tear down the next connection")
  void lateCloseAfterKeepAliveFailure() {
    ChannelFsm fsm = newFsm(true);
    EmbeddedChannel a = newChannel();
    var heldClose = new HeldClose();
    a.pipeline().addFirst(heldClose);
    final EmbeddedChannel b = newChannel();

    connect(fsm, a);

    a.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
    executor.drain();
    actions.keepAlives.get(0).completeExceptionally(new TimeoutException("keep-alive"));
    executor.drain();
    assertEquals(State.Idle, fsm.getState());
    assertTrue(heldClose.isHolding(), "the FSM closed A, and the close is in progress");

    fsm.getChannel();
    executor.drain();
    actions.lastConnect().complete(b);
    executor.drain();
    assertEquals(State.Connected, fsm.getState());

    heldClose.release();
    executor.drain();

    assertFalse(a.isOpen());
    assertEquals(State.Connected, fsm.getState(), "A's close must not tear down B");
    assertTrue(b.isOpen());
  }

  // ChannelActions.disconnect may start the close and complete without waiting for it.
  @Test
  @DisplayName("A late close after disconnect does not tear down the next connection")
  void lateCloseAfterDisconnect() {
    ChannelFsm fsm = newFsm(false);
    EmbeddedChannel a = newChannel();
    var heldClose = new HeldClose();
    a.pipeline().addFirst(heldClose);
    final EmbeddedChannel b = newChannel();

    connect(fsm, a);

    fsm.disconnect();
    executor.drain();
    assertEquals(State.NotConnected, fsm.getState());
    assertTrue(heldClose.isHolding(), "disconnect closed A, and the close is in progress");

    connect(fsm, b);

    heldClose.release();
    executor.drain();

    assertFalse(a.isOpen());
    assertEquals(State.Connected, fsm.getState(), "A's close must not tear down B");
    assertTrue(b.isOpen());
  }

  // A keep-alive request has its own timeout. It can fail after the channel it was sent on
  // closed and the FSM reconnected.
  @Test
  @DisplayName("A late KeepAliveFailure does not close the next connection")
  void lateKeepAliveFailure() {
    ChannelFsm fsm = newFsm(false);
    EmbeddedChannel a = newChannel();
    final EmbeddedChannel b = newChannel();

    connect(fsm, a);

    a.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
    executor.drain();
    assertEquals(1, actions.keepAlives.size());

    a.close();
    executor.drain();
    assertEquals(State.ReconnectWait, fsm.getState());

    scheduler.runPending();
    executor.drain();
    actions.lastConnect().complete(b);
    executor.drain();
    assertEquals(State.Connected, fsm.getState());

    actions.keepAlives.get(0).completeExceptionally(new TimeoutException("keep-alive"));
    executor.drain();

    assertEquals(State.Connected, fsm.getState(), "A's keep-alive failure must not affect B");
    assertTrue(b.isOpen());
  }

  // A's IdleStateHandler keeps firing while its close is held.
  @Test
  @DisplayName("A late ChannelIdle does not send a keep-alive on the next connection")
  void lateChannelIdle() {
    ChannelFsm fsm = newFsm(true);
    EmbeddedChannel a = newChannel();
    var heldClose = new HeldClose();
    a.pipeline().addFirst(heldClose);
    final EmbeddedChannel b = newChannel();

    connect(fsm, a);

    a.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
    executor.drain();
    actions.keepAlives.get(0).completeExceptionally(new TimeoutException("keep-alive"));
    executor.drain();
    assertEquals(State.Idle, fsm.getState());

    fsm.getChannel();
    executor.drain();
    actions.lastConnect().complete(b);
    executor.drain();
    assertEquals(State.Connected, fsm.getState());

    a.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
    executor.drain();

    assertEquals(1, actions.keepAlives.size(), "A's idle event must not send a keep-alive on B");
    heldClose.release();
  }

  private ChannelFsm newFsm(boolean lazy) {
    ChannelFsmConfig config = stepConfig(executor, scheduler, actions)
        .setLazy(lazy)
        .setMaxIdleSeconds(15)
        .build();

    return new ChannelFsmFactory(config).newChannelFsm();
  }

  private EmbeddedChannel newChannel() {
    var channel = new EmbeddedChannel();
    channels.add(channel);
    return channel;
  }

  private void connect(ChannelFsm fsm, EmbeddedChannel channel) {
    fsm.connect();
    executor.drain();
    actions.lastConnect().complete(channel);
    executor.drain();
    assertEquals(State.Connected, fsm.getState());
  }

}
