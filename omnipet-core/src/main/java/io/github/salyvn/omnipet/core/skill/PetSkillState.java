package io.github.salyvn.omnipet.core.skill;

import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public record PetSkillState(
        Map<String, Long> cooldownDeadlines,
        Map<UUID, SkillActionReservation> pendingActions,
        Map<String, Object> extensions) {
    public static final int MAX_PENDING_ACTIONS = 32;

    public PetSkillState {
        cooldownDeadlines = Map.copyOf(cooldownDeadlines == null ? Map.of() : cooldownDeadlines);
        pendingActions = Map.copyOf(pendingActions == null ? Map.of() : pendingActions);
        if (pendingActions.size() > MAX_PENDING_ACTIONS) {
            throw new IllegalArgumentException("too many pending skill actions");
        }
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
    }

    public static PetSkillState empty() {
        return new PetSkillState(Map.of(), Map.of(), Map.of());
    }
}
