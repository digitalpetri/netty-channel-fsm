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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Scheduler} that holds every scheduled command until the test runs it, and records the
 * requested delays and cancellations.
 */
class ManualScheduler implements Scheduler {

  private final List<Scheduled> scheduled = new ArrayList<>();

  @Override
  public synchronized Cancellable schedule(Runnable command, long delay, TimeUnit unit) {
    var s = new Scheduled(command, unit.toSeconds(delay));
    scheduled.add(s);
    return s;
  }

  /**
   * Get every command scheduled so far, in scheduling order.
   *
   * @return every command scheduled so far.
   */
  synchronized List<Scheduled> all() {
    return new ArrayList<>(scheduled);
  }

  /**
   * Get the requested delays, in seconds, in scheduling order.
   *
   * @return the requested delays, in seconds.
   */
  synchronized List<Long> delays() {
    List<Long> delays = new ArrayList<>();
    for (Scheduled s : scheduled) {
      delays.add(s.delaySeconds);
    }
    return delays;
  }

  /**
   * Get the commands that have neither run nor been cancelled.
   *
   * @return the pending commands.
   */
  synchronized List<Scheduled> pending() {
    List<Scheduled> pending = new ArrayList<>();
    for (Scheduled s : scheduled) {
      if (!s.ran && !s.cancelled) {
        pending.add(s);
      }
    }
    return pending;
  }

  /**
   * Run every pending command.
   */
  void runPending() {
    for (Scheduled s : pending()) {
      s.run();
    }
  }

  final class Scheduled implements Cancellable {

    final long delaySeconds;

    private final Runnable command;
    private boolean cancelled = false;
    private boolean ran = false;

    private Scheduled(Runnable command, long delaySeconds) {
      this.command = command;
      this.delaySeconds = delaySeconds;
    }

    @Override
    public boolean cancel() {
      synchronized (ManualScheduler.this) {
        if (cancelled || ran) {
          return false;
        }
        cancelled = true;
        return true;
      }
    }

    boolean isCancelled() {
      synchronized (ManualScheduler.this) {
        return cancelled;
      }
    }

    /**
     * Run the command, even if it was cancelled. A ScheduledExecutorService task that has
     * already started when {@code cancel(false)} is called keeps running the same way.
     */
    void run() {
      synchronized (ManualScheduler.this) {
        ran = true;
      }
      command.run();
    }

  }

}
