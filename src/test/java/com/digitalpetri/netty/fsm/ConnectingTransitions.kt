package com.digitalpetri.netty.fsm

import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class ConnectingTransitions {

    @Test
    fun `S(CONNECTING) x E(ConnectFailure) = S'(IDLE) (persistent=true, lazy=true)`() {
        val fsm = factory(persistent = true, lazy = true).newChannelFsm(State.Connecting)
        val event = Event.ConnectFailure(Throwable("failed"))

        assertEquals(State.Idle, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTING) x E(ConnectFailure) = S'(RECONNECT_WAIT) (persistent=true, lazy=false)`() {
        val fsm = factory(persistent = true, lazy = false).newChannelFsm(State.Connecting)
        val event = Event.ConnectFailure(Throwable("failed"))

        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTING) x E(ConnectFailure) = S'(NOT_CONNECTED) (persistent=false)`() {
        val fsm = factory(persistent = false).newChannelFsm(State.Connecting)
        val event = Event.ConnectFailure(Throwable("failed"))

        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(CONNECTING) x E(ConnectSuccess) = S'(CONNECTED)`() {
        val fsm = factory().newChannelFsm(State.Connecting)
        val event = Event.ConnectSuccess(EmbeddedChannel())

        assertEquals(State.Connected, fsm.fsm.fireEventBlocking(event))
    }

}
