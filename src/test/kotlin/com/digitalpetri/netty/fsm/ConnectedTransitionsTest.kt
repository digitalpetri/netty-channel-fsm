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


class ConnectedTransitionsTest {

    @Test
    fun `S(CONNECTED) x E(Disconnect) = S'(DISCONNECTING)`() {
        val fsm = factory().newChannelFsm(State.Connected)
        val event = Event.Disconnect()

        assertEquals(State.Disconnecting, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTED) x E(ChannelInactive) = S'(IDLE) lazy=true`() {
        val fsm = factory(lazy = true).newChannelFsm(State.Connected)
        val event = Event.ChannelInactive()

        assertEquals(State.Idle, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTED) x E(ChannelInactive) = S'(RECONNECT_WAIT) lazy=false`() {
        val fsm = factory().newChannelFsm(State.Connected)
        val event = Event.ChannelInactive()

        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTED) x E(KeepAliveFailure) = S'(IDLE) lazy=true`() {
        val fsm = factory(lazy = true).newChannelFsm(State.Connected)
        val event = Event.KeepAliveFailure(Throwable("failure"))

        assertEquals(State.Idle, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTED) x E(KeepAliveFailure) = S'(RECONNECT_WAIT) lazy=false`() {
        val fsm = factory(lazy = false).newChannelFsm(State.Connected)
        val event = Event.KeepAliveFailure(Throwable("failure"))

        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(event))
    }

}
