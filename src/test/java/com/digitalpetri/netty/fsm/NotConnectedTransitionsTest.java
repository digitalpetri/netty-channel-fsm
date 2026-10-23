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

class NotConnectedTransitionsTest {

  @Test
  @DisplayName("S(NOT_CONNECTED) x E(Connect) = S'(CONNECTING)")
  void connectTransitionsToConnecting() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm();

    var event = new Event.Connect();

    assertEquals(
        State.Connecting,
        fsm.getFsm().fireEventBlocking(event),
        "expected State.CONNECTING"
    );
  }

}
