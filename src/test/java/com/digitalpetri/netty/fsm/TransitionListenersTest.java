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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransitionListenersTest {

  @Test
  @DisplayName("TransitionListeners receive callbacks")
  void transitionListenersReceiveCallbacks() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    var listenerCalled = new AtomicBoolean(false);

    fsm.addTransitionListener((from, to, via) -> {
      assertEquals(State.NotConnected, from);
      assertEquals(State.Connecting, to);
      assertTrue(via instanceof Event.Connect);

      listenerCalled.set(true);
    });

    var event = new Event.Connect();
    assertEquals(State.Connecting, fsm.getFsm().fireEventBlocking(event));

    assertTrue(listenerCalled.get());
  }

}
