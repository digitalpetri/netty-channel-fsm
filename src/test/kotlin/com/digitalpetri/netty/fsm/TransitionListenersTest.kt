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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class TransitionListenersTest {

    @Test
    fun `TransitionListeners receive callbacks`() {
        val connectDelegate = TestConnectProxy()

        val fsm: ChannelFsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        val listenerCalled = AtomicBoolean(false)

        fsm.addTransitionListener { from, to, via ->
            assertEquals(State.NotConnected, from)
            assertEquals(State.Connecting, to)
            assertTrue(via is Event.Connect)

            listenerCalled.set(true)
        }

        val event = Event.Connect()
        assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(event))

        assertTrue(listenerCalled.get())
    }

}
