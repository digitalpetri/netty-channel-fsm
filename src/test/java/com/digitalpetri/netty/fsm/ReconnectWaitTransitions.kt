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


class ReconnectWaitTransitions {

    @Test
    fun `S(RECONNECT_WAIT) x E(ReconnectDelayElapsed) = S'(RECONNECTING)`() {
        val fsm = factory().newChannelFsm(State.ReconnectWait)
        val event = Event.ReconnectDelayElapsed()

        assertEquals(State.Reconnecting, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(RECONNECT_WAIT) x E(Disconnect) = S'(NOT_CONNECTED)`() {
        val fsm = factory().newChannelFsm(State.ReconnectWait)
        val event = Event.Disconnect()

        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(event))
    }

}
