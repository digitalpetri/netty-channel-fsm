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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class TestScheduler implements Scheduler {

  private final AtomicReference<Runnable> commandRef = new AtomicReference<>();

  @Override
  public Cancellable schedule(Runnable command, long delay, TimeUnit unit) {
    commandRef.set(command);

    return () -> commandRef.getAndSet(null) != null;
  }

  void execute() {
    Runnable command = commandRef.getAndSet(null);

    if (command != null) {
      command.run();
    }
  }

}
