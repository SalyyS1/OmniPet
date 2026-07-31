package io.github.salyvn.omnipet.core.incubation;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;

public record HatchResult(
        Status status,
        PlayerState state,
        IncubationState incubation,
        PetInstance claimedPet) {
    public enum Status {
        STARTED,
        TICKED,
        REDUCED,
        REMAINING_SET,
        COMPLETED,
        CANCELLED,
        CLAIMED,
        ALREADY_APPLIED,
        ALREADY_READY,
        ALREADY_CLAIMED,
        NO_INCUBATION,
        INCUBATION_ID_MISMATCH,
        ALREADY_INCUBATING,
        INCUBATION_ID_REUSED,
        LEGACY_MIGRATION_REQUIRED,
        ACTION_TOKEN_CAPACITY_REACHED,
        INVALID_STATE,
        VAULT_CAPACITY_REACHED
    }

    public HatchResult {
        if (status == null) throw new IllegalArgumentException("hatch result status is required");
        if (state == null) throw new IllegalArgumentException("player state is required");
    }

    public boolean changedFrom(PlayerState previous) {
        return !state.equals(previous);
    }

    public boolean succeeded() {
        return switch (status) {
            case STARTED, TICKED, REDUCED, REMAINING_SET, COMPLETED, CANCELLED, CLAIMED,
                    ALREADY_APPLIED, ALREADY_READY, ALREADY_CLAIMED -> true;
            default -> false;
        };
    }
}
