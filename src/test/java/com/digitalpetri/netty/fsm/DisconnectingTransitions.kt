package com.digitalpetri.netty.fsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class DisconnectingTransitions {

    @Test
    fun `S(DISCONNECTING) x E(DisconnectSuccess) = S'(NOT_CONNECTED)`() {
        val fsm = factory().newChannelFsm(State.Disconnecting)
        val event = Event.DisconnectSuccess()

        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(event))
    }

}
