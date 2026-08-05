package io.github.salyvn.omnipet.paper.incubation.placed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.incubation.EggDefinitionEnvelope;
import io.github.salyvn.omnipet.core.persistence.EggDefinitionRepository;

/**
 * Spending a support item on a placed egg, and the one property that matters: the item and the effect must
 * never come apart.
 *
 * <p>A support item is gone from the player's inventory once spent. The per-second countdown may lag disk by
 * a flush interval because the clock regenerates the loss, but a redeemed item does not — so the reduction
 * is written durably before the item is taken, and a failed write must leave the record untouched so the
 * caller keeps the item. Those are the two directions tested here.
 *
 * <p>No Bukkit needed: {@code applyReduction} deliberately does not re-check the placement requirement, so
 * unlike {@code tick} it never reads a live block. That is a design decision, not an omission — a player
 * spending their own item should not be refused because a neighbouring lava block was mined — and this test
 * exists partly to pin it.
 */
class PlacedEggSupportReductionTest {
    @TempDir
    Path root;

    @Test
    void aReductionIsOnDiskBeforeTheCallerCanTakeTheItem() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggCoordinator coordinator = coordinator(store);
        PlacedEggRecord placed = record(60_000L);
        store.save(placed);

        Optional<PlacedEggRecord> advanced = coordinator.applyReduction(placed, 20_000L);

        assertTrue(advanced.isPresent());
        assertEquals(40_000L, advanced.get().remainingMillis());
        // Read back from the store rather than from the working copy: the point is that the effect survives
        // a crash the instant before the item leaves the player's hand.
        assertEquals(40_000L, store.read(placed.key()).orElseThrow().remainingMillis(),
                "the reduction must be durable before the item is consumed");
    }

    @Test
    void aFailedWriteChangesNothingSoTheCallerKeepsTheItem() throws IOException {
        List<String> warnings = new ArrayList<>();
        PlacedEggStore store = new PlacedEggStore(root.resolve("live"));
        PlacedEggRecord placed = record(60_000L);
        store.save(placed);
        // A real failure mode rather than a stub: the store's root path is occupied by a regular file, so
        // createDirectories throws exactly as it would on a read-only or full disk.
        Path collision = root.resolve("blocked");
        Files.writeString(collision, "not a directory");
        PlacedEggCoordinator coordinator = new PlacedEggCoordinator(
                new PlacedEggStore(collision.resolve("records")), definitions(), warnings::add);

        Optional<PlacedEggRecord> advanced = coordinator.applyReduction(placed, 20_000L);

        assertTrue(advanced.isEmpty(), "a refused write must not report success");
        assertEquals(60_000L, store.read(placed.key()).orElseThrow().remainingMillis(),
                "the record on disk is untouched, so the player still owns the item");
        assertFalse(warnings.isEmpty(), "an operator has to be able to see the write failed");
    }

    @Test
    void aReductionLargerThanTheClockLeavesTheEggReadyRatherThanNegative() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggCoordinator coordinator = coordinator(store);
        PlacedEggRecord placed = record(30_000L);
        store.save(placed);

        PlacedEggRecord advanced = coordinator.applyReduction(placed, 90_000L).orElseThrow();

        assertEquals(0L, advanced.remainingMillis());
        assertTrue(advanced.ready());
    }

    /** An instant-hatch item could pass a saturating figure; wrapping it would hand the egg more time. */
    @Test
    void aSaturatingReductionCannotWrapIntoExtraTime() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggCoordinator coordinator = coordinator(store);
        PlacedEggRecord placed = record(30_000L);
        store.save(placed);

        PlacedEggRecord advanced = coordinator.applyReduction(placed, Long.MAX_VALUE).orElseThrow();

        assertEquals(0L, advanced.remainingMillis());
    }

    @Test
    void aReadyEggAndANonPositiveEffectBothDoNothing() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggCoordinator coordinator = coordinator(store);
        PlacedEggRecord ready = record(60_000L).withRemaining(0);
        store.save(ready);

        assertTrue(coordinator.applyReduction(ready, 10_000L).isEmpty(),
                "a ready egg is claimed by breaking it, so nothing may be spent on one");
        assertTrue(coordinator.applyReduction(record(60_000L), 0).isEmpty());
        assertTrue(coordinator.applyReduction(record(60_000L), -5_000L).isEmpty());
    }

    /** An open menu holds a key, not a block, so a reclaimed record has to resolve to nothing. */
    @Test
    void aRecordIsFoundByKeyAndStopsBeingFoundOnceItIsGone() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggCoordinator coordinator = coordinator(store);
        PlacedEggRecord placed = record(60_000L);
        store.save(placed);

        assertTrue(coordinator.byKey(placed.key()).isPresent());

        coordinator.consume(placed);

        assertTrue(coordinator.byKey(placed.key()).isEmpty());
        assertTrue(coordinator.byKey(null).isEmpty());
        assertTrue(coordinator.byKey("  ").isEmpty());
    }

    /** The saved copy is the durable one, so a pending deferred write for that key must be dropped. */
    @Test
    void aReductionSupersedesAnyDeferredCountdownWriteForThatEgg() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggCoordinator coordinator = coordinator(store);
        PlacedEggRecord placed = record(60_000L);
        store.save(placed);

        PlacedEggRecord advanced = coordinator.applyReduction(placed, 20_000L).orElseThrow();
        // A flush now must not resurrect anything: the reduction already wrote the authoritative copy.
        coordinator.flush();

        assertEquals(advanced.remainingMillis(), store.read(placed.key()).orElseThrow().remainingMillis());
    }

    private PlacedEggCoordinator coordinator(PlacedEggStore store) {
        return new PlacedEggCoordinator(store, definitions(), warning -> { });
    }

    private static EggDefinitionRepository definitions() {
        // Never consulted: applyReduction deliberately does not re-check the placement requirement, which
        // is the only thing that reads a definition. A repository that answers nothing proves that.
        return new EggDefinitionRepository() {
            @Override
            public Optional<EggDefinitionEnvelope> read(String id) {
                return Optional.empty();
            }

            @Override
            public List<String> list() {
                return List.of();
            }

            @Override
            public void save(EggDefinitionEnvelope envelope) {
                throw new UnsupportedOperationException("the reduction path never writes a definition");
            }
        };
    }

    private static PlacedEggRecord record(long totalMillis) {
        return new PlacedEggRecord(
                "dragon_egg", UUID.randomUUID(), UUID.randomUUID(),
                "world", 1, 2, 3, totalMillis, totalMillis, "AAAA", Map.of());
    }
}
