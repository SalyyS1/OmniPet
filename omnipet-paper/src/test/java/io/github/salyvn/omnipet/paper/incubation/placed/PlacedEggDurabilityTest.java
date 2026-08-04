package io.github.salyvn.omnipet.paper.incubation.placed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The property that keeps a placed egg from being duplicated.
 *
 * <p>A placed egg is an item the player still owns, held in a block record rather than in escrow. Exactly
 * one owner must exist at any moment: the record, or the item — never both, never neither. These tests
 * drive that through the store, which is the durable half; the Paper half only translates a block into a
 * key and hands the snapshot back.
 */
class PlacedEggDurabilityTest {
    @TempDir
    Path root;

    @Test
    void breakingConsumesTheRecordBeforeTheItemExistsAgain() throws IOException {
        // Delete-then-return is the order that matters. Returning the item first would leave a window
        // where the record and the item both exist, which is how a break/place cycle duplicates an egg.
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggRecord record = record("world", 1, 2, 3);
        store.save(record);

        assertTrue(store.delete(record.key()), "the record is the single owner and must be consumed");
        assertTrue(store.read(record.key()).isEmpty());
        assertFalse(store.delete(record.key()),
                "a second break finds no record, so no second egg can be handed out");
    }

    @Test
    void twoEggsAtDifferentBlocksAreIndependent() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        store.save(record("world", 1, 2, 3));
        store.save(record("world", 4, 5, 6));

        assertEquals(2, store.scanAll().records().size());
        store.delete(PlacedEggRecord.key("world", 1, 2, 3));
        assertEquals(1, store.scanAll().records().size());
        assertTrue(store.read(PlacedEggRecord.key("world", 4, 5, 6)).isPresent());
    }

    @Test
    void theSameBlockInTwoWorldsIsTwoDifferentEggs() throws IOException {
        // The key includes the world, so identical coordinates in a nether copy are not the same egg.
        PlacedEggStore store = new PlacedEggStore(root);
        store.save(record("world", 1, 2, 3));
        store.save(record("world_nether", 1, 2, 3));

        assertEquals(2, store.scanAll().records().size());
    }

    @Test
    void theStoredSnapshotIsWhatComesBackNotAFreshEgg() throws IOException {
        // Break returns the snapshot taken at placement, so the egg keeps the nonce it was minted with
        // and cannot become a second, differently-identified egg.
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggRecord record = record("world", 1, 2, 3);
        store.save(record);

        PlacedEggRecord read = store.read(record.key()).orElseThrow();

        assertEquals(record.itemSnapshot(), read.itemSnapshot());
        assertEquals(record.itemNonce(), read.itemNonce(),
                "the nonce identifies this exact egg and must survive the round trip");
    }

    @Test
    void aRecordSurvivesTimeBeingCreditedRepeatedly() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggRecord record = record("world", 1, 2, 3);
        store.save(record);

        PlacedEggRecord advanced = record;
        for (int pass = 0; pass < 5; pass++) {
            advanced = advanced.withRemaining(advanced.remainingMillis() - 10_000);
            store.save(advanced);
        }

        PlacedEggRecord read = store.read(record.key()).orElseThrow();
        assertEquals(70_000, read.remainingMillis());
        assertEquals(record.itemNonce(), read.itemNonce());
        assertEquals(1, store.scanAll().records().size(), "ticking must not accumulate records");
    }

    private static PlacedEggRecord record(String world, int x, int y, int z) {
        return new PlacedEggRecord(
                "ember_fox_egg",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                world, x, y, z,
                120_000, 120_000,
                "c25hcHNob3Q=",
                Map.of());
    }
}
