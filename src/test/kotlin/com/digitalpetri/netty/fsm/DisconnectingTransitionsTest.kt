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


class DisconnectingTransitionsTest {

    @Test
    fun `S(DISCONNECTING) x E(DisconnectSuccess) = S'(NOT_CONNECTED)`() {
        val fsm = factory().newChannelFsm(State.Disconnecting)
        val event = Event.DisconnectSuccess()

        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(event))
    }

}
