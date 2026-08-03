package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

/**
 * Paging, filtering, and ordering asserted on the pure function rather than through a rendered
 * inventory.
 *
 * <p>The comparator-stability tests are the most important in the phase. An inconsistent comparator
 * would let one pet land on two pages or on none, so a player would see a duplicate or lose a pet —
 * a real bug, not a cosmetic one.
 */
class VaultPetViewTest {
    @Test
    void everySortOrderIsTotalSoRepeatedSortsAgree() {
        List<PetInstance> pets = pets(40);

        for (VaultSortOrder order : VaultSortOrder.values()) {
            List<PetInstance> first = order.sort(pets);
            List<PetInstance> again = order.sort(first);
            List<PetInstance> fromShuffled = order.sort(shuffled(pets));

            assertEquals(ids(first), ids(again), order + " must be idempotent");
            if (order != VaultSortOrder.RECENT) {
                assertEquals(ids(first), ids(fromShuffled),
                        order + " must not depend on input order, or a pet drifts between pages");
            }
        }
    }

    @Test
    void everySortOrderKeepsEveryPetExactlyOnce() {
        List<PetInstance> pets = pets(40);

        for (VaultSortOrder order : VaultSortOrder.values()) {
            List<PetInstance> sorted = order.sort(pets);

            assertEquals(pets.size(), sorted.size(), order + " must not drop or duplicate a pet");
            assertEquals(new HashSet<>(ids(pets)), new HashSet<>(ids(sorted)), order.toString());
        }
    }

    @Test
    void tiesBreakOnPetIdSoNoPetAppearsOnTwoPagesOrNone() {
        // Twelve pets with identical level, rarity, and definition: every comparator falls through to
        // the tiebreak, which must still produce one deterministic order.
        List<PetInstance> identical = new ArrayList<>();
        for (int index = 0; index < 12; index++) identical.add(pet("same", 5, "common", false));

        for (VaultSortOrder order : VaultSortOrder.values()) {
            if (order == VaultSortOrder.RECENT) continue;
            List<UUID> expected = ids(order.sort(identical));

            assertEquals(expected, ids(order.sort(shuffled(identical))), order.toString());
            // Paged, the union of pages is still exactly the input with nothing repeated.
            Set<UUID> seen = new HashSet<>();
            int total = 0;
            for (int page = 1; page <= 3; page++) {
                VaultPetView view = VaultPetView.of(
                        snapshot(identical, List.of()), new VaultViewState(order, VaultFilter.ALL, page), 5);
                total += view.pets().size();
                view.pets().forEach(petInstance -> assertTrue(seen.add(petInstance.id()),
                        order + " showed the same pet on two pages"));
            }
            assertEquals(12, total, order + " lost a pet across pages");
        }
    }

    @Test
    void aPetWithNoReadableLevelSortsLastRatherThanAsLevelZero() {
        PetInstance high = pet("high", 90, "rare", false);
        PetInstance low = pet("low", 2, "common", false);
        PetInstance unknown = pet("unknown", null, null, false);

        List<PetInstance> sorted = VaultSortOrder.LEVEL_DESC.sort(List.of(unknown, low, high));

        // Absent data is unknown, not weakest; ranking it below a level-2 pet would be a claim we
        // cannot support.
        assertEquals(List.of(high.id(), low.id(), unknown.id()), ids(sorted));
    }

    @Test
    void favoritesSortAheadOfHigherLevelNonFavorites() {
        PetInstance favorite = pet("fav", 3, "common", true);
        PetInstance stronger = pet("strong", 99, "rare", false);

        List<PetInstance> sorted = VaultSortOrder.FAVORITES_FIRST.sort(List.of(stronger, favorite));

        assertEquals(favorite.id(), sorted.getFirst().id(),
                "a player who favorited a pet has said what they want on top");
    }

    @Test
    void nameSortIsCaseInsensitiveAndDeterministic() {
        PetInstance alpha = pet("Alpha", 1, "common", false);
        PetInstance beta = pet("beta", 1, "common", false);
        PetInstance gamma = pet("GAMMA", 1, "common", false);

        assertEquals(
                List.of(alpha.id(), beta.id(), gamma.id()),
                ids(VaultSortOrder.NAME_ASC.sort(List.of(gamma, beta, alpha))));
    }

