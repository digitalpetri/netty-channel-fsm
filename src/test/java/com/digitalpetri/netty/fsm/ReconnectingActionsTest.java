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

import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_RD;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_RDF;
import static com.digitalpetri.netty.fsm.FsmTestSupport.assertEventualState;
import static com.digitalpetri.netty.fsm.FsmTestSupport.assertWithTimeout;
import static com.digitalpetri.netty.fsm.FsmTestSupport.factory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconnectingActionsTest {

  @Test
  @DisplayName("Transition from RECONNECT_WAIT triggers connect()")
  void transitionFromReconnectWaitTriggersConnect() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();

    assertEquals(
        State.ReconnectWait,
        fsm.getFsm().fireEventBlocking(new Event.ChannelInactive())
    );

    assertEventualState(fsm, State.Reconnecting);

    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();
  }

  @Test
  @DisplayName("Internal transition via Connect")
  void internalTransitionViaConnect() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.Reconnecting);

    assertWithTimeout(() -> {
      var connect = new Event.Connect();
      fsm.getFsm().fireEvent(connect);
      connectDelegate.success();

      assertNotNull(connect.channelFuture.get());
    });
  }

  @Test
  @DisplayName("Internal transition via GetChannel")
  void internalTransitionViaGetChannel() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.Reconnecting);

    assertWithTimeout(() -> {
      var getChannel = new Event.GetChannel();
      fsm.getFsm().fireEvent(getChannel);
      connectDelegate.success();

      assertNotNull(getChannel.channelFuture.get());
    });
  }

  @Test
  @DisplayName(
      "Disconnect is shelved while RECONNECTING and un-shelved on external transition from "
          + "RECONNECTING")
  void disconnectIsShelvedWhileReconnecting() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.Reconnecting);

    var disconnect = new Event.Disconnect();
    assertEquals(State.Reconnecting, fsm.getFsm().fireEventBlocking(disconnect));

    connectDelegate.success();

    // after going back to CONNECTED the shelved Disconnect gets processed...

    assertWithTimeout(() -> {
      disconnect.disconnectFuture.get();

      assertTrue(
          disconnect.disconnectFuture.isDone()
              && !disconnect.disconnectFuture.isCompletedExceptionally()
      );
    });
  }

  @Test
  @DisplayName(
      "Transition from RECONNECTING to CONNECTED via ConnectSuccess clears reconnect state")
  void transitionToConnectedClearsReconnectState() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.Reconnecting);

    connectDelegate.success();

    assertEventualState(fsm, State.Connected);

    fsm.getFsm().withContext(ctx -> {
      assertNull(KEY_RD.get(ctx));
      assertNull(KEY_RDF.get(ctx));
    });
  }

}
