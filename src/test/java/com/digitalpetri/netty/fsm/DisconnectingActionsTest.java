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

import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_DF;
import static com.digitalpetri.netty.fsm.FsmTestSupport.assertEventualState;
import static com.digitalpetri.netty.fsm.FsmTestSupport.assertWithTimeout;
import static com.digitalpetri.netty.fsm.FsmTestSupport.factory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisconnectingActionsTest {

  @Test
  @DisplayName("External transition to DISCONNECTING from CONNECTED via Disconnect")
  void externalTransitionFromConnectedViaDisconnect() throws Exception {
    var connectDelegate = new TestConnectProxy();
    var disconnectDelegate = new TestDisconnectProxy();

    ChannelFsmFactory factory = factory()
        .connectProxy(connectDelegate)
        .disconnectProxy(disconnectDelegate)
        .build();

    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    moveToConnected(fsm, connectDelegate);

    var disconnect = new Event.Disconnect();
    fsm.getFsm().fireEventBlocking(disconnect);

    assertNotNull(fsm.getFsm().getFromContext(ctx -> KEY_DF.get(ctx)));

    disconnectDelegate.success();

    assertWithTimeout(() -> {
      disconnect.disconnectFuture.get();
      assertTrue(disconnect.disconnectFuture.isDone());
      assertFalse(disconnect.disconnectFuture.isCompletedExceptionally());
    });
  }

  @Test
  @DisplayName("Internal transition via Connect")
  void internalTransitionViaConnect() throws Exception {
    var disconnectDelegate = new TestDisconnectProxy();

    ChannelFsmFactory factory = factory()
        .disconnectProxy(disconnectDelegate)
        .build();

    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    var initialConnect = new Event.Connect();
    fsm.getFsm().fireEventBlocking(initialConnect);
    assertWithTimeout(() -> initialConnect.channelFuture.get());

    var disconnect = new Event.Disconnect();
    assertEquals(State.Disconnecting, fsm.getFsm().fireEventBlocking(disconnect));

    var connect = new Event.Connect();
    assertEquals(State.Disconnecting, fsm.getFsm().fireEventBlocking(connect));

    disconnectDelegate.success();

    assertWithTimeout(() -> {
      disconnect.disconnectFuture.get();
      assertTrue(disconnect.disconnectFuture.isDone());
      assertFalse(disconnect.disconnectFuture.isCompletedExceptionally());

      assertNotNull(connect.channelFuture.get());
    });
  }

  @Test
  @DisplayName("Internal transition via GetChannel")
  void internalTransitionViaGetChannel() throws Exception {
    var disconnectDelegate = new TestDisconnectProxy();

    ChannelFsmFactory factory = factory()
        .disconnectProxy(disconnectDelegate)
        .build();

    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    var initialConnect = new Event.Connect();
    fsm.getFsm().fireEventBlocking(initialConnect);
    assertWithTimeout(() -> initialConnect.channelFuture.get());

    var disconnect = new Event.Disconnect();
    assertEquals(State.Disconnecting, fsm.getFsm().fireEventBlocking(disconnect));

    var getChannel = new Event.GetChannel();
    assertEquals(State.Disconnecting, fsm.getFsm().fireEventBlocking(getChannel));

    disconnectDelegate.success();

    assertWithTimeout(() -> {
      disconnect.disconnectFuture.get();
      assertTrue(disconnect.disconnectFuture.isDone());
      assertFalse(disconnect.disconnectFuture.isCompletedExceptionally());

      assertThrows(ExecutionException.class, () -> getChannel.channelFuture.get());
    });
  }

  @Test
  @DisplayName("Internal transition via Disconnect")
  void internalTransitionViaDisconnect() throws Exception {
    var disconnectDelegate = new TestDisconnectProxy();

    ChannelFsmFactory factory = factory()
        .disconnectProxy(disconnectDelegate)
        .build();

    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    var initialConnect = new Event.Connect();
    fsm.getFsm().fireEventBlocking(initialConnect);
    assertWithTimeout(() -> initialConnect.channelFuture.get());

    var disconnect = new Event.Disconnect();
    assertEquals(State.Disconnecting, fsm.getFsm().fireEventBlocking(disconnect));

    List<Event.Disconnect> disconnects = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      var event = new Event.Disconnect();
      disconnects.add(event);
      assertEquals(State.Disconnecting, fsm.getFsm().fireEventBlocking(event));
    }

    disconnectDelegate.success();

    assertWithTimeout(() -> {
      disconnect.disconnectFuture.get();
      assertTrue(disconnect.disconnectFuture.isDone());
      assertFalse(disconnect.disconnectFuture.isCompletedExceptionally());

      for (Event.Disconnect event : disconnects) {
        event.disconnectFuture.get();
        assertTrue(event.disconnectFuture.isDone());
        assertFalse(event.disconnectFuture.isCompletedExceptionally());
      }
    });
  }

  private static void moveToConnected(ChannelFsm fsm, TestConnectProxy connectDelegate)
      throws Exception {

    var connect = new Event.Connect();
    fsm.getFsm().fireEventBlocking(connect);
    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();
    assertWithTimeout(() -> assertNotNull(connect.channelFuture.get()));
  }

}
