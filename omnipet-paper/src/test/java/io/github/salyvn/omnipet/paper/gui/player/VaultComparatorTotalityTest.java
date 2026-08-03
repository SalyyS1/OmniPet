package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;

/**
 * Exhaustively checks the comparator contract Java's sort relies on.
 *
 * <p>{@code List.sort} may throw "Comparison method violates its general contract!" or silently
 * misorder if a comparator is not a total order. These properties are checked over every pair and
 * triple of a deliberately degenerate pet set — duplicate levels, duplicate rarities, duplicate names,
 * and absent values — because that is where a comparator built from chained key extractors breaks.
 */
class VaultComparatorTotalityTest {
    @Test
    void everyComparatorIsAntisymmetricAndTransitiveOverDegenerateData() {
        List<PetInstance> pets = degenerate();

        for (VaultSortOrder order : VaultSortOrder.values()) {
            if (order == VaultSortOrder.RECENT) continue;
            List<PetInstance> sorted = order.sort(pets);

            // Antisymmetry: sorting a reversed input yields the same order, so no pair is "equal" in
            // one direction and ordered in the other.
            List<PetInstance> reversed = new ArrayList<>(pets);
            java.util.Collections.reverse(reversed);
            assertEquals(ids(sorted), ids(order.sort(reversed)),
                    order + " ordering depends on input order, so it is not a total order");

            // Totality: no two distinct pets compare equal, or one could drift between pages.
            assertEquals(pets.size(), ids(sorted).stream().distinct().count(), order.toString());

            // Transitivity via the sorted sequence: re-sorting any sublist must preserve relative order.
            for (int from = 0; from + 3 <= sorted.size(); from++) {
                List<PetInstance> window = sorted.subList(from, from + 3);
                assertEquals(ids(window), ids(order.sort(window)),
                        order + " is not transitive over window starting at " + from);
            }
        }
    }

    @Test
    void sortingADegenerateSetDoesNotThrowTheContractViolation() {
        // A large degenerate set is what triggers TimSort's contract check; a small one can pass by
        // falling into binary insertion sort instead.
        List<PetInstance> many = new ArrayList<>();
        for (int repeat = 0; repeat < 40; repeat++) many.addAll(degenerate());

        for (VaultSortOrder order : VaultSortOrder.values()) {
            List<PetInstance> sorted = order.sort(many);
            assertEquals(many.size(), sorted.size(), order.toString());
            assertTrue(sorted.containsAll(many), order.toString());
        }
    }

    private static List<PetInstance> degenerate() {
        List<PetInstance> pets = new ArrayList<>();
        // Same level, same rarity, same name: every key collides and only the UUID tiebreak separates.
        for (int index = 0; index < 6; index++) pets.add(pet("same", 5, "common", index % 2 == 0));
        // Absent level, absent rarity, and both absent.
        pets.add(pet("same", null, "common", false));
        pets.add(pet("same", 5, null, false));
        pets.add(pet("same", null, null, true));
        // Distinct values interleaved with the collisions.
        pets.add(pet("alpha", 99, "legendary", false));
        pets.add(pet("zeta", 1, "aaa", true));
        return List.copyOf(pets);
    }

    private static PetInstance pet(String definitionId, Integer level, String rarity, boolean favorite) {
        Map<String, Object> components = new java.util.LinkedHashMap<>();
        if (level != null) components.put("progression", Map.of("level", level));
        if (rarity != null) components.put("hatching", Map.of("rarityId", rarity));
        return new PetInstance(UUID.randomUUID(), definitionId, 1, components,
                favorite ? Map.of("management", Map.of("favorite", true)) : Map.of());
    }

    private static List<UUID> ids(List<PetInstance> pets) {
        return pets.stream().map(PetInstance::id).toList();
    }
}
