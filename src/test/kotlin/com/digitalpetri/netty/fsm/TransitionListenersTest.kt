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
