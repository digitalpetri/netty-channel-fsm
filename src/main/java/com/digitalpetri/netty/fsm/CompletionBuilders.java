/*
 * Copyright (c) 2024 Kevin Herron
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.digitalpetri.netty.fsm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

class CompletionBuilders {

  private CompletionBuilders() {}

  /**
   * Complete {@code future} with the result of the {@link CompletableFuture} that is provided to
   * the returned {@link CompletionBuilder}.
   *
   * @param future the future to complete.
   * @param <T> the type returned by {@code future}.
   * @return a {@link CompletionBuilder}.
   */
  public static <T> CompletionBuilder<T> complete(CompletableFuture<T> future) {
    return new CompletionBuilder<>(future);
  }

  /**
   * Complete {@code future} asynchronously with the result of the {@link CompletableFuture} that is
   * provided to the returned {@link CompletionBuilder}.
   *
   * @param future the future to complete.
   * @param executor the {@link Executor} to use.
   * @param <T> the type returned by {@code future}.
   * @return a {@link CompletionBuilder}.
   */
  public static <T> CompletionBuilder<T> completeAsync(CompletableFuture<T> future,
      Executor executor) {
    return new AsyncCompletionBuilder<>(future, executor);
  }

  public static class CompletionBuilder<T> {

    final CompletableFuture<T> toComplete;

    private CompletionBuilder(CompletableFuture<T> toComplete) {
      this.toComplete = toComplete;
    }

    /**
     * Turn this {@link CompletionBuilder} into an {@link AsyncCompletionBuilder}.
     *
     * @param executor the {@link Executor} to use for the async completion.
     * @return an {@link AsyncCompletionBuilder}.
     */
    public CompletionBuilder<T> async(Executor executor) {
      return new AsyncCompletionBuilder<>(toComplete, executor);
    }

    /**
     * Complete the contained to-be-completed {@link CompletableFuture} using the result of
     * {@code future}.
     *
     * @param future the {@link CompletableFuture} to use as the result for the contained
     *     future.
     * @return the original, to-be-completed future provided to this {@link CompletionBuilder}.
     */
    public CompletableFuture<T> with(CompletableFuture<T> future) {
      future.whenComplete((v, ex) -> {
        if (ex != null) {
          toComplete.completeExceptionally(ex);
        } else {
          toComplete.complete(v);
        }
      });

      return toComplete;
    }

  }

  private static final class AsyncCompletionBuilder<T> extends CompletionBuilder<T> {

    private final Executor executor;

    AsyncCompletionBuilder(CompletableFuture<T> toComplete, Executor executor) {
      super(toComplete);

      this.executor = executor;
    }

    @Override
    public CompletableFuture<T> with(CompletableFuture<T> future) {
      future.whenCompleteAsync((v, ex) -> {
        if (ex != null) {
          toComplete.completeExceptionally(ex);
        } else {
          toComplete.complete(v);
        }
      }, executor);

      return toComplete;
    }

  }

}
