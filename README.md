A `ChannelFsm` manages non-blocking access to a Netty `Channel` instance. 

Access to the Channel happens via `connect()`, `disconnect()`, and `getChannel()`. 

Once connected, the state machine works to keep the Channel connected until `disconnect()` is called. 

### Persistence
When the `persistent` setting is `true`, if the initial `connect()` fails it will move into a reconnecting state rather than back to a not connected state.

### Lazyness
When the `lazy` settings is `true`, a connection loss moves the state machine into an idle state, waiting to reconnect until the next `connect()` or `getChannel()` call requests a Channel.
