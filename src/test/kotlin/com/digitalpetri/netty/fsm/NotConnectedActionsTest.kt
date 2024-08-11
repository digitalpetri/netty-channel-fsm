/*
 * Copyright (c) 2024 Kevin Herron
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.digitalpetri.netty.fsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ExecutionException


class NotConnectedActionsTest {

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
