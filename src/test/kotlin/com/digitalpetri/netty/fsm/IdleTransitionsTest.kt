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
