package com.digitalpetri.netty.fsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


class NotConnectedActions {

    @Test
    fun `Internal transition via Disconnect completes successfully`() {
        val fsm = factory().newChannelFsm()

        val event = Event.Disconnect()

        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(event))

        assertTrue(event.disconnectFuture.isDone && !event.disconnectFuture.isCompletedExceptionally)
    }

    @Test
    fun `Internal transition via GetChannel completes exceptionally`() {
        val fsm = factory().newChannelFsm()

        val event = Event.GetChannel()

        assertEquals(State.NotConnected, fsm.fsm.fireEventBlocking(event))
        
        assertTrue(event.channelFuture.isCompletedExceptionally)
    }

}
