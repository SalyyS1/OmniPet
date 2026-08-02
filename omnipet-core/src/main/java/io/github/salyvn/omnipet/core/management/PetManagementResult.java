package io.github.salyvn.omnipet.core.management;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;

public record PetManagementResult(Status status, PlayerState state, PetInstance pet, String detail) {
    public enum Status {
        PERSISTED,
        PET_NOT_FOUND,
        INVALID_MOVE,
        REJECTED
    }

    public boolean succeeded() {
        return status == Status.PERSISTED;
    }
}
