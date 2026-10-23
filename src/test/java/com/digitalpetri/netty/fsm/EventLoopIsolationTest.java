/*
 * Copyright (c) 2026 Kevin Herron
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.digitalpetri.netty.fsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.fsm.FsmContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Netty event loop threads must never wait for the FSM. An FSM evaluation holds the FSM's lock
 * while its actions and TransitionListeners run, and an event loop that waits for that lock stalls
 * every channel registered with it.
 *
 * <p>These tests use real LocalChannels, because the waits only happen on a real event loop.
 */
class EventLoopIsolationTest {

  private DefaultEventLoopGroup group;
  private ExecutorService executor;
  private LocalAddress address;
  private Channel serverChannel;

  @BeforeEach
  void startServer() throws InterruptedException {
    // Daemon threads, so a test that fails by deadlocking can't keep the JVM alive.
    group = new DefaultEventLoopGroup(1, new DefaultThreadFactory("event-loop", true));
    executor = Executors.newSingleThreadExecutor(new DefaultThreadFactory("fsm-executor", true));
    address = new LocalAddress("EventLoopIsolationTest-" + System.nanoTime());
    serverChannel = new ServerBootstrap()
        .group(group)
        .channel(LocalServerChannel.class)
        .childHandler(new ChannelInboundHandlerAdapter())
        .bind(address)
        .sync()
        .channel();
  }

  @AfterEach
  void stopServer() {
    serverChannel.close();
    group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
    executor.shutdownNow();
  }

  // Netty notifies closeFuture listeners before it completes the close promise. A listener that
  // waits for the FSM's lock deadlocks with an FSM lock holder that waits for the close.
  @Test
  @DisplayName("A channel close completes while the FSM lock is held")
  void closeCompletesWhileFsmLockIsHeld() throws Exception {
    ChannelFsm fsm = newFsm();
    Channel channel = fsm.connect().get(5, TimeUnit.SECONDS);

    var closed = new AtomicBoolean();
    fsm.getFsm().withContext(ctx ->
        closed.set(channel.close().awaitUninterruptibly(1, TimeUnit.SECONDS))
    );

    assertTrue(closed.get(), "the close must complete while the FSM lock is held");
  }

  @Test
  @DisplayName("A slow TransitionListener does not block the event loop when the channel closes")
  void slowListenerDoesNotBlockEventLoopOnClose() throws Exception {
    ChannelFsm fsm = newFsm();
    Channel channel = fsm.connect().get(5, TimeUnit.SECONDS);

    var listenerRunning = new CountDownLatch(1);
    var releaseListener = new CountDownLatch(1);
    fsm.addTransitionListener((from, to, via) -> {
      if (from == State.Connected && via instanceof Event.Connect) {
        listenerRunning.countDown();
        try {
          releaseListener.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });

    try {
      fsm.connect();
      assertTrue(listenerRunning.await(5, TimeUnit.SECONDS));

      channel.close();
      boolean eventLoopRan = channel.eventLoop()
          .submit(() -> { })
          .await(1, TimeUnit.SECONDS);

      assertTrue(eventLoopRan, "the event loop must not wait for the FSM");
    } finally {
      releaseListener.countDown();
    }

    FsmTestSupport.assertEventualState(fsm, State.ReconnectWait);
  }

  private ChannelFsm newFsm() {
    var actions = new ChannelActions() {
      @Override
      public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
        var future = new CompletableFuture<Channel>();

        new Bootstrap()
            .group(group)
            .channel(LocalChannel.class)
            .handler(new ChannelInboundHandlerAdapter())
            .connect(address)
            .addListener((ChannelFutureListener) f -> {
              if (f.isSuccess()) {
                future.complete(f.channel());
              } else {
                future.completeExceptionally(f.cause());
              }
            });

        return future;
      }

      @Override
      public CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel) {
        var future = new CompletableFuture<Void>();
        channel.close().addListener((ChannelFuture f) -> future.complete(null));
        return future;
      }
    };

    ChannelFsmConfig config = ChannelFsmConfig.newBuilder()
        .setExecutor(executor)
        .setScheduler(new ManualScheduler())
        .setChannelActions(actions)
        .setLazy(false)
        .setPersistent(true)
        .setMaxIdleSeconds(0)
        .build();

    ChannelFsm fsm = new ChannelFsmFactory(config).newChannelFsm();
    assertEquals(State.NotConnected, fsm.getState());
    return fsm;
  }

}
