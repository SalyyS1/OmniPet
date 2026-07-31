package io.github.salyvn.omnipet.paper.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class PerPlayerTaskQueueTest {
    @Test
    void serializesOnePlayersTasksInSubmissionOrder() {
        ControlledExecutor executor = new ControlledExecutor();
        PerPlayerTaskQueue queue = new PerPlayerTaskQueue(executor);
        UUID playerId = UUID.randomUUID();
        List<Integer> completed = new ArrayList<>();

        queue.submit(playerId, () -> completed.add(1));
        queue.submit(playerId, () -> completed.add(2));

        assertEquals(1, executor.pendingCount());
        executor.runNext();
        assertEquals(List.of(1, 2), completed);
    }

    @Test
    void dispatchesDifferentPlayersConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        PerPlayerTaskQueue queue = new PerPlayerTaskQueue(executor);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Runnable blockingTask = () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        try {
            queue.submit(UUID.randomUUID(), blockingTask);
            queue.submit(UUID.randomUUID(), blockingTask);

            assertTrue(started.await(2, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(queue.awaitIdle(Duration.ofSeconds(2)));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void coalescesOnlyMatchingNamespacedReadsWithoutReorderingMutations() {
        ControlledExecutor executor = new ControlledExecutor();
        PerPlayerTaskQueue queue = new PerPlayerTaskQueue(executor);
        UUID playerId = UUID.randomUUID();
        List<Integer> completed = new ArrayList<>();

        queue.submitLatest(playerId, "vault:view", () -> completed.add(1));
        queue.submitLatest(playerId, "slot:view", () -> completed.add(2));
        queue.submit(playerId, () -> completed.add(3));
        queue.submitLatest(playerId, "vault:view", () -> completed.add(4));

        executor.runNext();
        assertEquals(List.of(2, 3, 4), completed);
        assertThrows(IllegalArgumentException.class,
                () -> queue.submitLatest(playerId, "view", () -> {}));
    }

    @Test
    void shutdownRejectsNewWorkDropsPendingReadsAndDrainsAcceptedMutations() throws Exception {
        ControlledExecutor executor = new ControlledExecutor();
        PerPlayerTaskQueue queue = new PerPlayerTaskQueue(executor);
        UUID playerId = UUID.randomUUID();
        List<Integer> completed = new ArrayList<>();

        assertTrue(queue.submit(playerId, () -> completed.add(1)));
        assertTrue(queue.submitLatest(playerId, "vault:view", () -> completed.add(99)));
        assertTrue(queue.submit(playerId, () -> completed.add(2)));
        queue.shutdown();

        assertFalse(queue.submit(playerId, () -> completed.add(3)));
        executor.runNext();
        assertEquals(List.of(1, 2), completed);
        assertTrue(queue.awaitIdle(Duration.ofMillis(10)));
    }

    @Test
    void taskFailureDoesNotPreventTheNextAcceptedMutation() throws Exception {
        ControlledExecutor executor = new ControlledExecutor();
        PerPlayerTaskQueue queue = new PerPlayerTaskQueue(executor);
        UUID playerId = UUID.randomUUID();
        List<Integer> completed = new ArrayList<>();
        List<Throwable> reported = new ArrayList<>();
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
        thread.setUncaughtExceptionHandler((ignored, failure) -> reported.add(failure));
        try {
            queue.submit(playerId, () -> { throw new IllegalStateException("boom"); });
            queue.submit(playerId, () -> completed.add(2));
            executor.runNext();
        } finally {
            thread.setUncaughtExceptionHandler(previous);
        }

        assertEquals(1, reported.size());
        assertEquals(List.of(2), completed);
        assertTrue(queue.awaitIdle(Duration.ofMillis(10)));
    }

    @Test
    void executorRejectionDoesNotLeaveThePlayerQueueBusy() throws Exception {
        PerPlayerTaskQueue queue = new PerPlayerTaskQueue(command -> {
            throw new RejectedExecutionException("executor stopped");
        });

        assertThrows(RejectedExecutionException.class,
                () -> queue.submit(UUID.randomUUID(), () -> {}));
        assertTrue(queue.awaitIdle(Duration.ofMillis(10)));
    }

    @Test
    void concurrentSubmitCannotBeAcceptedAndLostByFirstDispatchRejection() throws Exception {
        BlockingFirstRejectionExecutor executor = new BlockingFirstRejectionExecutor();
        PerPlayerTaskQueue queue = new PerPlayerTaskQueue(executor);
        UUID playerId = UUID.randomUUID();
        List<Integer> completed = new ArrayList<>();
        FutureTask<Boolean> first = new FutureTask<>(() ->
                queue.submit(playerId, () -> completed.add(1)));
        FutureTask<Boolean> second = new FutureTask<>(() -> {
            executor.concurrentSubmitStarted.countDown();
            return queue.submit(playerId, () -> completed.add(2));
        });
        Thread firstThread = new Thread(first, "queue-first-submit");
        Thread secondThread = new Thread(second, "queue-concurrent-submit");

        firstThread.start();
        assertTrue(executor.firstDispatchStarted.await(2, TimeUnit.SECONDS));
        secondThread.start();
        assertTrue(executor.concurrentSubmitStarted.await(2, TimeUnit.SECONDS));
        assertTrue(reachedAdmissionBoundary(second, secondThread));
        executor.rejectFirstDispatch.countDown();

        assertThrows(java.util.concurrent.ExecutionException.class, first::get);
        assertTrue(second.get(2, TimeUnit.SECONDS));
        executor.runNext();
        assertEquals(List.of(2), completed);
        assertTrue(queue.submit(playerId, () -> completed.add(3)));
        executor.runNext();
        assertEquals(List.of(2, 3), completed);
    }

    @Test
    void hugeAwaitDurationSaturatesInsteadOfOverflowing() throws Exception {
        PerPlayerTaskQueue queue = new PerPlayerTaskQueue(Runnable::run);

        assertTrue(queue.awaitIdle(Duration.ofSeconds(Long.MAX_VALUE)));
    }

    private static final class ControlledExecutor implements Executor {
        private final Queue<Runnable> pending = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        int pendingCount() {
            return pending.size();
        }

        void runNext() {
            pending.remove().run();
        }
    }

    private static boolean reachedAdmissionBoundary(FutureTask<?> submit, Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (submit.isDone() || thread.getState() == Thread.State.WAITING) return true;
            Thread.onSpinWait();
        }
        return false;
    }

    private static final class BlockingFirstRejectionExecutor implements Executor {
        private final CountDownLatch firstDispatchStarted = new CountDownLatch(1);
        private final CountDownLatch concurrentSubmitStarted = new CountDownLatch(1);
        private final CountDownLatch rejectFirstDispatch = new CountDownLatch(1);
        private final Queue<Runnable> pending = new ArrayDeque<>();
        private final AtomicBoolean first = new AtomicBoolean(true);

        @Override
        public void execute(Runnable command) {
            if (first.compareAndSet(true, false)) {
                firstDispatchStarted.countDown();
                try {
                    rejectFirstDispatch.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                throw new RejectedExecutionException("first dispatch rejected");
            }
            synchronized (pending) {
                pending.add(command);
            }
        }

        void runNext() {
            Runnable task;
            synchronized (pending) {
                task = pending.remove();
            }
            task.run();
        }
    }
}
