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


class NotConnectedTransitionsTest {

    @Test
    fun `S(NOT_CONNECTED) x E(Connect) = S'(CONNECTING)`() {
        val fsm = factory().newChannelFsm()

        val event = Event.Connect()

        assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(event)) {
            "expected State.CONNECTING"
        }
    }

}



