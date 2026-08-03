package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

/**
 * Walks a 120-pet vault through every sort and filter combination, page by page, asserting that no pet
 * is ever seen twice or lost. This is the plan's headline scale criterion, exercised end to end rather
 * than one comparator at a time.
 */
class VaultPagingWalkTest {
    @Test
    void everySortAndFilterCombinationPagesWithoutDuplicatingOrLosingAPet() {
        List<PetInstance> pets = pets(120);
        List<UUID> activeIds = List.of(pets.get(0).id(), pets.get(7).id(), pets.get(64).id());
        PetStorageSnapshot snapshot = new PetStorageSnapshot(
                UUID.randomUUID(), 1, pets, activeIds, 1000, 3);

        for (VaultSortOrder sort : VaultSortOrder.values()) {
            for (VaultFilter filter : VaultFilter.values()) {
                Set<UUID> seen = new HashSet<>();
                int expected = filter.apply(pets, Set.copyOf(activeIds)).size();
                int pages = VaultPetView.of(snapshot, new VaultViewState(sort, filter, 1), 45).pages();

                for (int page = 1; page <= pages; page++) {
                    VaultPetView view = VaultPetView.of(
                            snapshot, new VaultViewState(sort, filter, page), 45);
                    assertEquals(page, view.page(), sort + "/" + filter + " clamped page " + page);
                    for (PetInstance pet : view.pets()) {
                        assertTrue(seen.add(pet.id()),
                                sort + "/" + filter + " showed a pet twice on page " + page);
                    }
                }
                assertEquals(expected, seen.size(), sort + "/" + filter + " lost a pet across pages");
            }
        }
    }

    @Test
    void pagingForwardAndBackwardThroughTheHolderPreservesTheView() {
        List<PetInstance> pets = pets(120);
        PetStorageSnapshot snapshot = new PetStorageSnapshot(UUID.randomUUID(), 1, pets, List.of(), 1000, 3);
        VaultViewState state = new VaultViewState(VaultSortOrder.LEVEL_DESC, VaultFilter.STORED, 1);

        VaultPetView first = VaultPetView.of(snapshot, state, 45);
        VaultPetView second = VaultPetView.of(snapshot, state.withPage(2), 45);
        VaultPetView backAgain = VaultPetView.of(snapshot, state.withPage(2).withPage(1), 45);

        assertEquals(3, first.pages());
        assertEquals(ids(first), ids(backAgain), "paging away and back must show the same slice");
        assertTrue(java.util.Collections.disjoint(ids(first), ids(second)));
    }

    private static List<UUID> ids(VaultPetView view) {
        return view.pets().stream().map(PetInstance::id).toList();
    }

    private static List<PetInstance> pets(int size) {
        List<PetInstance> pets = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            Map<String, Object> components = index % 11 == 0
                    ? Map.of("hatching", Map.of("rarityId", "rarity-" + (index % 4)))
                    : Map.of("progression", Map.of("level", index % 30),
                            "hatching", Map.of("rarityId", "rarity-" + (index % 4)));
            pets.add(new PetInstance(UUID.randomUUID(), "pet-" + (index % 9), 1, components,
                    index % 5 == 0 ? Map.of("management", Map.of("favorite", true)) : Map.of()));
        }
        return List.copyOf(pets);
    }
}
