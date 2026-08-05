package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

/**
 * The locked-slot tile shares the pet grid, so the one defect that matters is it landing on a pet.
 *
 * <p>Walked across every page count and every sort/filter combination rather than spot-checked, because
 * the index is derived from the page's own size and the interesting cases are the boundaries: a page that
 * exactly fills the grid, a page with one free square left, and the pages before the last.
 */
class VaultLockedSlotPlacementTest {
    private static final int GRID = 45;

    @Test
    void theTileNeverLandsOnAPetAtAnyVaultSize() {
        for (int owned = 1; owned <= 140; owned++) {
            PetStorageSnapshot snapshot = snapshot(owned);
            int pages = VaultPetView.of(snapshot, VaultViewState.initial(), GRID).pages();
            for (int page = 1; page <= pages; page++) {
                VaultPetView view = VaultPetView.of(
                        snapshot, VaultViewState.page(page), GRID);
                int slot = view.lockedSlotIndex(GRID);
                if (slot < 0) continue;
                assertTrue(slot >= view.pets().size(),
                        "owned=" + owned + " page=" + page + " tile overwrote a pet at " + slot);
                assertTrue(slot < GRID,
                        "owned=" + owned + " page=" + page + " tile escaped the grid at " + slot);
            }
        }
    }

    @Test
    void theTileSitsImmediatelyAfterTheLastPetOnTheLastPage() {
        PetStorageSnapshot snapshot = snapshot(7);
        VaultPetView view = VaultPetView.of(snapshot, VaultViewState.initial(), GRID);

        assertEquals(7, view.lockedSlotIndex(GRID));
    }

    /** Only the last page: an earlier page's grid is full, and its free square belongs to the next page. */
    @Test
    void pagesBeforeTheLastCarryNoTile() {
        PetStorageSnapshot snapshot = snapshot(100);

        assertEquals(-1, VaultPetView.of(snapshot, VaultViewState.page(1), GRID).lockedSlotIndex(GRID));
        assertEquals(-1, VaultPetView.of(snapshot, VaultViewState.page(2), GRID).lockedSlotIndex(GRID));
        assertEquals(10, VaultPetView.of(snapshot, VaultViewState.page(3), GRID).lockedSlotIndex(GRID));
    }

    /**
     * A last page that exactly fills the grid has no free square, so the tile goes nowhere and the
     * control-row button remains the entry point.
     */
    @Test
    void aFullLastPageCarriesNoTile() {
        VaultPetView view = VaultPetView.of(snapshot(GRID), VaultViewState.initial(), GRID);

        assertTrue(view.lastPage());
        assertEquals(GRID, view.pets().size());
        assertEquals(-1, view.lockedSlotIndex(GRID));
    }

    /** An empty grid is telling the player something else; the tile would compete with that message. */
    @Test
    void anEmptyVaultCarriesNoTile() {
        PetStorageSnapshot snapshot = new PetStorageSnapshot(
                UUID.randomUUID(), 1, List.of(), List.of(), 100, 3);

        assertEquals(-1, VaultPetView.of(snapshot, VaultViewState.initial(), GRID).lockedSlotIndex(GRID));
    }

    @Test
    void aFilterThatMatchesNothingCarriesNoTile() {
        PetStorageSnapshot snapshot = snapshot(20);
        VaultPetView view = VaultPetView.of(
                snapshot, new VaultViewState(VaultSortOrder.FAVORITES_FIRST, VaultFilter.ACTIVE, 1), GRID);

        assertTrue(view.noMatches());
        assertEquals(-1, view.lockedSlotIndex(GRID));
    }

    @Test
    void everySortAndFilterCombinationPlacesTheTileOffThePets() {
        PetStorageSnapshot snapshot = snapshot(60);
        Set<UUID> active = Set.copyOf(snapshot.desiredActivePetIds());

        for (VaultSortOrder sort : VaultSortOrder.values()) {
            for (VaultFilter filter : VaultFilter.values()) {
                int matched = filter.apply(snapshot.pets(), active).size();
                int pages = VaultPetView.of(snapshot, new VaultViewState(sort, filter, 1), GRID).pages();
                VaultPetView last = VaultPetView.of(snapshot, new VaultViewState(sort, filter, pages), GRID);
                int slot = last.lockedSlotIndex(GRID);
                if (matched == 0 || last.pets().size() == GRID) {
                    assertEquals(-1, slot, sort + "/" + filter + " should carry no tile");
                    continue;
                }
                assertEquals(last.pets().size(), slot, sort + "/" + filter + " misplaced the tile");
            }
        }
    }

    /** A smaller configured page size still keeps the tile inside the grid it was given. */
    @Test
    void aSmallerPageSizeStillPlacesTheTileWithinThatPage() {
        PetStorageSnapshot snapshot = snapshot(13);
        VaultPetView view = VaultPetView.of(snapshot, VaultViewState.page(3), 5);

        assertEquals(3, view.pets().size());
        assertEquals(3, view.lockedSlotIndex(GRID));
    }

    private static PetStorageSnapshot snapshot(int owned) {
        List<PetInstance> pets = new ArrayList<>();
        for (int index = 0; index < owned; index++) {
            pets.add(new PetInstance(UUID.randomUUID(), "pet-" + index, 1, Map.of(), Map.of()));
        }
        return new PetStorageSnapshot(UUID.randomUUID(), 1, pets, List.of(), 1000, 3);
    }
}