    @Test
    void recentPreservesStorageOrderExactly() {
        List<PetInstance> pets = pets(10);

        assertEquals(ids(pets), ids(VaultSortOrder.RECENT.sort(pets)));
    }

    @Test
    void eachFilterSelectsTheRightPets() {
        PetInstance favoriteActive = pet("a", 1, "common", true);
        PetInstance plainActive = pet("b", 1, "common", false);
        PetInstance favoriteStored = pet("c", 1, "common", true);
        PetInstance plainStored = pet("d", 1, "common", false);
        List<PetInstance> all = List.of(favoriteActive, plainActive, favoriteStored, plainStored);
        List<UUID> active = List.of(favoriteActive.id(), plainActive.id());

        assertEquals(4, VaultFilter.ALL.apply(all, Set.copyOf(active)).size());
        assertEquals(Set.of(favoriteActive.id(), favoriteStored.id()),
                new HashSet<>(ids(VaultFilter.FAVORITES.apply(all, Set.copyOf(active)))));
        assertEquals(Set.of(favoriteActive.id(), plainActive.id()),
                new HashSet<>(ids(VaultFilter.ACTIVE.apply(all, Set.copyOf(active)))));
        assertEquals(Set.of(favoriteStored.id(), plainStored.id()),
                new HashSet<>(ids(VaultFilter.STORED.apply(all, Set.copyOf(active)))));
    }

    @Test
    void aPetWithNoManagementNodeIsSimplyNotAFavorite() {
        // A legacy pet predating the management extension must not throw here.
        PetInstance bare = new PetInstance(UUID.randomUUID(), "bare", 1, Map.of(), Map.of());

        assertFalse(VaultPetSummary.of(bare).favorite());
        assertEquals(0, VaultFilter.FAVORITES.apply(List.of(bare), Set.of()).size());
    }

    @Test
    void anEmptyVaultAndAZeroMatchFilterAreDistinguishableStates() {
        PetInstance stored = pet("stored", 1, "common", false);

        VaultPetView nothingOwned = VaultPetView.of(snapshot(List.of(), List.of()), VaultViewState.initial(), 45);
        VaultPetView nothingMatched = VaultPetView.of(
                snapshot(List.of(stored), List.of()),
                new VaultViewState(VaultSortOrder.RECENT, VaultFilter.FAVORITES, 1), 45);

        assertTrue(nothingOwned.empty());
        assertFalse(nothingOwned.noMatches());
        assertFalse(nothingMatched.empty());
        assertTrue(nothingMatched.noMatches(), "a filter that excludes everything is not an empty vault");
        assertNotEquals(nothingOwned.empty(), nothingMatched.empty());
    }

    @Test
    void pagesAreClampedAndTheLastPageKnowsItIsLast() {
        List<PetInstance> pets = pets(12);

        VaultPetView beyondEnd = VaultPetView.of(
                snapshot(pets, List.of()), new VaultViewState(VaultSortOrder.RECENT, VaultFilter.ALL, 99), 5);
        assertEquals(3, beyondEnd.pages());
        assertEquals(3, beyondEnd.page());
        assertTrue(beyondEnd.lastPage());
        assertEquals(2, beyondEnd.pets().size());

        VaultPetView first = VaultPetView.of(
                snapshot(pets, List.of()), new VaultViewState(VaultSortOrder.RECENT, VaultFilter.ALL, 1), 5);
        assertTrue(first.firstPage());
        assertFalse(first.lastPage());
        assertEquals(5, first.pets().size());
    }

    @Test
    void filteringChangesThePageCountAndTheMatchedCountReportsBoth() {
        List<PetInstance> plain = new ArrayList<>();
        for (int index = 0; index < 12; index++) plain.add(pet("plain-" + index, index, "common", false));
        plain.add(pet("favorite", 1, "common", true));

        VaultPetView filtered = VaultPetView.of(
                snapshot(plain, List.of()),
                new VaultViewState(VaultSortOrder.RECENT, VaultFilter.FAVORITES, 1), 5);

        assertEquals(1, filtered.matchedCount());
        assertEquals(13, filtered.ownedCount(), "the status row still reports the true owned total");
        assertEquals(1, filtered.pages());
    }

