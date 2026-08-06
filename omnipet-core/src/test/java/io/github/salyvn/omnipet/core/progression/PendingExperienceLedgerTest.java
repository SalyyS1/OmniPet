package io.github.salyvn.omnipet.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Experience banked between disk writes.
 *
 * <p>Exists so a kill costs no disk write. The properties worth testing are the ones a burst of kills on a
 * grinder would break: that draining and adding cannot lose an amount between them, that a failed write puts
 * back only what it was carrying, and that the whole thing is safe to touch from two threads — kills arrive
 * on the server's main thread and the flush runs off it.
 */
class PendingExperienceLedgerTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID PET = UUID.randomUUID();

    @Test
    void repeatedKillsAccumulateIntoOneAmount() {
        PendingExperienceLedger ledger = new PendingExperienceLedger();

        ledger.add(OWNER, PET, 10);
        ledger.add(OWNER, PET, 15);
        ledger.add(OWNER, PET, 5);

        assertEquals(30.0, ledger.drain().get(OWNER).get(PET));
    }

    /** Draining empties, so the next flush does not pay the same experience twice. */
    @Test
    void drainingLeavesNothingBehind() {
        PendingExperienceLedger ledger = new PendingExperienceLedger();
        ledger.add(OWNER, PET, 10);

        assertFalse(ledger.isEmpty());
        assertEquals(10.0, ledger.drain().get(OWNER).get(PET));
        assertTrue(ledger.isEmpty());
        assertTrue(ledger.drain().isEmpty());
    }

    @Test
    void ownersAndPetsAreKeptApart() {
        PendingExperienceLedger ledger = new PendingExperienceLedger();
        UUID otherOwner = UUID.randomUUID();
        UUID otherPet = UUID.randomUUID();

        ledger.add(OWNER, PET, 10);
        ledger.add(OWNER, otherPet, 20);
        ledger.add(otherOwner, PET, 30);

        Map<UUID, Map<UUID, Double>> drained = ledger.drain();
        assertEquals(10.0, drained.get(OWNER).get(PET));
        assertEquals(20.0, drained.get(OWNER).get(otherPet));
        assertEquals(30.0, drained.get(otherOwner).get(PET));
    }

    /**
     * A failed write puts the amount back rather than dropping it.
     *
     * <p>The player killed those mobs. A flush that loses its revision race has to leave the experience for
     * the next one, or a contended server silently eats progress in proportion to how busy it is.
     */
    @Test
    void restoredExperienceIsCarriedByTheNextFlush() {
        PendingExperienceLedger ledger = new PendingExperienceLedger();
        ledger.add(OWNER, PET, 10);
        Map<UUID, Double> failed = ledger.drain().get(OWNER);

        ledger.add(OWNER, PET, 7);
        ledger.restore(OWNER, failed);

        assertEquals(17.0, ledger.drain().get(OWNER).get(PET),
                "what was banked while the write was in flight must survive the restore");
    }

    /** An amount that is not a positive number is a no-op, not an error the caller has to guard. */
    @Test
    void nonPositiveAmountsAreIgnored() {
        PendingExperienceLedger ledger = new PendingExperienceLedger();

        ledger.add(OWNER, PET, 0);
        ledger.add(OWNER, PET, -5);
        ledger.add(OWNER, PET, Double.NaN);
        ledger.add(OWNER, PET, Double.POSITIVE_INFINITY);

        assertTrue(ledger.isEmpty());
    }

    @Test
    void anOwnerCanBeForgotten() {
        PendingExperienceLedger ledger = new PendingExperienceLedger();
        ledger.add(OWNER, PET, 10);

        ledger.forget(OWNER);

        assertTrue(ledger.isEmpty());
    }

    /**
     * Nothing is lost when kills land during a flush.
     *
     * <p>The race this class exists to survive: kills arrive on the main thread while the flush drains from
     * another. Every amount added must come out of exactly one drain — losing one silently costs a player
     * progress, and counting one twice pays them for a kill they did not make.
     */
    @Test
    void concurrentKillsAndFlushesLoseNothing() throws Exception {
        PendingExperienceLedger ledger = new PendingExperienceLedger();
        int kills = 2_000;
        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.atomic.DoubleAdder drained = new java.util.concurrent.atomic.DoubleAdder();

        var killer = threads.submit(() -> {
            await(start);
            for (int index = 0; index < kills; index++) ledger.add(OWNER, PET, 1);
        });
        var flusher = threads.submit(() -> {
            await(start);
            for (int index = 0; index < kills; index++) {
                ledger.drain().values().forEach(pets -> pets.values().forEach(drained::add));
            }
        });

        start.countDown();
        killer.get(30, TimeUnit.SECONDS);
        flusher.get(30, TimeUnit.SECONDS);
        threads.shutdownNow();

        // Whatever the last flush missed is still banked, so the two together must be the whole amount.
        ledger.drain().values().forEach(pets -> pets.values().forEach(drained::add));
        assertEquals((double) kills, drained.sum(),
                "every banked amount has to come out of exactly one drain");
    }

    /** A drained map must not change under the flusher when the next kill lands. */
    @Test
    void aDrainedSnapshotIsNotDisturbedByLaterKills() {
        PendingExperienceLedger ledger = new PendingExperienceLedger();
        ledger.add(OWNER, PET, 10);

        Map<UUID, Map<UUID, Double>> drained = ledger.drain();
        ledger.add(OWNER, PET, 99);

        assertEquals(10.0, drained.get(OWNER).get(PET));
        assertEquals(List.of(OWNER), List.copyOf(drained.keySet()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
