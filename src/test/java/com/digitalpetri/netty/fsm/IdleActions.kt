package com.digitalpetri.netty.fsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class IdleActions {

    @Test
    fun `Transition from IDLE to NOT_CONNECTED via Disconnect completes Disconnect future`() {
        val fsm = factory().newChannelFsm(State.Idle)

        val disconnect = Event.Disconnect()
        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(disconnect))

        assertWithTimeout {
            disconnect.disconnectFuture.get()
        }

    }

}
