package com.digitalpetri.netty.fsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class ReconnectWaitTransitions {

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
