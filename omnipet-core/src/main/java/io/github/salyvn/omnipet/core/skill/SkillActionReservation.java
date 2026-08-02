package io.github.salyvn.omnipet.core.skill;

import java.util.Objects;
import java.util.UUID;

public record SkillActionReservation(
        UUID actionId,
        String bindingId,
        double staminaCost,
        long cooldownDeadline,
        long createdAtEpochMillis,
        boolean persistCooldown) {
    public SkillActionReservation {
        actionId = Objects.requireNonNull(actionId, "skill action ID");
        if (bindingId == null || bindingId.isBlank()) throw new IllegalArgumentException("skill binding ID is required");
        if (!Double.isFinite(staminaCost) || staminaCost < 0) {
            throw new IllegalArgumentException("reserved skill stamina is invalid");
        }
        if (cooldownDeadline < 0 || createdAtEpochMillis < 0) {
            throw new IllegalArgumentException("skill reservation time is invalid");
        }
    }
}
