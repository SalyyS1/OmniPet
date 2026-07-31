package io.github.salyvn.omnipet.core.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;

/** Pure storage and activation-intent boundary; callers persist returned state under their UUID lock. */
public final class PetStorageService {
    public PetStorageSnapshot snapshot(PlayerState state, PetStorageLimits limits) {
        return PetStorageSnapshot.from(requireState(state), requireLimits(limits));
    }

    public PetStorageResult admit(PlayerState state, PetInstance pet, PetStorageLimits limits) {
        PetStorageLimits policy = requireLimits(limits);
        PlayerState original = requireState(state);
        PlayerState current = normalizeActiveFloor(original, policy);
        Objects.requireNonNull(pet, "pet");
        PetStorageSnapshot before = snapshot(current, policy);
        if (current.pets().stream().anyMatch(existing -> existing.id().equals(pet.id()))) {
            return result(PetStorageResult.Status.DUPLICATE_PET_ID, original, policy, List.of(), null);
        }
        if (before.vaultAtCapacity()) {
            return result(PetStorageResult.Status.VAULT_CAPACITY_REACHED, original, policy, List.of(), null);
        }

        List<PetInstance> pets = new ArrayList<>(current.pets());
        pets.add(pet);
        PlayerState next = current.withStorage(
                pets,
                current.vaultCapacity(),
                current.activeSlotCount(),
                current.desiredActivePetIds(),
                current.slotEntitlements());
        return result(PetStorageResult.Status.ADMITTED, next, policy, List.of(), null);
    }

    public PetStorageResult activate(PlayerState state, UUID petId, PetStorageLimits limits) {
        PetStorageLimits policy = requireLimits(limits);
        PlayerState original = requireState(state);
        PlayerState current = normalizeActiveFloor(original, policy);
        Objects.requireNonNull(petId, "petId");
        PetStorageSnapshot before = snapshot(current, policy);
        if (!owns(current, petId)) {
            return result(PetStorageResult.Status.PET_NOT_OWNED, original, policy, List.of(), null);
        }
        if (current.desiredActivePetIds().contains(petId)) {
            return result(PetStorageResult.Status.PET_ALREADY_ACTIVE, original, policy, List.of(), null);
        }
        if (before.activeAtCapacity()) {
            return result(PetStorageResult.Status.ACTIVE_SLOT_CAPACITY_REACHED, original, policy, List.of(), null);
        }

        List<UUID> desired = new ArrayList<>(current.desiredActivePetIds());
        desired.add(petId);
        PlayerState next = current.withStorage(
                current.pets(),
                current.vaultCapacity(),
                current.activeSlotCount(),
                desired,
                current.slotEntitlements());
        return result(PetStorageResult.Status.ACTIVATED, next, policy, List.of(), null);
    }

    public PetStorageResult deactivate(PlayerState state, UUID petId, PetStorageLimits limits) {
        PetStorageLimits policy = requireLimits(limits);
        PlayerState original = requireState(state);
        PlayerState current = normalizeActiveFloor(original, policy);
        Objects.requireNonNull(petId, "petId");
        if (!owns(current, petId)) {
            return result(PetStorageResult.Status.PET_NOT_OWNED, original, policy, List.of(), null);
        }
        if (!current.desiredActivePetIds().contains(petId)) {
            return result(PetStorageResult.Status.PET_NOT_ACTIVE, original, policy, List.of(), null);
        }

        List<UUID> desired = new ArrayList<>(current.desiredActivePetIds());
        desired.remove(petId);
        PlayerState next = current.withStorage(
                current.pets(),
                current.vaultCapacity(),
                current.activeSlotCount(),
                desired,
                current.slotEntitlements());
        return result(PetStorageResult.Status.DEACTIVATED, next, policy, List.of(petId), null);
    }

    public PetStorageResult remove(PlayerState state, UUID petId, PetStorageLimits limits) {
        PetStorageLimits policy = requireLimits(limits);
        PlayerState original = requireState(state);
        PlayerState current = normalizeActiveFloor(original, policy);
        Objects.requireNonNull(petId, "petId");
        PetInstance removed = current.pets().stream()
                .filter(pet -> pet.id().equals(petId))
                .findFirst()
                .orElse(null);
        if (removed == null) {
            return result(PetStorageResult.Status.PET_NOT_OWNED, original, policy, List.of(), null);
        }

        List<PetInstance> pets = current.pets().stream()
                .filter(pet -> !pet.id().equals(petId))
                .toList();
        List<UUID> desired = current.desiredActivePetIds().stream()
                .filter(id -> !id.equals(petId))
                .toList();
        List<UUID> recalled = current.desiredActivePetIds().contains(petId) ? List.of(petId) : List.of();
        PlayerState next = current.withStorage(
                pets,
                current.vaultCapacity(),
                current.activeSlotCount(),
                desired,
                current.slotEntitlements());
        return result(PetStorageResult.Status.REMOVED, next, policy, recalled, removed);
    }

    /** Trims activation intent when config or multi-pet mode changes, without deleting pets or entitlements. */
    public PetStorageResult reconcileLimits(PlayerState state, PetStorageLimits limits) {
        PlayerState current = requireState(state);
        PetStorageLimits policy = requireLimits(limits);
        PlayerState normalized = normalizeActiveFloor(current, policy);
        int allowed = policy.effectiveActiveSlotCount(normalized);
        List<UUID> desired = normalized.desiredActivePetIds();
        if (desired.size() <= allowed) {
            return result(PetStorageResult.Status.LIMITS_RECONCILED, normalized, policy, List.of(), null);
        }

        List<UUID> retained = List.copyOf(desired.subList(0, allowed));
        List<UUID> recalled = List.copyOf(desired.subList(allowed, desired.size()));
        PlayerState next = normalized.withStorage(
                normalized.pets(),
                normalized.vaultCapacity(),
                normalized.activeSlotCount(),
                retained,
                normalized.slotEntitlements());
        return result(PetStorageResult.Status.LIMITS_RECONCILED, next, policy, recalled, null);
    }

    private static PetStorageResult result(
            PetStorageResult.Status status,
            PlayerState state,
            PetStorageLimits limits,
            List<UUID> recalled,
            PetInstance removed) {
        return new PetStorageResult(status, state, PetStorageSnapshot.from(state, limits), recalled, removed);
    }

    private static boolean owns(PlayerState state, UUID petId) {
        return state.pets().stream().anyMatch(pet -> pet.id().equals(petId));
    }

    private static PlayerState normalizeActiveFloor(PlayerState state, PetStorageLimits limits) {
        int activeSlots = Math.max(state.activeSlotCount(), limits.configuredActiveSlotFloor());
        if (activeSlots == state.activeSlotCount()) return state;
        return state.withStorage(
                state.pets(),
                state.vaultCapacity(),
                activeSlots,
                state.desiredActivePetIds(),
                state.slotEntitlements());
    }

    private static PlayerState requireState(PlayerState state) {
        return Objects.requireNonNull(state, "player state");
    }

    private static PetStorageLimits requireLimits(PetStorageLimits limits) {
        return Objects.requireNonNull(limits, "storage limits");
    }
}
