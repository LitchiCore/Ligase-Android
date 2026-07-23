package com.limelight.ligase.endpoint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Executes side-effect-free endpoint probes with one overall deadline.
 */
public final class LigaseEndpointRaceExecutor<T> {
    public interface Probe<T> {
        @Nullable
        T probe(@NonNull LigaseEndpoint endpoint);
    }

    public static final class Result<T> {
        @NonNull public final LigaseEndpoint endpoint;
        @NonNull public final T value;

        Result(@NonNull LigaseEndpoint endpoint, @NonNull T value) {
            this.endpoint = endpoint;
            this.value = value;
        }
    }

    @Nullable
    public Result<T> race(
            @NonNull List<LigaseEndpointSelectionPlan.Attempt> attempts,
            @NonNull Probe<T> probe) throws InterruptedException {
        if (attempts.isEmpty()) {
            return null;
        }

        ExecutorService executor = Executors.newFixedThreadPool(attempts.size());
        ExecutorCompletionService<Result<T>> completion =
                new ExecutorCompletionService<>(executor);
        List<Future<Result<T>>> futures = new ArrayList<>(attempts.size());
        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(
                        LigaseEndpointSelectionPlan.OVERALL_TIMEOUT_MILLIS);
        try {
            for (LigaseEndpointSelectionPlan.Attempt attempt : attempts) {
                Callable<Result<T>> task = () -> {
                    if (attempt.startDelayMillis > 0) {
                        Thread.sleep(attempt.startDelayMillis);
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        return null;
                    }
                    T value = probe.probe(attempt.endpoint);
                    return value == null ? null : new Result<>(attempt.endpoint, value);
                };
                futures.add(completion.submit(task));
            }

            for (int completed = 0; completed < attempts.size(); completed++) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    return null;
                }
                Future<Result<T>> completedFuture =
                        completion.poll(remaining, TimeUnit.NANOSECONDS);
                if (completedFuture == null) {
                    return null;
                }
                try {
                    Result<T> result = completedFuture.get();
                    if (result != null) {
                        return result;
                    }
                }
                catch (java.util.concurrent.ExecutionException ignored) {
                    // A failed candidate does not prevent another candidate from winning.
                }
            }
            return null;
        }
        finally {
            for (Future<Result<T>> future : futures) {
                future.cancel(true);
            }
            executor.shutdownNow();
        }
    }
}
