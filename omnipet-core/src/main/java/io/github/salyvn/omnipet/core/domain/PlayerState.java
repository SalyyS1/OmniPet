package io.github.salyvn.omnipet.core.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;

public record PlayerState(
        UUID playerId,
        long revision,
        List<PetInstance> pets,
        int vaultCapacity,
        int activeSlotCount,
        List<UUID> desiredActivePetIds,
        List<SlotEntitlement> slotEntitlements,
        Map<String, Object> legacyCurrentEgg,
        IncubationState incubation,
        Map<String, Object> extensions) {
    public static final int MAX_VAULT_CAPACITY = 100_000;
    public static final int MAX_ACTIVE_SLOT_COUNT = SlotEntitlement.MAX_SLOT;

    public PlayerState {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        if (revision < 0) throw new IllegalArgumentException("player revision cannot be negative");
        pets = List.copyOf(pets == null ? List.of() : pets);
        Set<UUID> ownedPetIds = new HashSet<>(pets.stream().map(PetInstance::id).toList());
        if (ownedPetIds.size() != pets.size()) {
            throw new IllegalArgumentException("duplicate pet instance UUID");
        }
        if (vaultCapacity < 0 || vaultCapacity > MAX_VAULT_CAPACITY) {
            throw new IllegalArgumentException("vault capacity is outside the supported range");
        }
        if (activeSlotCount < 1 || activeSlotCount > MAX_ACTIVE_SLOT_COUNT) {
            throw new IllegalArgumentException("active slot count is outside the supported range");
        }
        desiredActivePetIds = List.copyOf(desiredActivePetIds == null ? List.of() : desiredActivePetIds);
        if (new HashSet<>(desiredActivePetIds).size() != desiredActivePetIds.size()) {
            throw new IllegalArgumentException("duplicate desired active pet UUID");
        }
        if (!ownedPetIds.containsAll(desiredActivePetIds)) {
            throw new IllegalArgumentException("desired active pet UUID is not owned by the player");
        }
        if (desiredActivePetIds.size() > activeSlotCount) {
            throw new IllegalArgumentException("desired active pets exceed the persisted active slot count");
        }
        slotEntitlements = List.copyOf(slotEntitlements == null ? List.of() : slotEntitlements);
        if (slotEntitlements.size() > MAX_ACTIVE_SLOT_COUNT - 1) {
            throw new IllegalArgumentException("too many slot entitlements");
        }
        if (new HashSet<>(slotEntitlements.stream().map(SlotEntitlement::slot).toList()).size()
                != slotEntitlements.size()) {
            throw new IllegalArgumentException("duplicate active slot entitlement");
        }
        if (slotEntitlements.stream().anyMatch(entitlement -> entitlement.slot() > activeSlotCount)) {
            throw new IllegalArgumentException("slot entitlement exceeds the persisted active slot count");
        }
        legacyCurrentEgg = RawNodeValues.immutableMap(legacyCurrentEgg == null ? Map.of() : legacyCurrentEgg);
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(legacyCurrentEgg, "currentEgg");
        RawNodeValues.rejectNonFinite(extensions, "extensions");
    }

    public PlayerState(
            UUID playerId,
            long revision,
            List<PetInstance> pets,
            int vaultCapacity,
            int activeSlotCount,
            List<UUID> desiredActivePetIds,
            List<SlotEntitlement> slotEntitlements,
            Map<String, Object> legacyCurrentEgg,
            Map<String, Object> extensions) {
        this(playerId, revision, pets, vaultCapacity, activeSlotCount, desiredActivePetIds,
                slotEntitlements, legacyCurrentEgg, null, extensions);
    }

    public static PlayerState empty(UUID playerId) {
        return new PlayerState(playerId, 0, List.of(), 0, 1, List.of(), List.of(), Map.of(), null, Map.of());
    }

    public PlayerState withRevision(long nextRevision) {
        return new PlayerState(
                playerId,
                nextRevision,
                pets,
                vaultCapacity,
                activeSlotCount,
                desiredActivePetIds,
                slotEntitlements,
                legacyCurrentEgg,
                incubation,
                extensions);
    }

    public PlayerState withStorage(
            List<PetInstance> nextPets,
            int nextVaultCapacity,
            int nextActiveSlotCount,
            List<UUID> nextDesiredActivePetIds,
            List<SlotEntitlement> nextSlotEntitlements) {
        return new PlayerState(
                playerId,
                revision,
                nextPets,
                nextVaultCapacity,
                nextActiveSlotCount,
                nextDesiredActivePetIds,
                nextSlotEntitlements,
                legacyCurrentEgg,
                incubation,
                extensions);
    }

    public PlayerState withIncubation(IncubationState nextIncubation) {
        return new PlayerState(
                playerId,
                revision,
                pets,
                vaultCapacity,
                activeSlotCount,
                desiredActivePetIds,
                slotEntitlements,
                legacyCurrentEgg,
                nextIncubation,
                extensions);
    }
}
