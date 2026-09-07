package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedRepositoryLockRegistryTest {
    @TempDir
    Path directory;

    @Test
    void nestedAcquireOnTheSameThreadFailsFastInsteadOfSpinning() throws Exception {
        Path lockFile = directory.resolve("player.lock");
        try (SharedRepositoryLockRegistry.Lease lease = SharedRepositoryLockRegistry.acquire(lockFile)) {
            assertTrue(SharedRepositoryLockRegistry.isLocallyHeld(lockFile));
            assertEquals(Thread.currentThread(), SharedRepositoryLockRegistry.ownerThread(lockFile));

            long started = System.nanoTime();
            assertThrows(IllegalStateException.class, () -> SharedRepositoryLockRegistry.acquire(lockFile));
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            assertTrue(elapsedMillis < 1_000, "nested acquire should fail fast, took " + elapsedMillis + "ms");
        }
        assertFalse(SharedRepositoryLockRegistry.isLocallyHeld(lockFile));
        assertNull(SharedRepositoryLockRegistry.ownerThread(lockFile));
    }

    @Test
    void concurrentAcquiresSerializeAndLeaveNoHolder() throws Exception {
        Path lockFile = directory.resolve("player.lock");
        // The guarded file, never the lock file itself: the lock is an exclusive OS file lock, and
        // Windows refuses a write to a locked file, which would fail the test for a reason that has
        // nothing to do with mutual exclusion.
        Path guarded = directory.resolve("player.yml");
        int threads = 4;
        int rounds = 25;
        AtomicInteger inCriticalSection = new AtomicInteger();
        AtomicInteger overlapSeen = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int thread = 0; thread < threads; thread++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int round = 0; round < rounds; round++) {
                        try (SharedRepositoryLockRegistry.Lease ignored =
                                SharedRepositoryLockRegistry.acquire(lockFile)) {
                            if (inCriticalSection.incrementAndGet() > 1) overlapSeen.incrementAndGet();
                            Files.writeString(guarded, "held");
                            inCriticalSection.decrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, overlapSeen.get(), "two threads held the same player lock at once");
        assertFalse(SharedRepositoryLockRegistry.isLocallyHeld(lockFile));
    }

    @Test
    void aReleasedLockIsImmediatelyAvailableToTheNextThread() throws Exception {
        Path lockFile = directory.resolve("player.lock");
        try (SharedRepositoryLockRegistry.Lease ignored = SharedRepositoryLockRegistry.acquire(lockFile)) {
            // Holder still inside the lease.
        }
        long started = System.nanoTime();
        try (SharedRepositoryLockRegistry.Lease ignored = SharedRepositoryLockRegistry.acquire(lockFile)) {
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            assertTrue(elapsedMillis < 5_000, "re-acquire after release should be fast, took " + elapsedMillis + "ms");
        }
    }
}
