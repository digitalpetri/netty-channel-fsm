package com.digitalpetri.netty.fsm;

public enum State {
    Connecting,
    Connected,
    Disconnecting,
    Idle,
    NotConnected,
    ReconnectWait,
    Reconnecting
}
