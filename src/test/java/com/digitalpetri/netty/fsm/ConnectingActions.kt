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

import com.digitalpetri.netty.fsm.ChannelFsm.KEY_CF
import com.digitalpetri.strictmachine.FsmContext
import io.netty.channel.Channel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException


class ConnectingActions {

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
