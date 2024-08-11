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

import com.digitalpetri.netty.fsm.ChannelFsm.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ExecutionException


class ReconnectWaitActionsTest {

    @Test
    fun `External transition to RECONNECT_WAIT from RECONNECTING via ConnectFailure notifies ConnectFuture`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()
        assertEventualState(fsm, State.Connected)
        connectDelegate.reset()

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.Reconnecting)

        val getChannel = Event.GetChannel()
        assertEquals(State.Reconnecting, fsm.fsm.fireEventBlocking(getChannel))

        connectDelegate.failure()

        assertWithTimeout {
            assertThrows<ExecutionException> {
                getChannel.channelFuture.get()
            }
        }
    }

    @Test
    fun `External transition to RECONNECT_WAIT sets a new ConnectFuture`() {
        val connectDelegate = TestConnectProxy()
        val scheduler = TestScheduler()

        val factory: ChannelFsmFactory = factory(
            connectProxy = connectDelegate,
            scheduler = scheduler
        )
        val fsm = factory.newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.ReconnectWait)

        fsm.fsm.getFromContext { ctx ->
            val cf = KEY_CF.get(ctx)
            assertNotNull(cf)
            assertFalse(cf.future.isDone)
        }
    }

    @Test
    fun `External transition to RECONNECT_WAIT sets reconnect delay and future`() {
        val connectDelegate = TestConnectProxy()
        val scheduler = TestScheduler()

        val factory: ChannelFsmFactory = factory(
            connectProxy = connectDelegate,
            scheduler = scheduler
        )
        val fsm = factory.newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.ReconnectWait)

        fsm.fsm.getFromContext { ctx ->
            val rd = KEY_RD.get(ctx)
            assertEquals(1L, rd)

            assertNotNull(KEY_RDF.get(ctx))
        }
    }

    @Test
    fun `Subsequent transitions to RECONNECT_WAIT due to failure increments reconnect delay`() {
        val connectDelegate = TestConnectProxy()
        val scheduler = TestScheduler()

        val factory: ChannelFsmFactory = factory(
            connectProxy = connectDelegate,
            scheduler = scheduler
        )
        val fsm = factory.newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()

        assertEventualState(fsm, State.Connected)
        connectDelegate.reset()

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.ReconnectWait)

        for (expectedDelay in listOf<Long>(2, 4, 8, 16, 32, 32, 32)) {
            scheduler.execute()
            assertEventualState(fsm, State.Reconnecting)
            connectDelegate.failure()
            assertEventualState(fsm, State.ReconnectWait)
            connectDelegate.reset()

            fsm.fsm.getFromContext { ctx ->
                assertEquals(expectedDelay, KEY_RD.get(ctx))
            }
        }
    }

    @Test
    fun `Internal transition via Connect event is handled`() {
        val connectDelegate = TestConnectProxy()
        val scheduler = TestScheduler()

        val factory: ChannelFsmFactory = factory(
            connectProxy = connectDelegate,
            scheduler = scheduler
        )
        val fsm = factory.newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        connectDelegate.reset()

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.ReconnectWait)


        run {
            val connect = Event.Connect()
            assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(connect))

            scheduler.execute()
            assertEventualState(fsm, State.Reconnecting)
            connectDelegate.failure()
            assertEventualState(fsm, State.ReconnectWait)
            connectDelegate.reset()

            assertThrows<ExecutionException> {
                connect.channelFuture.get()
            }
        }

        run {
            val connect = Event.Connect()
            assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(connect))

            scheduler.execute()
            assertEventualState(fsm, State.Reconnecting)
            connectDelegate.success()
            assertEventualState(fsm, State.Connected)
            connectDelegate.reset()

            assertWithTimeout {
                assertNotNull(connect.channelFuture.get())
            }
        }
    }

    @Test
    fun `Internal transition via GetChannel event is handled`() {
        val connectDelegate = TestConnectProxy()
        val scheduler = TestScheduler()

        val factory: ChannelFsmFactory = factory(
            connectProxy = connectDelegate,
            scheduler = scheduler
        )
        val fsm = factory.newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()
        assertEventualState(fsm, State.Connected)
        connectDelegate.reset()

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.ReconnectWait)

        assertWithTimeout {
            val getChannel = Event.GetChannel(false)
            fsm.fsm.fireEvent(getChannel)

            assertThrows(Exception::class.java) {
                getChannel.channelFuture.get()
            }
        }

        run {
            val getChannel = Event.GetChannel()
            assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(getChannel))

            scheduler.execute()
            assertEventualState(fsm, State.Reconnecting)
            connectDelegate.failure()
            assertEventualState(fsm, State.ReconnectWait)
            connectDelegate.reset()

            assertThrows<ExecutionException> {
                getChannel.channelFuture.get()
            }
        }

        run {
            val getChannel = Event.GetChannel()
            assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(getChannel))

            scheduler.execute()
            assertEventualState(fsm, State.Reconnecting)
            connectDelegate.success()
            assertEventualState(fsm, State.Connected)
            connectDelegate.reset()

            assertWithTimeout {
                assertNotNull(getChannel.channelFuture.get())
            }
        }
    }

    @Test
    fun `Transition from RECONNECT_WAIT to NOT_CONNECTED via Disconnect`() {
        val connectDelegate = TestConnectProxy()
        val scheduler = TestScheduler()

        val factory: ChannelFsmFactory = factory(
            connectProxy = connectDelegate,
            scheduler = scheduler
        )
        val fsm = factory.newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.ReconnectWait)

        val getChannel = Event.GetChannel()
        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(getChannel))

        val disconnect = Event.Disconnect()
        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(disconnect))

        assertThrows<ExecutionException> {
            getChannel.channelFuture.get()
        }

        assertWithTimeout {
            disconnect.disconnectFuture.get()
        }

        fsm.fsm.getFromContext { ctx ->
            assertNull(KEY_RD.get(ctx))
            assertNull(KEY_RDF.get(ctx))
        }
    }

}
