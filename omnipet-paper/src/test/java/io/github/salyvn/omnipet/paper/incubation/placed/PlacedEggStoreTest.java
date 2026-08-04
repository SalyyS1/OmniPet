package io.github.salyvn.omnipet.paper.incubation.placed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Durable storage for eggs placed in the world.
 *
 * <p>A placed egg is an item the player still owns, so a lost or misread record loses an item. These
 * tests pin the round trip, the one-egg-per-block key, and that an unreadable record is reported rather
 * than quietly skipped.
 */
class PlacedEggStoreTest {
    @TempDir
    Path root;

    @Test
    void aRecordRoundTripsExactly() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggRecord record = record(60_000, 120_000);

        store.save(record);

        assertEquals(record, store.read(record.key()).orElseThrow());
    }

    @Test
    void oneBlockHoldsOneEgg() throws IOException {
        // The key is the block, so saving twice replaces rather than accumulating - which is what makes
        // "one egg per block" enforceable rather than a convention.
        PlacedEggStore store = new PlacedEggStore(root);
        store.save(record(60_000, 120_000));
        store.save(record(30_000, 120_000));

        assertEquals(1, store.scanAll().records().size());
        assertEquals(30_000, store.read(record(0, 120_000).key()).orElseThrow().remainingMillis());
    }

    @Test
    void deletingIsWhatConsumesAPlacedEgg() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        PlacedEggRecord record = record(0, 120_000);
        store.save(record);

        assertTrue(store.delete(record.key()));
        assertFalse(store.delete(record.key()), "a second delete is a no-op, not an error");
        assertTrue(store.read(record.key()).isEmpty());
    }

    @Test
    void anAbsentRecordReadsAsEmptyRatherThanThrowing() throws IOException {
        assertTrue(new PlacedEggStore(root).read("world_1_2_3").isEmpty());
        assertEquals(0, new PlacedEggStore(root).scanAll().records().size());
    }

    @Test
    void anUnreadableRecordIsReportedNotSkipped() throws IOException {
        // Each file is somebody's item. Silently dropping one would look like the egg never existed.
        PlacedEggStore store = new PlacedEggStore(root);
        store.save(record(60_000, 120_000));
        Files.writeString(root.resolve("world_9_9_9.yml"), "schemaVersion: 1\neggId: ''\n",
                StandardCharsets.UTF_8);

        PlacedEggStore.Scan scan = store.scanAll();

        assertEquals(1, scan.records().size());
        assertEquals(1, scan.unreadable().size(), scan.unreadable().toString());
        assertFalse(scan.truncated());
    }

    @Test
    void anUnsupportedSchemaFailsClosed() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        Files.writeString(root.resolve("world_1_2_3.yml"), "schemaVersion: 99\n", StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class, () -> store.read("world_1_2_3"));
    }

    @Test
    void aKeyThatCouldEscapeTheDirectoryIsRefused() {
        PlacedEggStore store = new PlacedEggStore(root);

        assertThrows(IOException.class, () -> store.read("../outside"));
        assertThrows(IOException.class, () -> store.read("nested/child"));
        assertThrows(IOException.class, () -> store.read(""));
    }

    @Test
    void creditingTimeIsBoundedAtBothEnds() {
        PlacedEggRecord record = record(60_000, 120_000);

        assertEquals(0, record.withRemaining(-5).remainingMillis());
        assertEquals(120_000, record.withRemaining(999_999).remainingMillis());
        assertTrue(record.withRemaining(0).ready());
        assertFalse(record.withRemaining(1).ready());
    }

    @Test
    void aRecordRejectsTimeItCouldNotHaveHad() {
        assertThrows(IllegalArgumentException.class, () -> record(120_001, 120_000));
        assertThrows(IllegalArgumentException.class, () -> record(-1, 120_000));
        assertThrows(IllegalArgumentException.class, () -> record(0, 0));
    }

    private static PlacedEggRecord record(long remaining, long total) {
        return new PlacedEggRecord(
                "ember_fox_egg",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "world", 1, 2, 3,
                remaining, total,
                "c25hcHNob3Q=",
                Map.of());
    }
}
