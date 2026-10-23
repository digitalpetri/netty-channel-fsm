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

import static com.digitalpetri.netty.fsm.FsmTestSupport.assertWithTimeout;
import static com.digitalpetri.netty.fsm.FsmTestSupport.factory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotConnectedActionsTest {

  @Test
  @DisplayName("Internal transition via Disconnect completes successfully")
  void internalTransitionViaDisconnectCompletesSuccessfully() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm();

    var event = new Event.Disconnect();

    assertEquals(State.NotConnected, fsm.getFsm().fireEventBlocking(event));

    assertWithTimeout(() -> event.disconnectFuture.get());
  }

  @Test
  @DisplayName("Internal transition via GetChannel completes exceptionally")
  void internalTransitionViaGetChannelCompletesExceptionally() throws Exception {
    ChannelFsm fsm = factory().build().newChannelFsm();

    var event = new Event.GetChannel();

    assertEquals(State.NotConnected, fsm.getFsm().fireEventBlocking(event));

    assertThrows(ExecutionException.class, () -> event.channelFuture.get());
  }

}
