package com.digitalpetri.netty.fsm;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface Scheduler {

    /**
     * Schedule a command to run after a {@code delay}.
     *
     * @param command the commadn to run.
     * @param delay   the time to delay.
     * @param unit    the time unit of the delay.
     * @return
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
