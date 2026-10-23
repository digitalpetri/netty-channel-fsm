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

import static com.digitalpetri.netty.fsm.FsmTestSupport.factory;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectedTransitionsTest {

  @Test
  @DisplayName("S(CONNECTED) x E(Disconnect) = S'(DISCONNECTING)")
  void disconnectTransitionsToDisconnecting() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.Connected);
    var event = new Event.Disconnect();

    assertEquals(State.Disconnecting, fsm.getFsm().fireEventBlocking(event));
  }

  @Test
  @DisplayName("S(CONNECTED) x E(ChannelInactive) = S'(IDLE) lazy=true")
  void channelInactiveTransitionsToIdleWhenLazy() throws Exception {
    ChannelFsm fsm = factory().lazy(true).build().newChannelFsm(State.Connected);
    var event = new Event.ChannelInactive();

    assertEquals(State.Idle, fsm.getFsm().fireEventBlocking(event));
  }

  @Test
  @DisplayName("S(CONNECTED) x E(ChannelInactive) = S'(RECONNECT_WAIT) lazy=false")
  void channelInactiveTransitionsToReconnectWaitWhenNotLazy() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.Connected);
    var event = new Event.ChannelInactive();

    assertEquals(State.ReconnectWait, fsm.getFsm().fireEventBlocking(event));
  }

  @Test
  @DisplayName("S(CONNECTED) x E(KeepAliveFailure) = S'(IDLE) lazy=true")
  void keepAliveFailureTransitionsToIdleWhenLazy() throws Exception {
    ChannelFsm fsm = factory().lazy(true).build().newChannelFsm(State.Connected);
    var event = new Event.KeepAliveFailure(new Throwable("failure"));

    assertEquals(State.Idle, fsm.getFsm().fireEventBlocking(event));
  }

  @Test
  @DisplayName("S(CONNECTED) x E(KeepAliveFailure) = S'(RECONNECT_WAIT) lazy=false")
  void keepAliveFailureTransitionsToReconnectWaitWhenNotLazy() throws Exception {
    ChannelFsm fsm = factory().lazy(false).build().newChannelFsm(State.Connected);
    var event = new Event.KeepAliveFailure(new Throwable("failure"));

    assertEquals(State.ReconnectWait, fsm.getFsm().fireEventBlocking(event));
  }

}
