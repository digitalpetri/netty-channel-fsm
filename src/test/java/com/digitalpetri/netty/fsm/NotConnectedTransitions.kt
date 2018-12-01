package com.digitalpetri.netty.fsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class NotConnectedTransitions {

    @Test
    fun `S(NOT_CONNECTED) x E(Connect) = S'(CONNECTING)`() {
        val fsm = factory().newChannelFsm()

        val event = Event.Connect()

        assertEquals(State.Connecting, fsm.fsm.fireEventBlocking(event)) {
            "expected State.CONNECTING"
        }
    }

}



