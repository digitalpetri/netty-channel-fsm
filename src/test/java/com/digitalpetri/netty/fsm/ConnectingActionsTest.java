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

import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_CF;
import static com.digitalpetri.netty.fsm.FsmTestSupport.assertWithTimeout;
import static com.digitalpetri.netty.fsm.FsmTestSupport.factory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectingActionsTest {

  @Test
  @DisplayName("External transition to CONNECTING via Connect")
  void externalTransitionViaConnect() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    var event = new Event.Connect();

    assertEquals(State.Connecting, fsm.getFsm().fireEventBlocking(event));

    assertNotNull(fsm.getFsm().getFromContext(ctx -> KEY_CF.get(ctx)));

    connectDelegate.success();

    assertWithTimeout(() -> assertNotNull(event.channelFuture.get()));
  }

  @Test
  @DisplayName("Internal transition via Connect")
  void internalTransitionViaConnect() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    List<Event.Connect> events = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      events.add(new Event.Connect());
    }

    for (Event.Connect event : events) {
      assertEquals(State.Connecting, fsm.getFsm().fireEventBlocking(event));
    }

    connectDelegate.success();

    for (Event.Connect event : events) {
      assertWithTimeout(() -> assertNotNull(event.channelFuture.get()));
    }
  }

  @Test
  @DisplayName("Internal transition via GetChannel")
  void internalTransitionViaGetChannel() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEvent(new Event.Connect());

    List<Event.GetChannel> events = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      events.add(new Event.GetChannel());
    }

    for (Event.GetChannel event : events) {
      assertEquals(State.Connecting, fsm.getFsm().fireEventBlocking(event));
    }

    connectDelegate.success();

    for (Event.GetChannel event : events) {
      assertWithTimeout(() -> assertNotNull(event.channelFuture.get()));
    }
  }

  @Test
  @DisplayName("Internal transition via Disconnect")
  void internalTransitionViaDisconnect() throws Exception {
    var connectDelegate = new TestConnectProxy();

    var disconnectFuture = new CompletableFuture<Void>();

    DisconnectProxy disconnectDelegate = (ctx, channel) -> disconnectFuture;

    ChannelFsm fsm = factory()
        .connectProxy(connectDelegate)
        .disconnectProxy(disconnectDelegate)
        .build()
        .newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEvent(new Event.Connect());

    var event = new Event.Disconnect();
    assertEquals(State.Connecting, fsm.getFsm().fireEventBlocking(event));

    // move CONNECTING -> CONNECTED
    connectDelegate.success();

    // the Disconnect is processed from the shelf...
    // CONNECTED -> DISCONNECTING
    assertWithTimeout(() -> {
      while (fsm.getFsm().getState() != State.Disconnecting) {
        Thread.sleep(1);
      }
    });
  }

  @Test
  @DisplayName("External transition via ConnectFailure")
  void externalTransitionViaConnectFailure() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    var event = new Event.Connect();
    assertEquals(State.Connecting, fsm.getFsm().fireEventBlocking(event));

    List<Event.Connect> connectEvents = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      var connect = new Event.Connect();
      connectEvents.add(connect);
      fsm.getFsm().fireEvent(connect);
    }
    List<Event.GetChannel> getChannelEvents = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      var getChannel = new Event.GetChannel();
      getChannelEvents.add(getChannel);
      fsm.getFsm().fireEvent(getChannel);
    }

    connectDelegate.failure();

    assertWithTimeout(() -> {
      assertThrows(ExecutionException.class, () -> event.channelFuture.get());

      for (Event.Connect connect : connectEvents) {
        assertThrows(ExecutionException.class, () -> connect.channelFuture.get());
      }
      for (Event.GetChannel getChannel : getChannelEvents) {
        assertThrows(ExecutionException.class, () -> getChannel.channelFuture.get());
      }
    });
  }

}
