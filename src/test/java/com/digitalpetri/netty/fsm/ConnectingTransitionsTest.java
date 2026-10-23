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

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectingTransitionsTest {

  @Test
  @DisplayName("S(CONNECTING) x E(ConnectFailure) = S'(IDLE) (persistent=true, lazy=true)")
  void connectFailureTransitionsToIdleWhenPersistentAndLazy() throws Exception {
    ChannelFsm fsm = factory().persistent(true).lazy(true).build()
        .newChannelFsm(State.Connecting);
    var event = new Event.ConnectFailure(new Throwable("failed"));

    assertEquals(State.Idle, fsm.getFsm().fireEventBlocking(event));
  }

  @Test
  @DisplayName(
      "S(CONNECTING) x E(ConnectFailure) = S'(RECONNECT_WAIT) (persistent=true, lazy=false)")
  void connectFailureTransitionsToReconnectWaitWhenPersistentAndNotLazy() throws Exception {
    ChannelFsm fsm = factory().persistent(true).lazy(false).build()
        .newChannelFsm(State.Connecting);
    var event = new Event.ConnectFailure(new Throwable("failed"));

    assertEquals(State.ReconnectWait, fsm.getFsm().fireEventBlocking(event));
  }

  @Test
  @DisplayName("S(CONNECTING) x E(ConnectFailure) = S'(NOT_CONNECTED) (persistent=false)")
  void connectFailureTransitionsToNotConnectedWhenNotPersistent() throws Exception {
    ChannelFsm fsm = factory().persistent(false).build().newChannelFsm(State.Connecting);
    var event = new Event.ConnectFailure(new Throwable("failed"));

    assertEquals(State.NotConnected, fsm.getFsm().fireEventBlocking(event));
  }

  @Test
  @DisplayName("S(CONNECTING) x E(ConnectSuccess) = S'(CONNECTED)")
  void connectSuccessTransitionsToConnected() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.Connecting);
    var event = new Event.ConnectSuccess(new EmbeddedChannel());

    assertEquals(State.Connected, fsm.getFsm().fireEventBlocking(event));
  }

}
