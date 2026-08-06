package io.github.salyvn.omnipet.core.skill;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;

/**
 * The outcome of a durable skill action step.
 *
 * @param cooldownRemainingMillis how much longer the binding is cooling down, for a
 *     {@link Status#COOLDOWN} rejection, and zero otherwise. Carried here because the refusal is the only
 *     place that knows it: a player told "skill is cooling down" with no number cannot tell a one-second
 *     wait from a one-minute one, and asking again is the only way to find out.
 */
public record RepositorySkillActionResult(
        Status status,
        PlayerState state,
        PetInstance pet,
        SkillActionReservation reservation,
        String detail,
        long cooldownRemainingMillis) {
    public RepositorySkillActionResult {
        if (cooldownRemainingMillis < 0) cooldownRemainingMillis = 0;
    }

    /** An outcome with nothing to say about cooldown, which is every outcome but a cooldown refusal. */
    public RepositorySkillActionResult(
            Status status, PlayerState state, PetInstance pet, SkillActionReservation reservation, String detail) {
        this(status, state, pet, reservation, detail, 0);
    }

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
