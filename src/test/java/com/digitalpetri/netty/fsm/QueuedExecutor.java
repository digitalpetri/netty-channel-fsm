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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * An {@link Executor} that queues tasks until the test runs them, so a test controls the order in
 * which executor tasks run relative to channel events.
 *
 * <p>Any thread may submit tasks, for example an event loop thread, but only the test runs them.
 * Running tasks out of submission order models a thread pool, where independent tasks can run in
 * any order.
 */
class QueuedExecutor implements Executor {

  private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
  private final List<Throwable> failures = new ArrayList<>();

  private boolean captureFailures = false;

  /**
   * Record exceptions thrown by tasks instead of rethrowing them, the way a thread pool logs them
   * and keeps running other tasks.
   *
   * @return this {@link QueuedExecutor}.
   */
  QueuedExecutor captureFailures() {
    captureFailures = true;
    return this;
  }

  synchronized List<Throwable> failures() {
    return new ArrayList<>(failures);
  }

  @Override
  public synchronized void execute(Runnable command) {
    tasks.addLast(command);
  }

  synchronized int size() {
    return tasks.size();
  }

  void runFirst() {
    Runnable task;
    synchronized (this) {
      task = tasks.removeFirst();
    }
    run(task);
  }

  void runLast() {
    Runnable task;
    synchronized (this) {
      task = tasks.removeLast();
    }
    run(task);
  }

  void drain() {
    drain(false);
  }

  void drain(boolean reverse) {
    for (int i = 0; i < 1000; i++) {
      Runnable task;
      synchronized (this) {
        task = reverse ? tasks.pollLast() : tasks.pollFirst();
      }
      if (task == null) {
        return;
      }
      run(task);
    }
    throw new IllegalStateException("executor did not become idle");
  }

  private void run(Runnable task) {
    try {
      task.run();
    } catch (RuntimeException | Error e) {
      if (!captureFailures) {
        throw e;
      }
      synchronized (this) {
        failures.add(e);
      }
    }
  }

}
