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

class IdleTransitionsTest {

  @Test
  @DisplayName("S(IDLE) x E(Connect) = S'(RECONNECTING)")
  void connectTransitionsToReconnecting() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.Idle);
    var event = new Event.Connect();

    assertEquals(
        State.Reconnecting,
        fsm.getFsm().fireEventBlocking(event),
        "expected State.Reconnecting"
    );
  }

  @Test
  @DisplayName("S(IDLE) x E(GetChannel) = S'(RECONNECTING)")
  void getChannelTransitionsToReconnecting() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.Idle);
    var event = new Event.GetChannel();

    assertEquals(
        State.Reconnecting,
        fsm.getFsm().fireEventBlocking(event),
        "expected State.Reconnecting"
    );
  }

  @Test
  @DisplayName("S(IDLE) x E(Disconnect) = S'(NOT_CONNECTED)")
  void disconnectTransitionsToNotConnected() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.Idle);
    var event = new Event.Disconnect();

    assertEquals(
        State.NotConnected,
        fsm.getFsm().fireEventBlocking(event),
        "expected State.NOT_CONNECTED"
    );
  }

}
