package io.github.salyvn.omnipet.core.storage;

import io.github.salyvn.omnipet.core.domain.PlayerState;

/** Runtime limits that are combined with persisted per-player ownership counts. */
public record PetStorageLimits(
        int configuredVaultCapacity,
        int configuredActiveSlotFloor,
        int configuredActiveSlotCap,
        boolean multiPetEnabled) {
    public static final int MAX_VAULT_CAPACITY = PlayerState.MAX_VAULT_CAPACITY;
    public static final int MAX_ACTIVE_SLOT_COUNT = PlayerState.MAX_ACTIVE_SLOT_COUNT;

    public PetStorageLimits {
        if (configuredVaultCapacity < 0 || configuredVaultCapacity > MAX_VAULT_CAPACITY) {
            throw new IllegalArgumentException("configured vault capacity is outside the supported range");
        }
        if (configuredActiveSlotFloor < 1 || configuredActiveSlotFloor > MAX_ACTIVE_SLOT_COUNT) {
            throw new IllegalArgumentException("configured active slot floor is outside the supported range");
        }
        if (configuredActiveSlotCap < 1 || configuredActiveSlotCap > MAX_ACTIVE_SLOT_COUNT) {
            throw new IllegalArgumentException("configured active slot cap is outside the supported range");
        }
        if (configuredActiveSlotFloor > configuredActiveSlotCap) {
            throw new IllegalArgumentException("configured active slot floor cannot exceed the cap");
        }
    }

    public PetStorageLimits(int configuredVaultCapacity, int configuredActiveSlotCap, boolean multiPetEnabled) {
        this(configuredVaultCapacity, 1, configuredActiveSlotCap, multiPetEnabled);
    }

    public int configuredActiveSlotCount() {
        return configuredActiveSlotCap;
    }

    public int effectiveVaultCapacity(PlayerState state) {
        if (state == null) throw new IllegalArgumentException("player state is required");
        return Math.min(MAX_VAULT_CAPACITY, Math.max(state.vaultCapacity(), configuredVaultCapacity));
    }

    public int effectiveActiveSlotCount(PlayerState state) {
        if (state == null) throw new IllegalArgumentException("player state is required");
        if (!multiPetEnabled) return 1;
        return Math.min(configuredActiveSlotCap, Math.max(state.activeSlotCount(), configuredActiveSlotFloor));
    }
}
