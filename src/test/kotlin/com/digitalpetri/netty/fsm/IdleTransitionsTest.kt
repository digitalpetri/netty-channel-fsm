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


class IdleTransitionsTest {

    @Test
    fun `S(IDLE) x E(Connect) = S'(RECONNECTING)`() {
        val fsm = factory().newChannelFsm(State.Idle)
        val event = Event.Connect()

        assertEquals(State.Reconnecting, fsm.fsm.fireEventBlocking(event)) {
            "expected State.Reconnecting"
        }
    }

    @Test
    fun `S(IDLE) x E(GetChannel) = S'(RECONNECTING)`() {
        val fsm = factory().newChannelFsm(State.Idle)
        val event = Event.GetChannel()

        assertEquals(State.Reconnecting, fsm.fsm.fireEventBlocking(event)) {
            "expected State.Reconnecting"
        }
    }

    @Test
    fun `S(IDLE) x E(Disconnect) = S'(NOT_CONNECTED)`() {
        val fsm = factory().newChannelFsm(State.Idle)
        val event = Event.Disconnect()

        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(event)) {
            "expected State.NOT_CONNECTED"
        }
    }

}
