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

import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class ConnectingTransitionsTest {

    @Test
    fun `S(CONNECTING) x E(ConnectFailure) = S'(IDLE) (persistent=true, lazy=true)`() {
        val fsm = factory(persistent = true, lazy = true).newChannelFsm(State.Connecting)
        val event = Event.ConnectFailure(Throwable("failed"))

        assertEquals(State.Idle, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTING) x E(ConnectFailure) = S'(RECONNECT_WAIT) (persistent=true, lazy=false)`() {
        val fsm = factory(persistent = true, lazy = false).newChannelFsm(State.Connecting)
        val event = Event.ConnectFailure(Throwable("failed"))

        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTING) x E(ConnectFailure) = S'(NOT_CONNECTED) (persistent=false)`() {
        val fsm = factory(persistent = false).newChannelFsm(State.Connecting)
        val event = Event.ConnectFailure(Throwable("failed"))

        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTING) x E(ConnectSuccess) = S'(CONNECTED)`() {
        val fsm = factory().newChannelFsm(State.Connecting)
        val event = Event.ConnectSuccess(EmbeddedChannel())

        assertEquals(State.Connected, fsm.fsm.fireEventBlocking(event))
    }

}