    @Test
    void cyclingSortOrFilterReturnsToPageOne() {
        VaultViewState onPageThree = new VaultViewState(VaultSortOrder.RECENT, VaultFilter.ALL, 3);

        // The pet under the cursor moves, so staying on page 3 would show an unrelated slice.
        assertEquals(1, onPageThree.cycleSort().page());
        assertEquals(1, onPageThree.cycleFilter().page());
        assertEquals(VaultSortOrder.RECENT.next(), onPageThree.cycleSort().sort());
        assertEquals(VaultFilter.ALL.next(), onPageThree.cycleFilter().filter());
    }

    @Test
    void cyclingWrapsAroundEveryOption() {
        VaultSortOrder sort = VaultSortOrder.FAVORITES_FIRST;
        for (int step = 0; step < VaultSortOrder.values().length; step++) sort = sort.next();
        assertEquals(VaultSortOrder.FAVORITES_FIRST, sort);

        VaultFilter filter = VaultFilter.ALL;
        for (int step = 0; step < VaultFilter.values().length; step++) filter = filter.next();
        assertEquals(VaultFilter.ALL, filter);
    }

    @Test
    void cyclingPreservesTheOtherAxis() {
        VaultViewState state = new VaultViewState(VaultSortOrder.NAME_ASC, VaultFilter.ACTIVE, 1);

        assertEquals(VaultFilter.ACTIVE, state.cycleSort().filter());
        assertEquals(VaultSortOrder.NAME_ASC, state.cycleFilter().sort());
    }

    @Test
    void theHolderCarriesViewStateForwardSoAReRenderCanRestoreIt() {
        VaultViewState state = new VaultViewState(VaultSortOrder.LEVEL_DESC, VaultFilter.STORED, 2);

        var holder = new PlayerPetInventoryHolder(UUID.randomUUID(), 4, state, Map.of());

        assertEquals(state, holder.view());
        assertEquals(2, holder.page());
    }

    @Test
    void theLegacyPageConstructorStillDefaultsToTheInitialView() {
        var holder = new PlayerPetInventoryHolder(UUID.randomUUID(), 4, 2, Map.of());

        assertEquals(VaultSortOrder.FAVORITES_FIRST, holder.view().sort());
        assertEquals(VaultFilter.ALL, holder.view().filter());
        assertEquals(2, holder.page());
    }

    private static PetStorageSnapshot snapshot(List<PetInstance> pets, List<UUID> active) {
        return new PetStorageSnapshot(UUID.randomUUID(), 1, pets, active, 1000, 3);
    }

    private static List<PetInstance> pets(int count) {
        List<PetInstance> pets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            pets.add(pet("pet-" + (index % 7), index % 5 == 0 ? null : index, "rarity-" + (index % 3),
                    index % 4 == 0));
        }
        return List.copyOf(pets);
    }

    private static PetInstance pet(String definitionId, Integer level, String rarity, boolean favorite) {
        Map<String, Object> components = new java.util.LinkedHashMap<>();
        if (level != null) components.put("progression", Map.of("level", level));
        if (rarity != null) components.put("hatching", Map.of("rarityId", rarity));
        Map<String, Object> extensions = favorite
                ? Map.of("management", Map.of("favorite", true))
                : Map.of();
        return new PetInstance(UUID.randomUUID(), definitionId, 1, components, extensions);
    }

    /** Deterministic reordering, so a failure reproduces rather than appearing at random. */
    private static List<PetInstance> shuffled(List<PetInstance> pets) {
        List<PetInstance> copy = new ArrayList<>(pets);
        Collections.rotate(copy, 3);
        Collections.reverse(copy.subList(0, Math.min(5, copy.size())));
        return List.copyOf(copy);
    }

    private static List<UUID> ids(List<PetInstance> pets) {
        return pets.stream().map(PetInstance::id).toList();
    }
}
