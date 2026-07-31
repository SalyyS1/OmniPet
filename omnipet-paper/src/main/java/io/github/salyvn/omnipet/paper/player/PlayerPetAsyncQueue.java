package io.github.salyvn.omnipet.paper.player;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Serializes repository work per player while allowing different players to run concurrently. */
final class PlayerPetAsyncQueue {
    private final Executor executor;
    private final Map<UUID, SerialQueue> queues = new HashMap<>();
    private boolean accepting = true;

    PlayerPetAsyncQueue(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    boolean submit(UUID playerId, Runnable task) {
        return submit(playerId, null, task);
    }

    boolean submitLatest(UUID playerId, String coalesceKey, Runnable task) {
        if (coalesceKey == null || coalesceKey.isBlank()) {
            throw new IllegalArgumentException("coalesce key is required");
        }
        return submit(playerId, coalesceKey, task);
    }

    private boolean submit(UUID playerId, String coalesceKey, Runnable task) {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        Objects.requireNonNull(task, "task");
        SerialQueue queue;
        boolean startWorker = false;
        synchronized (queues) {
            if (!accepting) return false;
            queue = queues.computeIfAbsent(playerId, SerialQueue::new);
            if (coalesceKey != null) {
                queue.pending.removeIf(pending -> coalesceKey.equals(pending.coalesceKey()));
            }
            queue.pending.add(new QueuedTask(coalesceKey, task));
            if (!queue.running) {
                queue.running = true;
                startWorker = true;
            }
        }
        if (startWorker) dispatch(queue);
        return true;
    }

    void shutdown() {
        synchronized (queues) {
            accepting = false;
            for (SerialQueue queue : queues.values()) {
                queue.pending.removeIf(task -> task.coalesceKey() != null);
            }
            queues.notifyAll();
        }
    }

    boolean awaitIdle(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("timeout cannot be negative");
        long remainingNanos = timeout.toNanos();
        long deadline = System.nanoTime() + remainingNanos;
        synchronized (queues) {
            while (!queues.isEmpty()) {
                if (remainingNanos <= 0) return false;
                long millis = Math.max(1, Math.min(
                        Duration.ofNanos(remainingNanos).toMillis(), Integer.MAX_VALUE));
                queues.wait(millis);
                remainingNanos = deadline - System.nanoTime();
            }
            return true;
        }
    }

    private void dispatch(SerialQueue queue) {
        try {
            executor.execute(() -> runQueue(queue));
        } catch (RuntimeException failure) {
            synchronized (queues) {
                queue.pending.clear();
                queue.running = false;
                queues.remove(queue.playerId, queue);
                queues.notifyAll();
            }
            throw failure;
        }
    }

    private void runQueue(SerialQueue queue) {
        boolean completedNormally = false;
        try {
            while (true) {
                QueuedTask next;
                synchronized (queues) {
                    next = queue.pending.poll();
                    if (next == null) {
                        queue.running = false;
                        queues.remove(queue.playerId, queue);
                        queues.notifyAll();
                        completedNormally = true;
                        return;
                    }
                }
                try {
                    next.action().run();
                } catch (RuntimeException failure) {
                    Thread thread = Thread.currentThread();
                    thread.getUncaughtExceptionHandler().uncaughtException(thread, failure);
                }
            }
        } finally {
            if (!completedNormally) {
                synchronized (queues) {
                    queue.pending.clear();
                    queue.running = false;
                    queues.remove(queue.playerId, queue);
                    queues.notifyAll();
                }
            }
        }
    }

    private static final class SerialQueue {
        private final UUID playerId;
        private final Queue<QueuedTask> pending = new ArrayDeque<>();
        private boolean running;

        private SerialQueue(UUID playerId) {
            this.playerId = playerId;
        }
    }

    private record QueuedTask(String coalesceKey, Runnable action) {}
}
