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

import com.digitalpetri.netty.fsm.ChannelFsm.KEY_RD
import com.digitalpetri.netty.fsm.ChannelFsm.KEY_RDF
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


class ReconnectingActions {

    @Test
    fun `Transition from RECONNECT_WAIT triggers connect()`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()
        assertEventualState(fsm, State.Connected)
        connectDelegate.reset()

        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(Event.ChannelInactive()))

        assertEventualState(fsm, State.Reconnecting)

        connectDelegate.success()
        assertEventualState(fsm, State.Connected)
        connectDelegate.reset()
    }

    @Test
    fun `Internal transition via Connect`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()
        assertEventualState(fsm, State.Connected)
        connectDelegate.reset()

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.Reconnecting)

        assertWithTimeout {
            val connect = Event.Connect()
            fsm.fsm.fireEvent(connect)
            connectDelegate.success()

            assertNotNull(connect.channelFuture.get())
        }
    }

    @Test
    fun `Internal transition via GetChannel`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()
        assertEventualState(fsm, State.Connected)
        connectDelegate.reset()

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.Reconnecting)

        assertWithTimeout {
            val getChannel = Event.GetChannel()
            fsm.fsm.fireEvent(getChannel)
            connectDelegate.success()

            assertNotNull(getChannel.channelFuture.get())
        }
    }

    @Test
    fun `Disconnect is shelved while RECONNECTING and un-shelved on external transition from RECONNECTING`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()
        assertEventualState(fsm, State.Connected)
        connectDelegate.reset()

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.Reconnecting)

        val disconnect = Event.Disconnect()
        assertEquals(State.Reconnecting, fsm.fsm.fireEventBlocking(disconnect))

        connectDelegate.success()

        // after going back to CONNECTED the shelved Disconnect gets processed...

        assertWithTimeout {
            disconnect.disconnectFuture.get()

            assertTrue {
                disconnect.disconnectFuture.isDone &&
                    !disconnect.disconnectFuture.isCompletedExceptionally
            }
        }
    }

    @Test
    fun `Transition from RECONNECTING to CONNECTED via ConnectSuccess clears reconnect state`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()
        assertEventualState(fsm, State.Connected)
        connectDelegate.reset()

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.Reconnecting)

        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        fsm.fsm.getFromContext { ctx ->
            assertNull(KEY_RD.get(ctx))
            assertNull(KEY_RDF.get(ctx))
        }
    }

}
