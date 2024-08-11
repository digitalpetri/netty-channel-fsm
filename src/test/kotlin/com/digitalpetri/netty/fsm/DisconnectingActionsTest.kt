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

import com.digitalpetri.netty.fsm.ChannelFsm.KEY_DF
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ExecutionException


class DisconnectingActionsTest {

    @Test
    fun `External transition to DISCONNECTING from CONNECTED via Disconnect`() {
        val connectDelegate = TestConnectProxy()
        val disconnectDelegate = TestDisconnectProxy()

        val factory: ChannelFsmFactory = factory(
            connectProxy = connectDelegate,
            disconnectProxy = disconnectDelegate
        )

        val fsm: ChannelFsm = factory.newChannelFsm(State.NotConnected)

        moveToConnected(fsm, connectDelegate)

        val disconnect = Event.Disconnect()
        fsm.fsm.fireEventBlocking(disconnect)

        assertNotNull {
            fsm.fsm.getFromContext { ctx -> ctx[KEY_DF] }
        }

        disconnectDelegate.success()

        assertWithTimeout {
            disconnect.disconnectFuture.get()
            assertTrue(disconnect.disconnectFuture.isDone)
            assertFalse(disconnect.disconnectFuture.isCompletedExceptionally)
        }
    }

    @Test
    fun `Internal transition via Connect`() {
        val disconnectDelegate = TestDisconnectProxy()

        val factory: ChannelFsmFactory = factory(
            disconnectProxy = disconnectDelegate
        )

        val fsm: ChannelFsm = factory.newChannelFsm(State.NotConnected)

        Event.Connect().apply {
            fsm.fsm.fireEventBlocking(this)
            assertWithTimeout { this.channelFuture.get() }
        }

        val disconnect = Event.Disconnect()
        assertEquals(State.Disconnecting, fsm.fsm.fireEventBlocking(disconnect))

        val connect = Event.Connect()
        assertEquals(State.Disconnecting, fsm.fsm.fireEventBlocking(connect))

        disconnectDelegate.success()

        assertWithTimeout {
            disconnect.disconnectFuture.get()
            assertTrue(disconnect.disconnectFuture.isDone)
            assertFalse(disconnect.disconnectFuture.isCompletedExceptionally)

            assertNotNull(connect.channelFuture.get())
        }
    }

    @Test
    fun `Internal transition via GetChannel`() {
        val disconnectDelegate = TestDisconnectProxy()

        val factory: ChannelFsmFactory = factory(
            disconnectProxy = disconnectDelegate
        )

        val fsm: ChannelFsm = factory.newChannelFsm(State.NotConnected)

        Event.Connect().apply {
            fsm.fsm.fireEventBlocking(this)
            assertWithTimeout { this.channelFuture.get() }
        }

        val disconnect = Event.Disconnect()
        assertEquals(State.Disconnecting, fsm.fsm.fireEventBlocking(disconnect))

        val getChannel = Event.GetChannel()
        assertEquals(State.Disconnecting, fsm.fsm.fireEventBlocking(getChannel))

        disconnectDelegate.success()

        assertWithTimeout {
            disconnect.disconnectFuture.get()
            assertTrue(disconnect.disconnectFuture.isDone)
            assertFalse(disconnect.disconnectFuture.isCompletedExceptionally)

            assertThrows<ExecutionException> { getChannel.channelFuture.get() }
        }
    }

    @Test
    fun `Internal transition via Disconnect`() {
        val disconnectDelegate = TestDisconnectProxy()

        val factory: ChannelFsmFactory = factory(
            disconnectProxy = disconnectDelegate
        )

        val fsm: ChannelFsm = factory.newChannelFsm(State.NotConnected)

        Event.Connect().apply {
            fsm.fsm.fireEventBlocking(this)
            assertWithTimeout { this.channelFuture.get() }
        }

        val disconnect = Event.Disconnect()
        assertEquals(State.Disconnecting, fsm.fsm.fireEventBlocking(disconnect))

        val disconnects: List<Event.Disconnect> = List(5) {
            Event.Disconnect()
        }.onEach {
            assertEquals(State.Disconnecting, fsm.fsm.fireEventBlocking(it))
        }

        disconnectDelegate.success()

        assertWithTimeout {
            disconnect.disconnectFuture.get()
            assertTrue(disconnect.disconnectFuture.isDone)
            assertFalse(disconnect.disconnectFuture.isCompletedExceptionally)

            disconnects.forEach {
                it.disconnectFuture.get()
                assertTrue(it.disconnectFuture.isDone)
                assertFalse(it.disconnectFuture.isCompletedExceptionally)
            }
        }
    }

    private fun moveToConnected(
        fsm: ChannelFsm,
        connectDelegate: TestConnectProxy
    ) {
        val connect = Event.Connect()
        fsm.fsm.fireEventBlocking(connect)
        connectDelegate.success()
        assertEventualState(fsm, State.Connected)
        connectDelegate.reset()
        assertWithTimeout {
            assertNotNull(connect.channelFuture.get())
        }
    }

}
