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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ExecutionException


class NotConnectedActions {

    @Test
    fun `Internal transition via Disconnect completes successfully`() {
        val fsm = factory().newChannelFsm()

        val event = Event.Disconnect()

        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(event))

        assertWithTimeout { event.disconnectFuture.get() }
    }

    @Test
    fun `Internal transition via GetChannel completes exceptionally`() {
        val fsm = factory().newChannelFsm()

        val event = Event.GetChannel()

        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(event))

        assertThrows<ExecutionException> { event.channelFuture.get() }
    }

}
