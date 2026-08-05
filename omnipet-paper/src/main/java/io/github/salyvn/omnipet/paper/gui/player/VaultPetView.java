package io.github.salyvn.omnipet.paper.gui.player;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

/**
 * Snapshot plus view state resolved into the one page of pets a renderer should draw.
 *
 * <p>A pure function with no Bukkit types, so paging, filtering, and ordering are unit-testable
 * directly rather than through a rendered inventory. Filtering and sorting run in memory over an
 * already-loaded snapshot: no repository read, no I/O, and only ever off a click — never per tick.
 */
public record VaultPetView(
        List<PetInstance> pets,
        int page,
        int pages,
        int matchedCount,
        int ownedCount) {

    public VaultPetView {
        pets = List.copyOf(pets);
    }

    public static VaultPetView of(PetStorageSnapshot snapshot, VaultViewState state, int petsPerPage) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(state, "vault view state");
        int size = Math.max(1, petsPerPage);
        // Copied to a set once: desiredActivePetIds() is a List, and a linear contains() per pet would
        // make the ACTIVE and STORED filters quadratic on a large vault.
        Set<UUID> active = Set.copyOf(snapshot.desiredActivePetIds());
        List<PetInstance> matched = state.sort().sort(state.filter().apply(snapshot.pets(), active));

        int pages = Math.max(1, (matched.size() + size - 1) / size);
        int page = Math.max(1, Math.min(state.page(), pages));
        int start = (page - 1) * size;
        int end = Math.min(start + size, matched.size());
        return new VaultPetView(
                start >= end ? List.of() : matched.subList(start, end),
                page,
                pages,
                matched.size(),
                snapshot.pets().size());
    }

    /** No pets at all. The player needs to be told how to get one, not shown an empty grid. */
    public boolean empty() {
        return ownedCount == 0;
    }

    /** Pets exist but the active filter excludes all of them — a different problem from {@link #empty()}. */
    public boolean noMatches() {
        return ownedCount > 0 && matchedCount == 0;
    }

    public boolean lastPage() {
        return page >= pages;
    }

    public boolean firstPage() {
        return page <= 1;
    }

    /**
     * Where a locked active slot belongs in the grid, or {@code -1} for nowhere on this page.
     *
     * <p>Computed here rather than configured, because the vault's slot layout is not an operator
     * setting: pet rows own 0-44 and the paging arithmetic is derived from that shape, so a tile at an
     * operator-chosen index would either overwrite a pet or drift out of the grid. Placing it directly
     * after the last pet on the last page is the only position that is always free and always adjacent
     * to what the player is looking at.
     *
     * <p>Nowhere when the grid is showing no pets — the tile's job is to sit beside the collection, and a
     * player who filtered everything out is being told something else. Nowhere too when the last page is
     * full, since the next free index is off the grid; the control row keeps the entry point in both
     * cases.
     */
    public int lockedSlotIndex(int gridSize) {
        if (gridSize <= 0 || !lastPage() || pets.isEmpty()) return -1;
        int index = pets.size();
        return index < gridSize ? index : -1;
    }
}
