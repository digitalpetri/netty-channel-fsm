package com.digitalpetri.netty.fsm

import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class ReconnectingTransitions {

    @Test
    fun `S(RECONNECTING) x E(ConnectFailure) = S'(RECONNECT_WAIT)`() {
        val fsm = factory().newChannelFsm(State.Reconnecting)
        val event = Event.ConnectFailure(Exception("failed"))

        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(event))
    }

    @Test
    fun `S(RECONNECTING) x E(ConnectSuccess) = S'(CONNECTED)`() {
        val fsm = factory().newChannelFsm(State.Reconnecting)
        val event = Event.ConnectSuccess(EmbeddedChannel())

        assertEquals(State.Connected, fsm.fsm.fireEventBlocking(event))
    }

}
