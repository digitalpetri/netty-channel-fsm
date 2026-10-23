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

import static com.digitalpetri.netty.fsm.FsmTestSupport.assertEventualState;
import static com.digitalpetri.netty.fsm.FsmTestSupport.assertWithTimeout;
import static com.digitalpetri.netty.fsm.FsmTestSupport.factory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.Channel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectedActionsTest {

  @Test
  @DisplayName("External transition to CONNECTED via ConnectSuccess")
  void externalTransitionViaConnectSuccess() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    var event = new Event.Connect();

    assertEquals(State.Connecting, fsm.getFsm().fireEventBlocking(event));

    connectDelegate.success();

    assertWithTimeout(() -> assertNotNull(event.channelFuture.get()));
  }

  @Test
  @DisplayName("Internal transition to CONNECTED via Connect")
  void internalTransitionViaConnect() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.NotConnected);

    var event = new Event.Connect();
    fsm.getFsm().fireEventBlocking(event);

    assertWithTimeout(() -> assertNotNull(event.channelFuture.get()));

    assertEquals(State.Connected, fsm.getFsm().getState());

    var connect = new Event.Connect();
    fsm.getFsm().fireEventBlocking(connect);
    assertWithTimeout(() -> assertNotNull(connect.channelFuture.get()));
  }

  @Test
  @DisplayName("Internal transition to CONNECTED via GetChannel")
  void internalTransitionViaGetChannel() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.NotConnected);

    var event = new Event.Connect();
    fsm.getFsm().fireEventBlocking(event);

    assertWithTimeout(() -> assertNotNull(event.channelFuture.get()));

    assertEquals(State.Connected, fsm.getFsm().getState());

    var getChannel = new Event.GetChannel();
    fsm.getFsm().fireEventBlocking(getChannel);
    assertWithTimeout(() -> assertNotNull(getChannel.channelFuture.get()));
  }

  @Test
  @DisplayName("Internal transition to CONNECTED via ChannelIdle triggers KeepAliveProxy")
  void internalTransitionViaChannelIdleTriggersKeepAlive() throws Exception {
    var keepAliveLatch = new CountDownLatch(1);

    KeepAliveProxy keepAliveProxy = (ctx, channel) -> {
      keepAliveLatch.countDown();

      return CompletableFuture.completedFuture(null);
    };

    ChannelFsm fsm = factory().keepAliveProxy(keepAliveProxy).build()
        .newChannelFsm(State.NotConnected);

    var event = new Event.Connect();
    fsm.getFsm().fireEventBlocking(event);

    assertEventualState(fsm, State.Connected);

    var channelIdle = new Event.ChannelIdle();
    fsm.getFsm().fireEventBlocking(channelIdle);

    assertWithTimeout(keepAliveLatch::await);
  }

  @Test
  @DisplayName("Transition from CONNECTED to IDLE via KeepAliveFailure closes channel")
  void transitionToIdleViaKeepAliveFailureClosesChannel() throws Exception {
    ChannelFsmFactory factory = factory().lazy(true).build();
    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    assertEventualState(fsm, State.Connected);

    Channel channel = fsm.getChannel().get();

    assertTrue(channel.isOpen());

    fsm.getFsm().fireEventBlocking(new Event.KeepAliveFailure(new Throwable("failure")));
    assertEventualState(fsm, State.Idle);

    assertFalse(channel.isOpen());
  }

  @Test
  @DisplayName("Transition from CONNECTED to RECONNECT_WAIT via KeepAliveFailure closes channel")
  void transitionToReconnectWaitViaKeepAliveFailureClosesChannel() throws Exception {
    ChannelFsmFactory factory = factory().lazy(false).build();
    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    assertEventualState(fsm, State.Connected);

    Channel channel = fsm.getChannel().get();

    assertTrue(channel.isOpen());

    assertEquals(
        State.ReconnectWait,
        fsm.getFsm().fireEventBlocking(new Event.KeepAliveFailure(new Throwable("failure")))
    );

    assertFalse(channel.isOpen());
  }

}
