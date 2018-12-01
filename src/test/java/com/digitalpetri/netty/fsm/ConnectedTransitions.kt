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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class ConnectedTransitions {

    @Test
    fun `S(CONNECTED) x E(Disconnect) = S'(DISCONNECTING)`() {
        val fsm = factory().newChannelFsm(State.Connected)
        val event = Event.Disconnect()

        assertEquals(State.Disconnecting, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTED) x E(ChannelInactive) = S'(IDLE) lazy=true`() {
        val fsm = factory(lazy = true).newChannelFsm(State.Connected)
        val event = Event.ChannelInactive()

        assertEquals(State.Idle, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTED) x E(ChannelInactive) = S'(RECONNECT_WAIT) lazy=false`() {
        val fsm = factory().newChannelFsm(State.Connected)
        val event = Event.ChannelInactive()

        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTED) x E(KeepAliveFailure) = S'(IDLE) lazy=true`() {
        val fsm = factory(lazy = true).newChannelFsm(State.Connected)
        val event = Event.KeepAliveFailure(Throwable("failure"))

        assertEquals(State.Idle, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTED) x E(KeepAliveFailure) = S'(RECONNECT_WAIT) lazy=false`() {
        val fsm = factory(lazy = false).newChannelFsm(State.Connected)
        val event = Event.KeepAliveFailure(Throwable("failure"))

        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(event))
    }

}
