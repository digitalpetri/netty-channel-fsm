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

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransitionListenerIsolationTest {

  @Test
  @DisplayName("A TransitionListener that throws does not hide transitions from other listeners")
  void throwingListenerDoesNotAffectOtherListeners() {
    var executor = new QueuedExecutor();
    var actions = new RecordingChannelActions();
    ChannelFsm fsm = new ChannelFsmFactory(
        stepConfig(executor, new ManualScheduler(), actions).build()
    ).newChannelFsm();

    List<String> transitions = new CopyOnWriteArrayList<>();
    fsm.addTransitionListener((from, to, via) -> {
      throw new IllegalStateException("listener failed");
    });
    fsm.addTransitionListener((from, to, via) -> transitions.add(from + "->" + to));

    var channel = new EmbeddedChannel();
    try {
      fsm.connect();
      executor.drain();
      actions.lastConnect().complete(channel);
      executor.drain();
    } finally {
      channel.finishAndReleaseAll();
    }

    assertEquals(List.of("NotConnected->Connecting", "Connecting->Connected"), transitions);
  }

}
