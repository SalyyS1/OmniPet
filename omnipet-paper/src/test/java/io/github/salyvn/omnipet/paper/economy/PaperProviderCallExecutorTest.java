package io.github.salyvn.omnipet.paper.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class PaperProviderCallExecutorTest {
    @Test
    void primaryThreadCallsRunDirectlyWithoutScheduling() throws Exception {
        TestScheduler scheduler = new TestScheduler(true);
        PaperProviderCallExecutor calls = new PaperProviderCallExecutor(scheduler);

        assertEquals("done", calls.call(() -> "done"));
        assertFalse(scheduler.scheduled);
    }

    @Test
    void shutdownCannotMissFutureWhileSchedulingIsInProgress() throws Exception {
        TestScheduler scheduler = new TestScheduler(false);
        PaperProviderCallExecutor calls = new PaperProviderCallExecutor(scheduler);
        Thread caller = new Thread(() -> {
            try {
                calls.call(() -> "unused");
            } catch (Exception ignored) {
                // Cancellation is the expected shutdown result.
            }
        }, "provider-caller");
        CountDownLatch shutdownStarted = new CountDownLatch(1);
        CountDownLatch shutdownFinished = new CountDownLatch(1);
        Thread shutdown = new Thread(() -> {
            shutdownStarted.countDown();
            calls.shutdown();
            shutdownFinished.countDown();
        }, "provider-shutdown");

        caller.start();
        assertTrue(scheduler.schedulingStarted.await(2, TimeUnit.SECONDS));
        shutdown.start();
        assertTrue(shutdownStarted.await(2, TimeUnit.SECONDS));
        assertFalse(shutdownFinished.await(100, TimeUnit.MILLISECONDS));
        scheduler.releaseScheduling.countDown();

        assertTrue(shutdownFinished.await(2, TimeUnit.SECONDS));
        caller.join(2000);
        assertFalse(caller.isAlive());
        assertTrue(scheduler.future.isCancelled());
    }

    private static final class TestScheduler implements PaperProviderCallExecutor.SyncCallScheduler {
        private final boolean primaryThread;
        private final CountDownLatch schedulingStarted = new CountDownLatch(1);
        private final CountDownLatch releaseScheduling = new CountDownLatch(1);
        private final FutureTask<Object> future = new FutureTask<>(() -> "unused");
        private volatile boolean scheduled;

        private TestScheduler(boolean primaryThread) {
            this.primaryThread = primaryThread;
        }

        @Override
        public boolean isPrimaryThread() {
            return primaryThread;
        }

        @Override
        public Future<?> schedule(Callable<?> operation) {
            scheduled = true;
            schedulingStarted.countDown();
            try {
                releaseScheduling.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return future;
        }
    }
}
