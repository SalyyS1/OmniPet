package io.github.salyvn.omnipet.core.skill;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;

public record RepositorySkillActionResult(
        Status status,
        PlayerState state,
        PetInstance pet,
        SkillActionReservation reservation,
        String detail) {
    public enum Status {
        PREPARED,
        COMPLETED,
        ROLLED_BACK,
        ALREADY_PREPARED,
        PET_NOT_FOUND,
        COOLDOWN,
        INSUFFICIENT_STAMINA,
        ACTION_PENDING,
        ACTION_NOT_FOUND,
        CONFLICT
    }

    public boolean succeeded() {
        return status == Status.PREPARED || status == Status.COMPLETED || status == Status.ROLLED_BACK
                || status == Status.ALREADY_PREPARED;
    }
}
