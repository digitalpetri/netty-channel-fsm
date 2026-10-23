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

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * An {@link Executor} that queues tasks until the test runs them, so a test controls the order in
 * which executor tasks run relative to channel events.
 */
class QueuedExecutor implements Executor {

  private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

  @Override
  public void execute(Runnable command) {
    tasks.addLast(command);
  }

  void runFirst() {
    tasks.removeFirst().run();
  }

  void drain() {
    drain(false);
  }

  void drain(boolean reverse) {
    for (int i = 0; i < 1000; i++) {
      Runnable task = reverse ? tasks.pollLast() : tasks.pollFirst();
      if (task == null) {
        return;
      }
      task.run();
    }
    throw new IllegalStateException("executor did not become idle");
  }

}
