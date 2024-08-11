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
import io.netty.channel.Channel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CountDownLatch


class ConnectedActionsTest {

    @Test
    fun `External transition to CONNECTED via ConnectSuccess`() {
        val connectDelegate = TestConnectProxy()

        val fsm: ChannelFsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        val event = Event.Connect()

        assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(event))

        connectDelegate.success()

        assertWithTimeout {
            assertNotNull(event.channelFuture.get())
        }
    }

    @Test
    fun `Internal transition to CONNECTED via Connect`() {
        val fsm: ChannelFsm = factory()
            .newChannelFsm(State.NotConnected)

        val event = Event.Connect()
        fsm.fsm.fireEventBlocking(event)

        assertWithTimeout {
            assertNotNull(event.channelFuture.get())
        }

        assertEquals(State.Connected, fsm.fsm.state)

        val connect = Event.Connect()
        fsm.fsm.fireEventBlocking(connect)
        assertWithTimeout {
            assertNotNull(connect.channelFuture.get())
        }
    }

    @Test
    fun `Internal transition to CONNECTED via GetChannel`() {
        val fsm: ChannelFsm = factory()
            .newChannelFsm(State.NotConnected)

        val event = Event.Connect()
        fsm.fsm.fireEventBlocking(event)

        assertWithTimeout {
            assertNotNull(event.channelFuture.get())
        }

        assertEquals(State.Connected, fsm.fsm.state)

        val getChannel = Event.GetChannel()
        fsm.fsm.fireEventBlocking(getChannel)
        assertWithTimeout {
            assertNotNull(getChannel.channelFuture.get())
        }
    }

    @Test
    fun `Internal transition to CONNECTED via ChannelIdle triggers KeepAliveProxy`() {
        val keepAliveLatch = CountDownLatch(1)

        val keepAliveProxy = object : KeepAliveProxy {
            override fun keepAlive(ctx: FsmContext<State, Event>, channel: Channel): CompletableFuture<Void> {
                keepAliveLatch.countDown()

                return completedFuture(null)
            }
        }

        val fsm: ChannelFsm = factory(keepAliveProxy = keepAliveProxy)
            .newChannelFsm(State.NotConnected)

        val event = Event.Connect()
        fsm.fsm.fireEventBlocking(event)

        assertEventualState(fsm, State.Connected)

        val channelIdle = Event.ChannelIdle()
        fsm.fsm.fireEventBlocking(channelIdle)

        assertWithTimeout {
            keepAliveLatch.await()
        }
    }

    @Test
    fun `Transition from CONNECTED to IDLE via KeepAliveFailure closes channel`() {
        val factory: ChannelFsmFactory = factory(lazy = true)
        val fsm: ChannelFsm = factory.newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        assertEventualState(fsm, State.Connected)

        val channel = fsm.channel.get()

        assertTrue { channel.isOpen }

        fsm.fsm.fireEventBlocking(Event.KeepAliveFailure(Throwable("failure")))
        assertEventualState(fsm, State.Idle)

        assertFalse { channel.isOpen }
    }

    @Test
    fun `Transition from CONNECTED to RECONNECT_WAIT via KeepAliveFailure closes channel`() {
        val factory: ChannelFsmFactory = factory(lazy = false)
        val fsm: ChannelFsm = factory.newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        assertEventualState(fsm, State.Connected)

        val channel = fsm.channel.get()

        assertTrue { channel.isOpen }

        assertEquals(
            State.ReconnectWait,
            fsm.fsm.fireEventBlocking(Event.KeepAliveFailure(Throwable("failure")))
        )

        assertFalse { channel.isOpen }
    }

}
