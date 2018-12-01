package com.digitalpetri.netty.fsm;

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

    /**
     * @see ChannelFsmConfig#isLazy()
     */
    public ChannelFsmConfigBuilder setLazy(boolean lazy) {
        this.lazy = lazy;
        return this;
    }

    /**
     * @see ChannelFsmConfig#isPersistent()
     */
    public ChannelFsmConfigBuilder setPersistent(boolean persistent) {
        this.persistent = persistent;
        return this;
    }

    /**
     * @see ChannelFsmConfig#getMaxIdleSeconds()
     */
    public ChannelFsmConfigBuilder setMaxIdleSeconds(int maxIdleSeconds) {
        this.maxIdleSeconds = maxIdleSeconds;
        return this;
    }

    /**
     * @see ChannelFsmConfig#getMaxReconnectDelaySeconds()
     */
    public ChannelFsmConfigBuilder setMaxReconnectDelaySeconds(int maxReconnectDelaySeconds) {
        this.maxReconnectDelaySeconds = maxReconnectDelaySeconds;
        return this;
    }

    /**
     * @see ChannelFsmConfig#getChannelActions()
     */
    public ChannelFsmConfigBuilder setChannelActions(ChannelActions channelActions) {
        this.channelActions = channelActions;
        return this;
    }

    /**
     * @see ChannelFsmConfig#getExecutor()
     */
    public ChannelFsmConfigBuilder setExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    /**
     * @see ChannelFsmConfig#getScheduler()
     */
    public ChannelFsmConfigBuilder setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
        return this;
    }

    /**
     * @see ChannelFsmConfig#getScheduler()
     */
    public ChannelFsmConfigBuilder setScheduler(ScheduledExecutorService scheduledExecutor) {
        this.scheduler = Scheduler.fromScheduledExecutor(scheduledExecutor);
        return this;
    }

    /**
     * @see ChannelFsmConfig#getLoggerName()
     */
    public ChannelFsmConfigBuilder setLoggerName(String loggerName) {
        this.loggerName = loggerName;
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
            loggerName
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

        ChannelFsmConfigImpl(
            boolean lazy,
            boolean persistent,
            int maxIdleSeconds,
            int maxReconnectDelaySeconds,
            ChannelActions channelActions,
            Executor executor,
            Scheduler scheduler,
            String loggerName) {

            this.lazy = lazy;
            this.persistent = persistent;
            this.maxIdleSeconds = maxIdleSeconds;
            this.maxReconnectDelaySeconds = maxReconnectDelaySeconds;
            this.channelActions = channelActions;
            this.executor = executor;
            this.scheduler = scheduler;
            this.loggerName = loggerName;
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

    }

}
