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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ChannelFsmConfigBuilder {

    static int DEFAULT_MAX_RECONNECT_DELAY_SECONDS = 32;

    private boolean lazy = false;
    private boolean persistent = true;
    private int maxIdleSeconds = 15;
    private int maxReconnectDelaySeconds = DEFAULT_MAX_RECONNECT_DELAY_SECONDS;
    private ChannelActions channelActions;
    private Executor executor;
    private Scheduler scheduler;
    private String loggerName;
    private Map<String, String> loggingContext = Collections.emptyMap();

    /**
     * @param lazy {@code true} if the ChannelFsm should be lazy,
     * @return this {@link ChannelFsmConfigBuilder}.
     * @see ChannelFsmConfig#isLazy()
     */
    public ChannelFsmConfigBuilder setLazy(boolean lazy) {
        this.lazy = lazy;
        return this;
    }

    /**
     * @param persistent {@code true} if the ChannelFsm should be persistent in its initial connect attempt.
     * @return this {@link ChannelFsmConfigBuilder}.
     * @see ChannelFsmConfig#isPersistent()
     */
    public ChannelFsmConfigBuilder setPersistent(boolean persistent) {
        this.persistent = persistent;
        return this;
    }

    /**
     * @param maxIdleSeconds the maximum amount of time, in seconds, before a keep alive occurs on an idle channel.
     * @return this {@link ChannelFsmConfigBuilder}.
     * @see ChannelFsmConfig#getMaxIdleSeconds()
     */
    public ChannelFsmConfigBuilder setMaxIdleSeconds(int maxIdleSeconds) {
        this.maxIdleSeconds = maxIdleSeconds;
        return this;
    }

    /**
     * @param maxReconnectDelaySeconds the maximum delay to occur between reconnect attempts.
     * @return this {@link ChannelFsmConfigBuilder}.
     * @see ChannelFsmConfig#getMaxReconnectDelaySeconds()
     */
    public ChannelFsmConfigBuilder setMaxReconnectDelaySeconds(int maxReconnectDelaySeconds) {
        this.maxReconnectDelaySeconds = maxReconnectDelaySeconds;
        return this;
    }

    /**
     * @param channelActions the {@link ChannelActions} delegate.
     * @return this {@link ChannelFsmConfigBuilder}.
     * @see ChannelFsmConfig#getChannelActions()
     */
    public ChannelFsmConfigBuilder setChannelActions(ChannelActions channelActions) {
        this.channelActions = channelActions;
        return this;
    }

    /**
     * @param executor the {@link Executor} to use.
     * @return this {@link ChannelFsmConfigBuilder}.
     * @see ChannelFsmConfig#getExecutor()
     */
    public ChannelFsmConfigBuilder setExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    /**
     * @param scheduler the {@link Scheduler} to use.
     * @return this {@link ChannelFsmConfigBuilder}.
     * @see ChannelFsmConfig#getScheduler()
     */
    public ChannelFsmConfigBuilder setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
        return this;
    }

    /**
     * @param scheduledExecutor the {@link ScheduledExecutorService} to use.
     * @return this {@link ChannelFsmConfigBuilder}.
     * @see ChannelFsmConfig#getScheduler()
     */
    public ChannelFsmConfigBuilder setScheduler(ScheduledExecutorService scheduledExecutor) {
        this.scheduler = Scheduler.fromScheduledExecutor(scheduledExecutor);
        return this;
    }

    /**
     * @param loggerName the logger name the FSM should use.
     * @return this {@link ChannelFsmConfigBuilder}.
     * @see ChannelFsmConfig#getLoggerName()
     */
    public ChannelFsmConfigBuilder setLoggerName(String loggerName) {
        this.loggerName = loggerName;
        return this;
    }

    /**
     * Set the logging context Map a {@link ChannelFsm} instance will use.
     * <p>
     * Keys and values in the Map will be set on the SLF4J {@link org.slf4j.MDC} when logging.
     * <p>
     * This method makes a defensive copy of {@code loggingContext}.
     *
     * @param loggingContext the logging context Map a {@link ChannelFsm} instance will use.
     * @return this {@link ChannelFsmConfigBuilder}
     * @see ChannelFsmConfig#getLoggingContext()
     */
    public ChannelFsmConfigBuilder setLoggingContext(Map<String, String> loggingContext) {
        this.loggingContext = new ConcurrentHashMap<>(loggingContext);
        return this;
    }

    public ChannelFsmConfig build() {
        if (channelActions == null) {
            throw new IllegalArgumentException("channelActions must be non-null");
        }
        if (maxReconnectDelaySeconds < 1) {
            maxReconnectDelaySeconds = DEFAULT_MAX_RECONNECT_DELAY_SECONDS;
        }
        if (executor == null) {
            executor = SharedExecutor.INSTANCE;
        }
        if (scheduler == null) {
            scheduler = SharedScheduler.INSTANCE;
        }
        if (loggerName == null) {
            loggerName = ChannelFsm.class.getName();
        }

        return new ChannelFsmConfigImpl(
            lazy,
            persistent,
            maxIdleSeconds,
            maxReconnectDelaySeconds,
            channelActions,
            executor,
            scheduler,
            loggerName,
            loggingContext
        );
    }

    private static class SharedExecutor {
        private static final ExecutorService INSTANCE = Executors.newSingleThreadExecutor();
    }

    private static class SharedScheduler {
        private static final Scheduler INSTANCE =
            Scheduler.fromScheduledExecutor(Executors.newSingleThreadScheduledExecutor());
    }

    private static class ChannelFsmConfigImpl implements ChannelFsmConfig {

        private final boolean lazy;
        private final boolean persistent;
        private final int maxIdleSeconds;
        private final int maxReconnectDelaySeconds;
        private final ChannelActions channelActions;
        private final Executor executor;
        private final Scheduler scheduler;
        private final String loggerName;
        private final Map<String, String> loggingContext;

        ChannelFsmConfigImpl(
            boolean lazy,
            boolean persistent,
            int maxIdleSeconds,
            int maxReconnectDelaySeconds,
            ChannelActions channelActions,
            Executor executor,
            Scheduler scheduler,
            String loggerName,
            Map<String, String> loggingContext
        ) {

            this.lazy = lazy;
            this.persistent = persistent;
            this.maxIdleSeconds = maxIdleSeconds;
            this.maxReconnectDelaySeconds = maxReconnectDelaySeconds;
            this.channelActions = channelActions;
            this.executor = executor;
            this.scheduler = scheduler;
            this.loggerName = loggerName;
            this.loggingContext = loggingContext;
        }

        @Override
        public boolean isLazy() {
            return lazy;
        }

        @Override
        public boolean isPersistent() {
            return persistent;
        }

        @Override
        public int getMaxIdleSeconds() {
            return maxIdleSeconds;
        }

        @Override
        public int getMaxReconnectDelaySeconds() {
            return maxReconnectDelaySeconds;
        }

        @Override
        public ChannelActions getChannelActions() {
            return channelActions;
        }

        @Override
        public Executor getExecutor() {
            return executor;
        }

        @Override
        public Scheduler getScheduler() {
            return scheduler;
        }

        @Override
        public String getLoggerName() {
            return loggerName;
        }

        @Override
        public Map<String, String> getLoggingContext() {
            return loggingContext;
        }

    }

}
