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

import static com.digitalpetri.netty.fsm.FsmTestSupport.stepConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.fsm.FsmContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The FSM adds its handlers to a channel's pipeline when it enters Connected. That can fail, and
 * the pipeline has its own lock.
 */
class ConnectedPipelineTest {

  // Adding the FSM's handlers fails once the channel's event loop has shut down. The FSM must still
  // connect and publish the channel, since Disconnect waits for it.
  @Test
  @DisplayName("Disconnect completes when the channel's event loop rejects the FSM's handlers")
  void disconnectCompletesWhenHandlersAreRejected() throws Exception {
    var group = new DefaultEventLoopGroup(1);
    try {
      var channel = new LocalChannel();
      group.register(channel).sync();
      group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();

      // The application shut the event loop down after the connect attempt succeeded but before
      // the executor evaluated ConnectSuccess. The pipeline now rejects handlers.
      assertThrows(
          RejectedExecutionException.class,
          () -> channel.pipeline().addLast(new ChannelInboundHandlerAdapter())
      );

      var executor = new QueuedExecutor();
      var actions = new RecordingChannelActions();
      ChannelFsmConfig config = stepConfig(executor, new ManualScheduler(), actions)
          .setMaxIdleSeconds(15)
          .build();
      ChannelFsm fsm = new ChannelFsmFactory(config).newChannelFsm();

      final CompletableFuture<Channel> connected = fsm.connect();
      executor.drain();
      actions.lastConnect().complete(channel);
      executor.drain();
      assertEquals(State.Connected, fsm.getState());

      CompletableFuture<Void> disconnected = fsm.disconnect();
      executor.drain();

      assertTrue(connected.isDone(), "connect() must complete");
      assertTrue(disconnected.isDone(), "disconnect() must complete");
      assertEquals(State.NotConnected, fsm.getState());
      assertEquals(1, actions.disconnects.size());
    } finally {
      group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
    }
  }

  // DefaultChannelPipeline.destroy() calls handlerRemoved while it holds the pipeline's lock. A
  // handler that calls into the FSM there waits for the FSM's lock, so the FSM must not wait for
  // the pipeline's lock while it holds its own.
  //
  // The channel unregisters while the FSM adds its handlers when the peer closes right after the
  // handshake. The hook only widens that window: it holds the FSM at the point where it reads
  // maxIdleSeconds to add its handlers, until the event loop is inside destroy().
  @Test
  @DisplayName("Adding the FSM's handlers does not deadlock with a handler's handlerRemoved")
  void addingHandlersDoesNotDeadlockWithHandlerRemoved() throws Exception {
    var group = new DefaultEventLoopGroup(1, new DefaultThreadFactory("event-loop", true));
    ExecutorService executor =
        Executors.newSingleThreadExecutor(new DefaultThreadFactory("fsm-executor", true));

    try {
      var address = new LocalAddress("ConnectedPipelineTest-" + System.nanoTime());
      new ServerBootstrap()
          .group(group)
          .channel(LocalServerChannel.class)
          .childHandler(new ChannelInboundHandlerAdapter())
          .bind(address)
          .sync();

      var fsmRef = new AtomicReference<ChannelFsm>();
      var inDestroy = new CountDownLatch(1);
      var handlerRemovedReturned = new CountDownLatch(1);

      var consumerHandler = new ChannelInboundHandlerAdapter() {
        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
          inDestroy.countDown();
          fsmRef.get().getState();
          handlerRemovedReturned.countDown();
        }
      };

      var clientChannel = new AtomicReference<Channel>();
      var handshake = new CompletableFuture<Channel>();
      var actions = new ChannelActions() {
        @Override
        public CompletableFuture<Channel> connect(FsmContext<State, Event> ctx) {
          new Bootstrap()
              .group(group)
              .channel(LocalChannel.class)
              .handler(new ChannelInitializer<LocalChannel>() {
                @Override
                protected void initChannel(LocalChannel ch) {
                  ch.pipeline().addLast(consumerHandler);
                  clientChannel.set(ch);
                }
              })
              .connect(address);
          return handshake;
        }

        @Override
        public CompletableFuture<Void> disconnect(FsmContext<State, Event> ctx, Channel channel) {
          return CompletableFuture.completedFuture(null);
        }
      };

      var hookRunning = new CountDownLatch(1);
      Runnable hook = () -> {
        hookRunning.countDown();
        try {
          inDestroy.await(5, TimeUnit.SECONDS);
          // Let handlerRemoved reach fsm.getState().
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      };

      ChannelFsmConfig config = ChannelFsmConfig.newBuilder()
          .setExecutor(executor)
          .setScheduler(new ManualScheduler())
          .setChannelActions(actions)
          .setMaxIdleSeconds(15)
          .build();
      ChannelFsm fsm = new ChannelFsmFactory(new HookedConfig(config, hook)).newChannelFsm();
      fsmRef.set(fsm);

      fsm.connect();
      FsmTestSupport.assertWithTimeout(() -> {
        while (clientChannel.get() == null || !clientChannel.get().isActive()) {
          Thread.sleep(1);
        }
      });
      Channel channel = clientChannel.get();

      // Complete the handshake on another thread, the way an event loop would, so the hook can
      // hold whichever thread adds the FSM's handlers without holding the test.
      new Thread(() -> handshake.complete(channel), "handshake").start();
      assertTrue(hookRunning.await(5, TimeUnit.SECONDS));
      channel.close();

      assertTrue(
          handlerRemovedReturned.await(2, TimeUnit.SECONDS),
          "the event loop and the FSM executor deadlocked"
      );
      FsmTestSupport.assertEventualState(fsm, State.ReconnectWait);
    } finally {
      group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
      executor.shutdownNow();
    }
  }

  /**
   * A config that runs a hook the first time the FSM reads maxIdleSeconds.
   */
  private static final class HookedConfig implements ChannelFsmConfig {

    private final AtomicBoolean armed = new AtomicBoolean(true);

    private final ChannelFsmConfig delegate;
    private final Runnable hook;

    HookedConfig(ChannelFsmConfig delegate, Runnable hook) {
      this.delegate = delegate;
      this.hook = hook;
    }

    @Override
    public int getMaxIdleSeconds() {
      if (armed.getAndSet(false)) {
        hook.run();
      }
      return delegate.getMaxIdleSeconds();
    }

    @Override
    public boolean isLazy() {
      return delegate.isLazy();
    }

    @Override
    public boolean isPersistent() {
      return delegate.isPersistent();
    }

    @Override
    public int getMaxReconnectDelaySeconds() {
      return delegate.getMaxReconnectDelaySeconds();
    }

    @Override
    public ChannelActions getChannelActions() {
      return delegate.getChannelActions();
    }

    @Override
    public Executor getExecutor() {
      return delegate.getExecutor();
    }

    @Override
    public Scheduler getScheduler() {
      return delegate.getScheduler();
    }

    @Override
    public String getLoggerName() {
      return delegate.getLoggerName();
    }

    @Override
    public Map<String, String> getLoggingContext() {
      return delegate.getLoggingContext();
    }

    @Override
    public Object getUserContext() {
      return delegate.getUserContext();
    }

  }

}
