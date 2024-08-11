/*
 * Copyright 2018 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.netty.fsm;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface Scheduler {

  /**
   * Schedule a command to run after a {@code delay}.
   *
   * @param command the commadn to run.
   * @param delay the time to delay.
   * @param unit the time unit of the delay.
   * @return a {@link Cancellable} that can be used to attempt cancellation if needed.
   */
  Cancellable schedule(Runnable command, long delay, TimeUnit unit);

  interface Cancellable {

    /**
     * Attempt to cancel a scheduled command.
     *
     * @return {@code true} if the command was canceled.
     */
    boolean cancel();

  }

  /**
   * Create a {@link Scheduler} from the provided {@link ScheduledExecutorService}.
   *
   * @param scheduledExecutor a {@link ScheduledExecutorService}.
   * @return a {@link Scheduler}.
   */
  static Scheduler fromScheduledExecutor(ScheduledExecutorService scheduledExecutor) {
    return (command, delay, unit) -> {
      ScheduledFuture<?> future = scheduledExecutor.schedule(command, delay, unit);

      return () -> future.cancel(false);
    };
  }

}
