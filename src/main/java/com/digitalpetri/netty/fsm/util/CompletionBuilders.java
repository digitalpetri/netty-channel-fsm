/*
 * Copyright 2018 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.netty.fsm.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CompletionBuilders {

    /**
     * Complete {@code future} with the result of the {@link CompletableFuture} that is provided to the returned
     * {@link CompletionBuilder}.
     *
     * @param future the future to complete.
     * @return a {@link CompletionBuilder}.
     */
    public static <T> CompletionBuilder<T> complete(CompletableFuture<T> future) {
        return new CompletionBuilder<>(future);
    }

    /**
     * Complete {@code future} asynchronously with the result of the {@link CompletableFuture} that is provided to
     * the returned {@link CompletionBuilder}.
     *
     * @param future   the future to complete.
     * @param executor the {@link Executor} to use.
     * @return a {@link CompletionBuilder}.
     */
    public static <T> CompletionBuilder<T> completeAsync(CompletableFuture<T> future, Executor executor) {
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
         * Complete the contained to-be-completed {@link CompletableFuture} using the result of {@code future}.
         *
         * @param future the {@link CompletableFuture} to use as the result for the contained future.
         * @return the original, to-be-completed future provided to this {@link CompletionBuilder}.
         */
        public CompletableFuture<T> with(CompletableFuture<T> future) {
            future.whenComplete((v, ex) -> {
                if (ex != null) toComplete.completeExceptionally(ex);
                else toComplete.complete(v);
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
                if (ex != null) toComplete.completeExceptionally(ex);
                else toComplete.complete(v);
            }, executor);

            return toComplete;
        }

    }

}
