package io.github.salyvn.omnipet.core.storage;

import java.util.List;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;

/** Structured result for a storage mutation, including safe recalls and the resulting immutable state. */
public record PetStorageResult(
        Status status,
        PlayerState state,
        PetStorageSnapshot snapshot,
        List<UUID> recalledPetIds,
        PetInstance removedPet) {
    public PetStorageResult {
        if (status == null) throw new IllegalArgumentException("storage result status is required");
        if (state == null) throw new IllegalArgumentException("storage result state is required");
        if (snapshot == null) throw new IllegalArgumentException("storage result snapshot is required");
        recalledPetIds = List.copyOf(recalledPetIds == null ? List.of() : recalledPetIds);
        if (removedPet != null && status != Status.REMOVED) {
            throw new IllegalArgumentException("removed pet is only valid for a removal result");
        }
    }

    public boolean succeeded() {
        return switch (status) {
            case ADMITTED, ACTIVATED, DEACTIVATED, REMOVED, LIMITS_RECONCILED -> true;
            default -> false;
        };
    }

    public enum Status {
        ADMITTED,
        ACTIVATED,
        DEACTIVATED,
        REMOVED,
        LIMITS_RECONCILED,
        DUPLICATE_PET_ID,
        PET_NOT_OWNED,
        PET_ALREADY_ACTIVE,
        PET_NOT_ACTIVE,
        VAULT_CAPACITY_REACHED,
        ACTIVE_SLOT_CAPACITY_REACHED
    }
}
