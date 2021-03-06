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


class ReconnectingTransitions {

    @Test
    fun `S(RECONNECTING) x E(ConnectFailure) = S'(RECONNECT_WAIT)`() {
        val fsm = factory().newChannelFsm(State.Reconnecting)
        val event = Event.ConnectFailure(Exception("failed"))

        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(RECONNECTING) x E(ConnectSuccess) = S'(CONNECTED)`() {
        val fsm = factory().newChannelFsm(State.Reconnecting)
        val event = Event.ConnectSuccess(EmbeddedChannel())

        assertEquals(State.Connected, fsm.fsm.fireEventBlocking(event))
    }

}
