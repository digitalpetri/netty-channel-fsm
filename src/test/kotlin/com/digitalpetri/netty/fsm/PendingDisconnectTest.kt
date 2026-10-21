/*
 * Copyright (c) 2026 Kevin Herron
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.digitalpetri.netty.fsm

import com.digitalpetri.fsm.FsmContext
import io.netty.channel.Channel
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class PendingDisconnectTest {

    @Test
    fun `Disconnect closes a pending initial connection before completing`() {
        pendingDisconnect(State.NotConnected, State.Connecting)
    }

    @Test
    fun `Disconnect closes a pending reconnection before completing`() {
        pendingDisconnect(State.Idle, State.Reconnecting)
    }

    private fun pendingDisconnect(initial: State, connecting: State) {
        val executor = QueuedExecutor()
        val channel = EmbeddedChannel()
        val connection = CompletableFuture<Channel>()
        val closure = CompletableFuture<Void>()
        var disconnectCalls = 0

        val actions = object : ChannelActions {
            override fun connect(ctx: FsmContext<State, Event>): CompletableFuture<Channel> = connection

            override fun disconnect(ctx: FsmContext<State, Event>, target: Channel): CompletableFuture<Void> {
                assertSame(channel, target)
                disconnectCalls++
                target.close()
                return closure
            }
        }

        val config = ChannelFsmConfig.newBuilder()
            .setExecutor(executor)
            .setMaxIdleSeconds(0)
            .setLazy(true)
            .setChannelActions(actions)
            .build()
        val fsm = ChannelFsmFactory(config).newChannelFsm(initial)

        try {
            val connected = fsm.connect()
            executor.drain()
            assertEquals(connecting, fsm.state)

            val disconnected = fsm.disconnect()
            executor.drain()
            assertFalse(disconnected.isDone)

            connection.complete(channel)
            executor.runFirst()
            assertEquals(State.Connected, fsm.state)

            // A pool can execute the shelved Disconnect before the separately scheduled task
            // that publishes the connected channel. Keep FSM events serialized, but run the
            // independent executor tasks in that order.
            executor.drain(reverse = true)

            assertEquals(1, disconnectCalls)
            assertFalse(channel.isOpen)
            assertTrue(connected.isDone)
            assertFalse(disconnected.isDone, "disconnect must await the channel action's completion")
            assertEquals(State.Disconnecting, fsm.state)

            closure.complete(null)
            executor.drain()

            assertTrue(disconnected.isDone)
            assertFalse(disconnected.isCompletedExceptionally)
            assertEquals(State.NotConnected, fsm.state)
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runFirst() {
            tasks.removeFirst().run()
        }

        fun drain(reverse: Boolean = false) {
            repeat(1000) {
                val task = if (reverse) tasks.pollLast() else tasks.pollFirst()
                if (task == null) return
                task.run()
            }
            error("executor did not become idle")
        }
    }
}
