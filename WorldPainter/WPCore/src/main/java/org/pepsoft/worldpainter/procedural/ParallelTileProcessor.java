package org.pepsoft.worldpainter.procedural;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Tile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Processes a set of tiles (or other work items) on all available processor cores, reporting progress and supporting
 * cancellation. Events are inhibited on the tiles for the duration of the operation and released afterwards on the
 * calling thread.
 */
public final class ParallelTileProcessor {
    private ParallelTileProcessor() {
        // Prevent instantiation
    }

    public static void process(Collection<? extends Tile> tiles, TileTask task, ProgressReceiver progressReceiver) throws OperationCancelled {
        if (tiles.isEmpty()) {
            return;
        }
        tiles.forEach(Tile::inhibitEvents);
        try {
            forEach(tiles, task::process, progressReceiver);
        } finally {
            tiles.forEach(Tile::releaseEvents);
        }
    }

    /**
     * Perform a task for each of a collection of items on all available processor cores, reporting progress and
     * supporting cancellation.
     */
    public static <T> void forEach(Collection<T> items, Consumer<? super T> task, ProgressReceiver progressReceiver) throws OperationCancelled {
        if (items.isEmpty()) {
            return;
        }
        final int threads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), items.size()));
        final ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
            final Thread thread = new Thread(runnable, "Procedural tile processor");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        final AtomicInteger done = new AtomicInteger();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final List<Future<?>> futures = new ArrayList<>(items.size());
        try {
            for (T item: items) {
                futures.add(executor.submit(() -> {
                    if (! cancelled.get()) {
                        task.accept(item);
                    }
                    done.incrementAndGet();
                    return null;
                }));
            }
            executor.shutdown();
            final int total = items.size();
            while (! executor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                if (progressReceiver != null) {
                    try {
                        progressReceiver.setProgress((float) done.get() / total);
                    } catch (OperationCancelled e) {
                        cancelled.set(true);
                        executor.awaitTermination(1, TimeUnit.MINUTES);
                        throw e;
                    }
                }
            }
            for (Future<?> future: futures) {
                future.get();
            }
            if (progressReceiver != null) {
                progressReceiver.setProgress(1.0f);
            }
        } catch (InterruptedException e) {
            cancelled.set(true);
            Thread.currentThread().interrupt();
            throw new OperationCancelled("Interrupted");
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                throw new RuntimeException(cause);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    public interface TileTask {
        void process(Tile tile);
    }
}
