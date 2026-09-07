package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class KeyedLockRegistryTest {
    @Test
    void anEntryDisappearsOnceItsLastHolderReleases() throws Exception {
        KeyedLockRegistry<UUID> registry = new KeyedLockRegistry<>();
        UUID key = UUID.randomUUID();

        registry.withLock(key, () -> {
            assertEquals(1, registry.trackedKeys(), "the held key should be tracked while held");
            return null;
        });

        assertEquals(0, registry.trackedKeys(), "a released key must not stay in the map");
    }

    @Test
    void thousandsOfDistinctKeysLeaveNothingBehind() throws Exception {
        KeyedLockRegistry<UUID> registry = new KeyedLockRegistry<>();

        for (int round = 0; round < 2_000; round++) {
            registry.withLock(UUID.randomUUID(), () -> null);
        }

        assertEquals(0, registry.trackedKeys(), "per-key locks must not accumulate for the process lifetime");
    }

    @Test
    void theSameKeyIsMutuallyExclusiveAcrossThreads() throws Exception {
        KeyedLockRegistry<UUID> registry = new KeyedLockRegistry<>();
        UUID key = UUID.randomUUID();
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int thread = 0; thread < 6; thread++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int round = 0; round < 50; round++) {
                        registry.withLock(key, () -> {
                            if (inside.incrementAndGet() > 1) overlaps.incrementAndGet();
                            Thread.yield();
                            inside.decrementAndGet();
                            return null;
                        });
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, overlaps.get(), "two threads entered the same key at once");
        assertEquals(0, registry.trackedKeys());
    }

    @Test
    void differentKeysDoNotBlockEachOther() throws Exception {
        KeyedLockRegistry<UUID> registry = new KeyedLockRegistry<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> registry.withLock(first, () -> {
                firstEntered.countDown();
                // Holds the first key while the second one is taken by another thread.
                try {
                    assertTrue(secondFinished.await(10, TimeUnit.SECONDS),
                            "a different key should not wait on this one");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while holding the first key", interrupted);
                }
                return null;
            }));
            assertTrue(firstEntered.await(10, TimeUnit.SECONDS));
            pool.submit(() -> registry.withLock(second, () -> {
                secondFinished.countDown();
                return null;
            })).get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFailingOperationStillReleasesAndEvicts() {
        KeyedLockRegistry<UUID> registry = new KeyedLockRegistry<>();
        UUID key = UUID.randomUUID();

        assertThrows(IOException.class, () -> registry.withLock(key, () -> {
            throw new IOException("boom");
        }));

        assertEquals(0, registry.trackedKeys(), "a thrown operation must not leak its lock entry");
    }
}
