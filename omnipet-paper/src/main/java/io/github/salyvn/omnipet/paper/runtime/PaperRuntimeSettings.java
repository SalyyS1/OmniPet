package io.github.salyvn.omnipet.paper.runtime;

import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

public record PaperRuntimeSettings(
        long initialDelayTicks,
        long periodTicks,
        int maximumOwnersPerTick,
        int maximumPetsPerOwner) {
    public PaperRuntimeSettings {
        if (initialDelayTicks < 0) throw new IllegalArgumentException("initial delay cannot be negative");
        if (periodTicks < 1) throw new IllegalArgumentException("runtime period must be positive");
        if (maximumOwnersPerTick < 1) throw new IllegalArgumentException("owner budget must be positive");
        if (maximumPetsPerOwner < 1 || maximumPetsPerOwner > PetStorageLimits.MAX_ACTIVE_SLOT_COUNT) {
            throw new IllegalArgumentException("pet budget is outside the supported active-slot range");
        }
    }

    public static PaperRuntimeSettings defaults() {
        return new PaperRuntimeSettings(1, 1, 64, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT);
    }
}
