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


class ReconnectWaitTransitionsTest {

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
