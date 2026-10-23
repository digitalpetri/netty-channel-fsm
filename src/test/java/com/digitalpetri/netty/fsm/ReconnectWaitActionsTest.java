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
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_RD;
import static com.digitalpetri.netty.fsm.ChannelFsm.KEY_RDF;
import static com.digitalpetri.netty.fsm.FsmTestSupport.assertEventualState;
import static com.digitalpetri.netty.fsm.FsmTestSupport.assertWithTimeout;
import static com.digitalpetri.netty.fsm.FsmTestSupport.factory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.netty.fsm.ChannelFsm.ConnectFuture;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconnectWaitActionsTest {

  @Test
  @DisplayName(
      "External transition to RECONNECT_WAIT from RECONNECTING via ConnectFailure notifies "
          + "ConnectFuture")
  void transitionFromReconnectingViaConnectFailureNotifiesConnectFuture() throws Exception {
    var connectDelegate = new TestConnectProxy();

    ChannelFsm fsm = factory().connectProxy(connectDelegate).build()
        .newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.Reconnecting);

    var getChannel = new Event.GetChannel();
    assertEquals(State.Reconnecting, fsm.getFsm().fireEventBlocking(getChannel));

    connectDelegate.failure();

    assertWithTimeout(() ->
        assertThrows(ExecutionException.class, () -> getChannel.channelFuture.get())
    );
  }

  @Test
  @DisplayName("External transition to RECONNECT_WAIT sets a new ConnectFuture")
  void transitionSetsNewConnectFuture() throws Exception {
    var connectDelegate = new TestConnectProxy();
    var scheduler = new TestScheduler();

    ChannelFsmFactory factory = factory()
        .connectProxy(connectDelegate)
        .scheduler(scheduler)
        .build();
    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();

    assertEventualState(fsm, State.Connected);

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.ReconnectWait);

    fsm.getFsm().withContext(ctx -> {
      ConnectFuture cf = KEY_CF.get(ctx);
      assertNotNull(cf);
      assertFalse(cf.future.isDone());
    });
  }

  @Test
  @DisplayName("External transition to RECONNECT_WAIT sets reconnect delay and future")
  void transitionSetsReconnectDelayAndFuture() throws Exception {
    var connectDelegate = new TestConnectProxy();
    var scheduler = new TestScheduler();

    ChannelFsmFactory factory = factory()
        .connectProxy(connectDelegate)
        .scheduler(scheduler)
        .build();
    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();

    assertEventualState(fsm, State.Connected);

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.ReconnectWait);

    fsm.getFsm().withContext(ctx -> {
      Long rd = KEY_RD.get(ctx);
      assertEquals(1L, rd);

      assertNotNull(KEY_RDF.get(ctx));
    });
  }

  @Test
  @DisplayName("Subsequent transitions to RECONNECT_WAIT due to failure increments reconnect delay")
  void subsequentTransitionsIncrementReconnectDelay() throws Exception {
    var connectDelegate = new TestConnectProxy();
    var scheduler = new TestScheduler();

    ChannelFsmFactory factory = factory()
        .connectProxy(connectDelegate)
        .scheduler(scheduler)
        .build();
    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();

    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.ReconnectWait);

    for (Long expectedDelay : List.of(2L, 4L, 8L, 16L, 32L, 32L, 32L)) {
      scheduler.execute();
      assertEventualState(fsm, State.Reconnecting);
      connectDelegate.failure();
      assertEventualState(fsm, State.ReconnectWait);
      connectDelegate.reset();

      fsm.getFsm().withContext(ctx -> assertEquals(expectedDelay, KEY_RD.get(ctx)));
    }
  }

  @Test
  @DisplayName("Internal transition via Connect event is handled")
  void internalTransitionViaConnectIsHandled() throws Exception {
    var connectDelegate = new TestConnectProxy();
    var scheduler = new TestScheduler();

    ChannelFsmFactory factory = factory()
        .connectProxy(connectDelegate)
        .scheduler(scheduler)
        .build();
    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();

    assertEventualState(fsm, State.Connected);

    connectDelegate.reset();

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.ReconnectWait);

    var failedConnect = new Event.Connect();
    assertEquals(State.ReconnectWait, fsm.getFsm().fireEventBlocking(failedConnect));

    scheduler.execute();
    assertEventualState(fsm, State.Reconnecting);
    connectDelegate.failure();
    assertEventualState(fsm, State.ReconnectWait);
    connectDelegate.reset();

    assertThrows(ExecutionException.class, () -> failedConnect.channelFuture.get());

    var successfulConnect = new Event.Connect();
    assertEquals(State.ReconnectWait, fsm.getFsm().fireEventBlocking(successfulConnect));

    scheduler.execute();
    assertEventualState(fsm, State.Reconnecting);
    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();

    assertWithTimeout(() -> assertNotNull(successfulConnect.channelFuture.get()));
  }

  @Test
  @DisplayName("Internal transition via GetChannel event is handled")
  void internalTransitionViaGetChannelIsHandled() throws Exception {
    var connectDelegate = new TestConnectProxy();
    var scheduler = new TestScheduler();

    ChannelFsmFactory factory = factory()
        .connectProxy(connectDelegate)
        .scheduler(scheduler)
        .build();
    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.ReconnectWait);

    assertWithTimeout(() -> {
      var getChannel = new Event.GetChannel(false);
      fsm.getFsm().fireEvent(getChannel);

      assertThrows(Exception.class, () -> getChannel.channelFuture.get());
    });

    var failedGetChannel = new Event.GetChannel();
    assertEquals(State.ReconnectWait, fsm.getFsm().fireEventBlocking(failedGetChannel));

    scheduler.execute();
    assertEventualState(fsm, State.Reconnecting);
    connectDelegate.failure();
    assertEventualState(fsm, State.ReconnectWait);
    connectDelegate.reset();

    assertThrows(ExecutionException.class, () -> failedGetChannel.channelFuture.get());

    var successfulGetChannel = new Event.GetChannel();
    assertEquals(State.ReconnectWait, fsm.getFsm().fireEventBlocking(successfulGetChannel));

    scheduler.execute();
    assertEventualState(fsm, State.Reconnecting);
    connectDelegate.success();
    assertEventualState(fsm, State.Connected);
    connectDelegate.reset();

    assertWithTimeout(() -> assertNotNull(successfulGetChannel.channelFuture.get()));
  }

  @Test
  @DisplayName("Transition from RECONNECT_WAIT to NOT_CONNECTED via Disconnect")
  void transitionToNotConnectedViaDisconnect() throws Exception {
    var connectDelegate = new TestConnectProxy();
    var scheduler = new TestScheduler();

    ChannelFsmFactory factory = factory()
        .connectProxy(connectDelegate)
        .scheduler(scheduler)
        .build();
    ChannelFsm fsm = factory.newChannelFsm(State.NotConnected);

    fsm.getFsm().fireEventBlocking(new Event.Connect());
    connectDelegate.success();

    assertEventualState(fsm, State.Connected);

    fsm.getFsm().fireEvent(new Event.ChannelInactive());

    assertEventualState(fsm, State.ReconnectWait);

    var getChannel = new Event.GetChannel();
    assertEquals(State.ReconnectWait, fsm.getFsm().fireEventBlocking(getChannel));

    var disconnect = new Event.Disconnect();
    assertEquals(State.NotConnected, fsm.getFsm().fireEventBlocking(disconnect));

    assertThrows(ExecutionException.class, () -> getChannel.channelFuture.get());

    assertWithTimeout(() -> disconnect.disconnectFuture.get());

    fsm.getFsm().withContext(ctx -> {
      assertNull(KEY_RD.get(ctx));
      assertNull(KEY_RDF.get(ctx));
    });
  }

}
