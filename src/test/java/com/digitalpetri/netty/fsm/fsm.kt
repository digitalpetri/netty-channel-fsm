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

package com.digitalpetri.netty.fsm

import com.digitalpetri.strictmachine.FsmContext
import io.netty.channel.Channel
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import java.lang.Thread.sleep
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


fun assertWithTimeout(timeout: Duration = Duration.ofSeconds(1), executable: () -> Unit) {
    assertTimeoutPreemptively(timeout, executable)
}

fun assertEventualState(fsm: ChannelFsm, state: State) {
    assertWithTimeout {
        while (fsm.fsm.state != state) {
            sleep(1)
        }
    }
}

fun factory(
    lazy: Boolean = false,
    persistent: Boolean = false,
    maxIdleSeconds: Int = 0,
    connectProxy: ConnectProxy = connectDelegate(true),
    disconnectProxy: DisconnectProxy = disconnectDelegate(true),
    keepAliveProxy: KeepAliveProxy = object : KeepAliveProxy {
        override fun keepAlive(ctx: FsmContext<State, Event>, channel: Channel): CompletableFuture<Void> {
            return completedFuture(null)
        }
    },
    executor: Executor = Executors.newSingleThreadExecutor(),
    scheduler: Scheduler = Scheduler { command, _, unit ->
        // schedule immediately
        val f = Executors.newSingleThreadScheduledExecutor().schedule(command, 0, unit)

        Scheduler.Cancellable { f.cancel(false) }
    }
): ChannelFsmFactory {

    val channelActions = object : ChannelActions {
        override fun connect(ctx: FsmContext<State, Event>): CompletableFuture<Channel> {
            return connectProxy.connect(ctx)
        }

        override fun disconnect(ctx: FsmContext<State, Event>, channel: Channel): CompletableFuture<Void> {
            return disconnectProxy.disconnect(ctx, channel)
        }

        override fun keepAlive(ctx: FsmContext<State, Event>, channel: Channel): CompletableFuture<Void> {
            return keepAliveProxy.keepAlive(ctx, channel)
        }
    }

    val config = ChannelFsmConfig.newBuilder().apply {
        setLazy(lazy)
        setPersistent(persistent)
        setMaxIdleSeconds(maxIdleSeconds)
        setChannelActions(channelActions)
        setExecutor(executor)
        setScheduler(scheduler)
    }

    return ChannelFsmFactory(config.build())
}

fun connectDelegate(success: Boolean) = object : ConnectProxy {
    override fun connect(ctx: FsmContext<State, Event>): CompletableFuture<Channel> {
        return if (success) {
            completedFuture(EmbeddedChannel())
        } else {
            CompletableFuture<Channel>().apply {
                completeExceptionally(Exception("failed"))
            }
        }
    }
}

fun disconnectDelegate(success: Boolean) = object : DisconnectProxy {
    override fun disconnect(ctx: FsmContext<State, Event>, channel: Channel): CompletableFuture<Void> {
        return if (success) {
            completedFuture(null)
        } else {
            CompletableFuture<Void>().apply {
                completeExceptionally(Exception("failed"))
            }
        }
    }
}

class TestConnectProxy : ConnectProxy {
    private var future = CompletableFuture<Channel>()

    override fun connect(ctx: FsmContext<State, Event>): CompletableFuture<Channel> = synchronized(this) {
        return future
    }

    fun success() = synchronized(this) {
        future.complete(EmbeddedChannel())
    }

    fun failure() = synchronized(this) {
        future.completeExceptionally(Exception("failed"))
    }

    fun reset() = synchronized(this) {
        future = CompletableFuture()
    }
}

class TestDisconnectProxy : DisconnectProxy {
    private var future = CompletableFuture<Void>()

    override fun disconnect(ctx: FsmContext<State, Event>, channel: Channel): CompletableFuture<Void> =
        synchronized(this) {
            return future
        }

    fun success() = synchronized(this) {
        future.complete(null)
    }

    fun failure() = synchronized(this) {
        future.completeExceptionally(Exception("failed"))
    }

    fun reset() = synchronized(this) {
        future = CompletableFuture()
    }
}

class TestScheduler : Scheduler {

    private val commandRef = AtomicReference<Runnable>()

    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): Scheduler.Cancellable {
        commandRef.set(command)

        return Scheduler.Cancellable {
            commandRef.getAndSet(null) != null
        }
    }

    fun execute() {
        commandRef.getAndSet(null)?.run()
    }

}

interface ConnectProxy {

    /**
     * Bootstrap a new [Channel] and return a [CompletableFuture] that completes successfully when the
     * Channel is ready to use or completes exceptionally if the Channel could not be created or made ready to use for
     * any reason.
     *
     * @param ctx the [FsmContext].
     * @return a [CompletableFuture] that completes successfully when the
     * Channel is ready to use or completes exceptionally if the Channel could not be created or made ready to use for
     * any reason.
     */
    fun connect(ctx: FsmContext<State, Event>): CompletableFuture<Channel>

}


interface DisconnectProxy {

    /**
     * Perform any disconnect logic and then close `channel`, returning a [CompletableFuture] that completes
     * successfully when the Channel has disconnected or completes exceptionally if the channel could not be
     * disconnected for any reason.
     *
     *
     * The state machine advances the same way regardless of how the future is completed.
     *
     * @param ctx     the [FsmContext].
     * @param channel the [Channel] to disconnect.
     * @return a [CompletableFuture] that completes successfully when the Channel
     * * has disconnected or completes exceptionally if the channel could not be disconnected for any reason.
     */
    fun disconnect(ctx: FsmContext<State, Event>, channel: Channel): CompletableFuture<Void>

}

interface KeepAliveProxy {

    /**
     * Perform a keep-alive action because the Channel has been idle for longer than `maxIdleSeconds`.
     *
     *
     * Although the keep-alive action is implementation dependent the intended usage would be to do something send a
     * request that tests the Channel to make sure it's still valid.
     *
     * @param ctx     the [FsmContext]
     * @param channel the [Channel] to send the keep-alive on.
     * @return a [CompletableFuture] that completes successfully if the channel is still valid and completes
     * exceptionally otherwise.
     */
    fun keepAlive(ctx: FsmContext<State, Event>, channel: Channel): CompletableFuture<Void>

}
