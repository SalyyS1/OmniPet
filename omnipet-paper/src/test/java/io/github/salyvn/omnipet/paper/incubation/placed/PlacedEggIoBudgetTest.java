package io.github.salyvn.omnipet.paper.incubation.placed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a placed-egg pass is allowed to cost in disk operations.
 *
 * <p>The cost was invisible to a correctness test: every pass re-scanned the whole directory and re-parsed
 * every file this code had just written itself, re-read the egg definition per egg, and fsynced twice per
 * egg. At a hundred placed eggs that was hundreds of reads and a hundred double-fsync writes per second,
 * all on the tick thread, for a countdown that only had to be roughly right.
 *
 * <p>{@link PlacedEggCoordinator#tick} needs a live Bukkit {@code Block} to read the surrounding heat, so
 * the per-pass behaviour is asserted against the source. The parts reachable without a server — the store's
 * own write cost, and that a scan is what a restart pays rather than what every pass pays — are executed.
 * The durability property stays in {@link PlacedEggDurabilityTest} and is deliberately unchanged.
 */
class PlacedEggIoBudgetTest {
    @TempDir
    Path root;

    @Test
    void oneSaveWritesOneRecordAndOneBackup() throws IOException {
        // The reason a per-second save was expensive: each one is an atomic write with its own backup, so
        // "just save it every tick" was never as cheap as it reads.
        PlacedEggStore store = new PlacedEggStore(root);

        store.save(PlacedEggTestRecords.record("world", 1, 2, 3));

        assertEquals(1, store.scanAll().records().size());
        assertTrue(Files.list(root).count() >= 1, "the record has to reach disk the moment it is placed");
    }

    @Test
    void aScanIsWhatARestartPaysNotWhatEveryPassPays() throws IOException {
        PlacedEggStore store = new PlacedEggStore(root);
        for (int index = 0; index < 5; index++) {
            store.save(PlacedEggTestRecords.record("world", index, 2, 3));
        }

        // The scan itself still works and is still bounded -- it is the startup path.
        PlacedEggStore.Scan scan = store.scanAll();

        assertEquals(5, scan.records().size());
        assertTrue(scan.unreadable().isEmpty());
    }

    @Test
    void thePassReadsTheWorkingCopyRatherThanRescanningEveryTime() throws Exception {
        String source = source("PlacedEggCoordinator.java");

        // all() must consult a loaded flag before reaching for the store.
        assertTrue(Pattern.compile("if \\(!loaded\\) \\{.*?store\\.scanAll\\(\\)", Pattern.DOTALL)
                        .matcher(source).find(),
                "the directory scan must happen once, not once per pass");
        assertTrue(source.contains("return new PlacedEggStore.Scan(List.copyOf(records.values())"),
                "later calls must answer from the working copy");
    }

    @Test
    void aMovingCountdownReachesDiskOnAFlushRatherThanEveryPass() throws Exception {
        String source = source("PlacedEggCoordinator.java");

        // tick() records the change in memory and marks it, and does not call save() itself except when
        // the egg has become ready.
        assertTrue(source.contains("records.put(advanced.key(), advanced);"));
        assertTrue(source.contains("unflushed.add(advanced.key());"));
        assertTrue(source.contains("if (advanced.ready()) flush();"),
                "a ready egg is about to be granted, so it cannot wait for the flush cadence");
        assertTrue(Pattern.compile("public void flush\\(\\).*?store\\.save\\(record\\)", Pattern.DOTALL)
                        .matcher(source).find(),
                "the only periodic write path is flush()");
    }

    @Test
    void theEggDefinitionIsCachedRatherThanReReadPerPass() throws Exception {
        String source = source("PlacedEggCoordinator.java");

        assertTrue(source.contains("PlacementRequirement cached = requirements.get(eggId);"),
                "an egg definition is static config; a pass must not re-read it");
        assertTrue(source.contains("public void invalidateDefinitions()"),
                "a reload is the one thing that can change a definition, so it must be able to clear this");
    }

    @Test
    void theFlushCadenceAndShutdownAreBothWired() throws Exception {
        String source = source("PlacedEggView.java");

        assertTrue(source.contains("FLUSH_INTERVAL_NANOS"));
        assertTrue(Pattern.compile("lastFlushNanos = now;\\s*coordinator\\.flush\\(\\);", Pattern.DOTALL)
                        .matcher(source).find(),
                "the pass must flush on a cadence");
        assertTrue(Pattern.compile("public void stop\\(\\).*?coordinator\\.flush\\(\\)", Pattern.DOTALL)
                        .matcher(source).find(),
                "shutdown must persist what only lived in memory");
    }

    @Test
    void aGrantedEggLeavesTheWorkingCopyEvenWhenTheDeleteFails() throws Exception {
        // The pet has already been granted by this point. Replaying the record would hand out a second one,
        // so it must go regardless of whether the file could be removed.
        String source = source("PlacedEggCoordinator.java");

        assertTrue(Pattern.compile("public void consume\\(.*?finally \\{.*?forget\\(record\\.key\\(\\)\\);",
                        Pattern.DOTALL).matcher(source).find(),
                "a granted egg must leave the working copy on every path");
    }

    private static String source(String name) throws Exception {
        Path relative = Path.of("src/main/java/io/github/salyvn/omnipet/paper/incubation/placed").resolve(name);
        Path path = Files.exists(relative) ? relative : Path.of("omnipet-paper").resolve(relative);
        return Files.readString(path);
    }

    /** Shared record shape, so the two placed-egg tests cannot drift on what a valid record looks like. */
    private static final class PlacedEggTestRecords {
        private static PlacedEggRecord record(String world, int x, int y, int z) {
            return new PlacedEggRecord(
                    "dragon_egg", java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                    world, x, y, z, 60_000L, 60_000L, "AAAA", java.util.Map.of());
        }
    }
}
