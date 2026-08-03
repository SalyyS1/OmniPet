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
}
