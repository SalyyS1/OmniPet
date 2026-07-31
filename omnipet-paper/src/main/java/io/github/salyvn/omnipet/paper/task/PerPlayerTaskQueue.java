package io.github.salyvn.omnipet.paper.task;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Serializes accepted work per player while allowing different players to run concurrently. */
public final class PerPlayerTaskQueue {
    private final Executor executor;
    private final Map<UUID, SerialQueue> queues = new HashMap<>();
    private boolean accepting = true;

    public PerPlayerTaskQueue(Executor executor) { this.executor = Objects.requireNonNull(executor, "executor"); }

    public boolean submit(UUID playerId, Runnable task) { return submit(playerId, null, task); }

    public boolean submitLatest(UUID playerId, String coalesceKey, Runnable task) {
        validateCoalesceKey(coalesceKey);
        return submit(playerId, coalesceKey, task);
    }

    public void shutdown() {
        synchronized (queues) {
            accepting = false;
            queues.values().forEach(queue -> queue.pending.removeIf(task -> task.coalesceKey() != null));
            queues.notifyAll();
        }
    }

    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("timeout cannot be negative");
        long timeoutNanos = saturatedNanos(timeout);
        long started = System.nanoTime();
        long remainingNanos = timeoutNanos;
        synchronized (queues) {
            while (!queues.isEmpty()) {
                if (remainingNanos <= 0) return false;
                long millis = Math.max(1, Math.min(
                        Duration.ofNanos(remainingNanos).toMillis(), Integer.MAX_VALUE));
                queues.wait(millis);
                remainingNanos = timeoutNanos - (System.nanoTime() - started);
            }
            return true;
        }
    }

    private boolean submit(UUID playerId, String coalesceKey, Runnable task) {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        Objects.requireNonNull(task, "task");
        boolean interrupted = false;
        try {
            while (true) {
                SerialQueue queue;
                boolean startWorker = false;
                synchronized (queues) {
                    if (!accepting) return false;
                    queue = queues.computeIfAbsent(playerId, SerialQueue::new);
                    interrupted |= awaitDispatchAdmission(queue);
                    if (!accepting) return false;
                    if (queues.get(playerId) != queue) continue;
                    if (coalesceKey != null) {
                        queue.pending.removeIf(pending -> coalesceKey.equals(pending.coalesceKey()));
                    }
                    queue.pending.add(new QueuedTask(coalesceKey, task));
                    if (!queue.running) {
                        queue.dispatching = true;
                        startWorker = true;
                    }
                }
                if (startWorker) dispatch(queue);
                return true;
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void dispatch(SerialQueue queue) {
        try {
            executor.execute(() -> runQueue(queue));
            synchronized (queues) {
                if (queues.get(queue.playerId) == queue && queue.dispatching) {
                    queue.dispatching = false;
                    queue.running = true;
                    queues.notifyAll();
                }
            }
        } catch (RuntimeException | Error failure) {
            synchronized (queues) {
                queue.pending.clear();
                queue.dispatching = false;
                queue.running = false;
                queues.remove(queue.playerId, queue);
                queues.notifyAll();
            }
            throw failure;
        }
    }

    private void runQueue(SerialQueue queue) {
        try {
            synchronized (queues) {
                if (queues.get(queue.playerId) != queue) return;
                queue.dispatching = false;
                queue.running = true;
                queues.notifyAll();
            }
            while (true) {
                QueuedTask next;
                synchronized (queues) {
                    next = queue.pending.poll();
                    if (next == null) {
                        finish(queue);
                        return;
                    }
                }
                runSafely(next.action());
            }
        } catch (Throwable failure) {
            reportFailure(failure);
            synchronized (queues) {
                queue.pending.clear();
                finish(queue);
            }
        }
    }

    private void finish(SerialQueue queue) {
        queue.dispatching = false;
        queue.running = false;
        queues.remove(queue.playerId, queue);
        queues.notifyAll();
    }

    private static void runSafely(Runnable task) {
        try {
            task.run();
        } catch (Throwable failure) {
            reportFailure(failure);
        }
    }

    private static void reportFailure(Throwable failure) {
        try {
            Thread thread = Thread.currentThread();
            thread.getUncaughtExceptionHandler().uncaughtException(thread, failure);
        } catch (Throwable ignored) {
            // A broken reporter must not strand later accepted mutations.
        }
    }

    private static void validateCoalesceKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("coalesce key is required");
        int separator = key.indexOf(':');
        if (separator < 1
                || separator == key.length() - 1
                || key.substring(0, separator).isBlank()
                || key.substring(separator + 1).isBlank()) {
            throw new IllegalArgumentException("coalesce key must be namespaced");
        }
    }

    private boolean awaitDispatchAdmission(SerialQueue queue) {
        boolean interrupted = false;
        while (accepting && queue.dispatching && queues.get(queue.playerId) == queue) {
            try {
                queues.wait();
            } catch (InterruptedException failure) {
                interrupted = true;
            }
        }
        return interrupted;
    }

    private static long saturatedNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static final class SerialQueue {
        private final UUID playerId;
        private final Queue<QueuedTask> pending = new ArrayDeque<>();
        private boolean dispatching;
        private boolean running;

        private SerialQueue(UUID playerId) { this.playerId = playerId; }
    }

    private record QueuedTask(String coalesceKey, Runnable action) {}
}
