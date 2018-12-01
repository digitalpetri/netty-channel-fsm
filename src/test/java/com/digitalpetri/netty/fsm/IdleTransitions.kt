package com.digitalpetri.netty.fsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class IdleTransitions {

    @Test
    fun `S(IDLE) x E(Connect) = S'(CONNECTING)`() {
        val fsm = factory().newChannelFsm(State.Idle)
        val event = Event.Connect()

        assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(event)) {
            "expected State.CONNECTING"
        }
    }

    @Test
    fun `S(IDLE) x E(GetChannel) = S'(CONNECTING)`() {
        val fsm = factory().newChannelFsm(State.Idle)
        val event = Event.GetChannel()

        assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(event)) {
            "expected State.CONNECTING"
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
