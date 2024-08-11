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


class IdleActionsTest {

    @Test
    fun `Transition from IDLE to NOT_CONNECTED via Disconnect completes Disconnect future`() {
        val fsm = factory().newChannelFsm(State.Idle)

        val disconnect = Event.Disconnect()
        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(disconnect))

        assertWithTimeout {
            disconnect.disconnectFuture.get()
        }
    }

}
