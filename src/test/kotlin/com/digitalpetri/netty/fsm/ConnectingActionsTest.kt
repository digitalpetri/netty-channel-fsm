/*
 * Copyright (c) 2024 Kevin Herron
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.digitalpetri.netty.fsm

import com.digitalpetri.fsm.FsmContext
import com.digitalpetri.netty.fsm.ChannelFsm.KEY_CF
import io.netty.channel.Channel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException


class ConnectingActionsTest {

    @Test
    fun `External transition to CONNECTING via Connect`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        val event = Event.Connect()

        assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(event))

        assertNotNull(fsm.fsm.getFromContext { ctx -> KEY_CF.get(ctx) })

        connectDelegate.success()

        assertWithTimeout {
            assertNotNull(event.channelFuture.get())
        }
    }

    @Test
    fun `Internal transition via Connect`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        val events = MutableList(5) { Event.Connect() }

        events.forEach {
            assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(it))
        }

        connectDelegate.success()

        events.forEach {
            assertWithTimeout {
                assertNotNull(it.channelFuture.get())
            }
        }
    }

    @Test
    fun `Internal transition via GetChannel`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEvent(Event.Connect())

        val events = MutableList(5) { Event.GetChannel() }

        events.forEach {
            assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(it))
        }

        connectDelegate.success()

        events.forEach {
            assertWithTimeout {
                assertNotNull(it.channelFuture.get())
            }
        }
    }

    @Test
    fun `Internal transition via Disconnect`() {
        val connectDelegate = TestConnectProxy()

        val disconnectFuture = CompletableFuture<Void>()

        val disconnectDelegate = object : DisconnectProxy {
            override fun disconnect(ctx: FsmContext<State, Event>, channel: Channel): CompletableFuture<Void> {
                return disconnectFuture
            }
        }

        val fsm = factory(
            connectProxy = connectDelegate,
            disconnectProxy = disconnectDelegate
        ).newChannelFsm(State.NotConnected)

        fsm.fsm.fireEvent(Event.Connect())

        val event = Event.Disconnect()
        assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(event))

        // move CONNECTING -> CONNECTED
        connectDelegate.success()

        // the Disconnect is processed from the shelf...
        // CONNECTED -> DISCONNECTING
        assertWithTimeout {
            while (fsm.fsm.state != State.Disconnecting) {
                sleep(1)
            }
        }
    }

    @Test
    fun `External transition via ConnectFailure`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        val event = Event.Connect()
        assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(event))

        val connectEvents = MutableList(5) { Event.Connect() }.apply {
            forEach { fsm.fsm.fireEvent(it) }
        }
        val getChannelEvents = MutableList(5) { Event.GetChannel() }.apply {
            forEach { fsm.fsm.fireEvent(it) }
        }

        connectDelegate.failure()

        assertWithTimeout {
            assertThrows(ExecutionException::class.java) {
                event.channelFuture.get()
            }
            connectEvents.forEach {
                assertThrows(ExecutionException::class.java) {
                    it.channelFuture.get()
                }
            }
            getChannelEvents.forEach {
                assertThrows(ExecutionException::class.java) {
                    it.channelFuture.get()
                }
            }
        }
    }

}
