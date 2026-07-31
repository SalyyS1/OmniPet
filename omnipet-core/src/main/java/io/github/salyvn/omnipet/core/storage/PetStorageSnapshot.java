package io.github.salyvn.omnipet.core.storage;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;

/** Immutable view of owned pets and persisted activation intent at one point in time. */
public record PetStorageSnapshot(
        UUID playerId,
        long revision,
        List<PetInstance> pets,
        List<UUID> desiredActivePetIds,
        int effectiveVaultCapacity,
        int effectiveActiveSlotCount) {
    public PetStorageSnapshot {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        if (revision < 0) throw new IllegalArgumentException("player revision cannot be negative");
        pets = List.copyOf(pets == null ? List.of() : pets);
        desiredActivePetIds = List.copyOf(desiredActivePetIds == null ? List.of() : desiredActivePetIds);
        if (effectiveVaultCapacity < 0 || effectiveVaultCapacity > PetStorageLimits.MAX_VAULT_CAPACITY) {
            throw new IllegalArgumentException("effective vault capacity is outside the supported range");
        }
        if (effectiveActiveSlotCount < 1 || effectiveActiveSlotCount > PetStorageLimits.MAX_ACTIVE_SLOT_COUNT) {
            throw new IllegalArgumentException("effective active slot count is outside the supported range");
        }
        if (new HashSet<>(desiredActivePetIds).size() != desiredActivePetIds.size()) {
            throw new IllegalArgumentException("duplicate desired active pet UUID");
        }
        HashSet<UUID> ownedIds = new HashSet<>(pets.stream().map(PetInstance::id).toList());
        if (!ownedIds.containsAll(desiredActivePetIds)) {
            throw new IllegalArgumentException("desired active pet is not owned");
        }
    }

    public static PetStorageSnapshot from(PlayerState state, PetStorageLimits limits) {
        if (state == null) throw new IllegalArgumentException("player state is required");
        if (limits == null) throw new IllegalArgumentException("storage limits are required");
        return new PetStorageSnapshot(
                state.playerId(),
                state.revision(),
                state.pets(),
                state.desiredActivePetIds(),
                limits.effectiveVaultCapacity(state),
                limits.effectiveActiveSlotCount(state));
    }

    public int ownedCount() {
        return pets.size();
    }

    public int vaultOverflow() {
        return Math.max(0, pets.size() - effectiveVaultCapacity);
    }

    public boolean vaultAtCapacity() {
        return pets.size() >= effectiveVaultCapacity;
    }

    public boolean activeAtCapacity() {
        return desiredActivePetIds.size() >= effectiveActiveSlotCount;
    }
}
