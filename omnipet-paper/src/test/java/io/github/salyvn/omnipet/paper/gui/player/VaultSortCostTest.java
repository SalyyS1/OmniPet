package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;

/**
 * Sorting runs off a click on Paper's main thread, so its cost is a correctness concern.
 *
 * <p>Each sort key must be derived <strong>once per pet</strong>, not once per comparison. Reading
 * {@link VaultPetSummary} inside a comparator re-parses the pet's component and extension maps on
 * every one of the {@code n log n} comparisons. Measured at the 100,000-pet vault cap that was a
 * 1.4-second stall — roughly 28 ticks — on a single click; derive-once brought the same case to about
 * 250ms and a realistic 1,000-pet vault to under 5ms.
 */
class VaultSortCostTest {
    @Test
    void sortKeysAreDerivedOncePerPetNotOncePerComparison() {
        List<PetInstance> pets = pets(4_000);
        CountingPets counted = new CountingPets(pets);

        VaultSortOrder.FAVORITES_FIRST.sort(counted);

        // A comparison-sort makes far more comparisons than it has elements, so a per-comparison
        // derivation would read the list many times over. One read per pet is the whole point.
        assertEquals(pets.size(), counted.reads(),
                "each pet must be visited exactly once to build its sort keys");
    }

    @Test
    void aRealisticVaultSortsFastEnoughToRunOffAClick() {
        List<PetInstance> pets = pets(1_000);

        for (VaultSortOrder order : VaultSortOrder.values()) {
            for (int warmup = 0; warmup < 20; warmup++) order.sort(pets);
            long best = Long.MAX_VALUE;
            for (int run = 0; run < 5; run++) {
                long started = System.nanoTime();
                order.sort(pets);
                best = Math.min(best, System.nanoTime() - started);
            }
            long millis = best / 1_000_000;
            // Deliberately loose: this catches a return to per-comparison derivation, which was two
            // orders of magnitude slower, without failing on a busy CI machine.
            assertTrue(millis < 100, order + " took " + millis + "ms for 1,000 pets");
        }
    }

    @Test
    void noComparatorDerivesTheSummaryInline() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/salyvn/omnipet/paper/gui/player/VaultSortOrder.java"));

        // One call site, inside the record that pre-computes the keys.
        assertEquals(1, count(source, "VaultPetSummary.of("),
                "a second call site means a comparator is re-deriving per comparison");
        assertTrue(source.contains("record Ranked("), "sort keys belong to a pre-computed carrier");
    }

    private static int count(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static List<PetInstance> pets(int size) {
        List<PetInstance> pets = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            pets.add(new PetInstance(
                    UUID.randomUUID(),
                    "pet-" + (index % 50),
                    1,
                    Map.of("progression", Map.of("level", index % 100),
                            "hatching", Map.of("rarityId", "rarity-" + (index % 5))),
                    index % 4 == 0 ? Map.of("management", Map.of("favorite", true)) : Map.of()));
        }
        return List.copyOf(pets);
    }

    /** Counts element reads, so a per-comparison derivation is visible as a read explosion. */
    private static final class CountingPets extends java.util.AbstractList<PetInstance> {
        private final List<PetInstance> delegate;
        private int reads;

        private CountingPets(List<PetInstance> delegate) {
            this.delegate = delegate;
        }

        @Override
        public PetInstance get(int index) {
            reads++;
            return delegate.get(index);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        private int reads() {
            return reads;
        }
    }
}
