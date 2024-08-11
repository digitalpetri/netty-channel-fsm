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

import com.digitalpetri.fsm.FsmContext
import io.netty.channel.Channel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CountDownLatch


class ConnectedActions {

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
