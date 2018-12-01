package com.digitalpetri.netty.fsm

import com.digitalpetri.netty.fsm.ChannelFsm.KEY_RD
import com.digitalpetri.netty.fsm.ChannelFsm.KEY_RDF
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


class ReconnectingActions {

    @Test
    fun `Transition from RECONNECT_WAIT triggers connect()`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        assertEquals(State.ReconnectWait, fsm.fsm.fireEventBlocking(Event.ChannelInactive()))

        assertEventualState(fsm, State.Reconnecting)

        connectDelegate.success()

        assertEventualState(fsm, State.Connected)
    }

    @Test
    fun `Internal transition via Connect`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.Reconnecting)

        assertWithTimeout {
            val connect = Event.Connect()
            fsm.fsm.fireEvent(connect)
            connectDelegate.success()

            assertNotNull(connect.channelFuture.get())
        }
    }

    @Test
    fun `Internal transition via GetChannel`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.Reconnecting)

        assertWithTimeout {
            val getChannel = Event.GetChannel()
            fsm.fsm.fireEvent(getChannel)
            connectDelegate.success()

            assertNotNull(getChannel.channelFuture.get())
        }
    }

    @Test
    fun `Disconnect is shelved while RECONNECTING and un-shelved on external transition from RECONNECTING`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.Reconnecting)

        val disconnect = Event.Disconnect()
        assertEquals(State.Reconnecting, fsm.fsm.fireEventBlocking(disconnect))

        connectDelegate.success()

        // after going back to CONNECTED the shelved Disconnect gets processed...

        assertWithTimeout {
            disconnect.disconnectFuture.get()

            assertTrue {
                disconnect.disconnectFuture.isDone &&
                    !disconnect.disconnectFuture.isCompletedExceptionally
            }
        }
    }

    @Test
    fun `Transition from RECONNECTING to CONNECTED via ConnectSuccess clears reconnect state`() {
        val connectDelegate = TestConnectProxy()

        val fsm = factory(connectProxy = connectDelegate)
            .newChannelFsm(State.NotConnected)

        fsm.fsm.fireEventBlocking(Event.Connect())
        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        fsm.fsm.fireEvent(Event.ChannelInactive())

        assertEventualState(fsm, State.Reconnecting)

        connectDelegate.success()

        assertEventualState(fsm, State.Connected)

        fsm.fsm.getFromContext { ctx ->
            assertNull(KEY_RD.get(ctx))
            assertNull(KEY_RDF.get(ctx))
        }
    }

}
