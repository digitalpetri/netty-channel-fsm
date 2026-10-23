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

class ReconnectWaitTransitionsTest {

  @Test
  @DisplayName("S(RECONNECT_WAIT) x E(ReconnectDelayElapsed) = S'(RECONNECTING)")
  void reconnectDelayElapsedTransitionsToReconnecting() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.ReconnectWait);
    var event = new Event.ReconnectDelayElapsed();

    assertEquals(State.Reconnecting, fsm.getFsm().fireEventBlocking(event));
  }

  @Test
  @DisplayName("S(RECONNECT_WAIT) x E(Disconnect) = S'(NOT_CONNECTED)")
  void disconnectTransitionsToNotConnected() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm(State.ReconnectWait);
    var event = new Event.Disconnect();

    assertEquals(State.NotConnected, fsm.getFsm().fireEventBlocking(event));
  }

}
