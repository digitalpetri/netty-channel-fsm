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

class ReconnectingTransitionsTest {

  @Test
  @DisplayName("S(RECONNECTING) x E(ConnectFailure) = S'(RECONNECT_WAIT)")
  void connectFailureTransitionsToReconnectWait() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.Reconnecting);
    var event = new Event.ConnectFailure(new Exception("failed"));

    assertEquals(State.ReconnectWait, fsm.getFsm().fireEventBlocking(event));
  }

  @Test
  @DisplayName("S(RECONNECTING) x E(ConnectSuccess) = S'(CONNECTED)")
  void connectSuccessTransitionsToConnected() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.Reconnecting);
    var event = new Event.ConnectSuccess(new EmbeddedChannel());

    assertEquals(State.Connected, fsm.getFsm().fireEventBlocking(event));
  }

}
