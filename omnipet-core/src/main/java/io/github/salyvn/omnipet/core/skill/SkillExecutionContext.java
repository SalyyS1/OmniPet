package io.github.salyvn.omnipet.core.skill;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public record SkillExecutionContext(
        UUID actionId,
        UUID actorId,
        UUID ownerId,
        UUID petInstanceId,
        long nowEpochMillis,
        double stamina,
        double chanceRoll,
        Map<String, Long> cooldownDeadlines,
        Map<String, Object> providerContext) {
    public SkillExecutionContext {
        actionId = Objects.requireNonNull(actionId, "skill action ID");
        actorId = Objects.requireNonNull(actorId, "skill actor ID");
        ownerId = Objects.requireNonNull(ownerId, "skill owner ID");
        petInstanceId = Objects.requireNonNull(petInstanceId, "skill pet instance ID");
        if (nowEpochMillis < 0) throw new IllegalArgumentException("skill time cannot be negative");
        if (!Double.isFinite(stamina) || stamina < 0) {
            throw new IllegalArgumentException("skill stamina must be finite and non-negative");
        }
        if (!Double.isFinite(chanceRoll) || chanceRoll < 0 || chanceRoll >= 1) {
            throw new IllegalArgumentException("skill chance roll must be in [0,1)");
        }
        cooldownDeadlines = Map.copyOf(cooldownDeadlines == null ? Map.of() : cooldownDeadlines);
        cooldownDeadlines.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value < 0) {
                throw new IllegalArgumentException("skill cooldown deadlines are invalid");
            }
        });
        providerContext = RawNodeValues.immutableMap(providerContext == null ? Map.of() : providerContext);
    }
}
