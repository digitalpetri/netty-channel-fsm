/*
 * Copyright (c) 2026 Kevin Herron
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.digitalpetri.netty.fsm;

import static com.digitalpetri.netty.fsm.FsmTestSupport.stepConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * While CONNECTED, getChannel() completes without firing an event. What a caller does with the
 * returned future must not affect the FSM.
 */
class GetChannelFastPathTest {

  private final QueuedExecutor executor = new QueuedExecutor();
  private final RecordingChannelActions actions = new RecordingChannelActions();
  private final EmbeddedChannel channel = new EmbeddedChannel();

  private final ChannelFsm fsm = new ChannelFsmFactory(
      stepConfig(executor, new ManualScheduler(), actions).build()
  ).newChannelFsm();

  @AfterEach
  void releaseChannel() {
    channel.finishAndReleaseAll();
  }

  // The FSM enters CONNECTED before an executor task completes its ConnectFuture. A caller that
  // gives up on getChannel() in that window, e.g. orTimeout() or a cancelled Kotlin await(),
  // must not cancel the FSM's own future.
  @Test
  @DisplayName("Cancelling a getChannel() future while CONNECTED does not affect the FSM")
  void cancellingFutureDoesNotAffectFsm() throws Exception {
    final CompletableFuture<Channel> connected = fsm.connect();
    executor.drain();
    actions.lastConnect().complete(channel);
    executor.runFirst();
    assertEquals(State.Connected, fsm.getState());

    fsm.getChannel().cancel(false);
    executor.drain();

    assertSame(channel, connected.get(), "connect() completes with the channel");
    assertSame(channel, fsm.getChannel().get(), "getChannel() completes with the channel");

    fsm.disconnect();
    executor.drain();

    assertEquals(State.NotConnected, fsm.getState());
    assertEquals(1, actions.disconnects.size(), "ChannelActions.disconnect was called");
    assertFalse(channel.isOpen());
  }

  @Test
  @DisplayName("Replacing a getChannel() result while CONNECTED does not affect the FSM")
  void obtrudingFutureDoesNotAffectFsm() throws Exception {
    fsm.connect();
    executor.drain();
    actions.lastConnect().complete(channel);
    executor.drain();
    assertEquals(State.Connected, fsm.getState());

    fsm.getChannel().obtrudeException(new Exception("caller"));

    assertSame(channel, fsm.getChannel().get(), "getChannel() completes with the channel");
  }

}
