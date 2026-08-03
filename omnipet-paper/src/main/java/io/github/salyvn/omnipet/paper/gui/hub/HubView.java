package io.github.salyvn.omnipet.paper.gui.hub;

import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

/**
 * Everything the hub shows, derived from one {@link PlayerState} read.
 *
 * <p>{@code RepositoryHatchService.snapshot} already returns the full player state, which carries
 * pets, desired-active IDs, entitlements, and incubation. Deriving the storage view with
 * {@link PetStorageSnapshot#from} — the same pure factory the vault uses — means the hub needs no
 * second repository call and no new API.
 *
 * @param viewerId the player the hub was opened for
 * @param storage the vault and slot view
 * @param incubation the current incubation, or {@code null} when none is active
 */
public record HubView(UUID viewerId, PetStorageSnapshot storage,
                      io.github.salyvn.omnipet.core.domain.incubation.IncubationState incubation) {

    public HubView {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(storage, "storage snapshot");
    }

    /** Derives the hub view from one player-state read. */
    public static HubView from(PlayerState state, PetStorageLimits limits) {
        Objects.requireNonNull(state, "player state");
        Objects.requireNonNull(limits, "storage limits");
        return new HubView(state.playerId(), PetStorageSnapshot.from(state, limits), state.incubation());
    }
}
