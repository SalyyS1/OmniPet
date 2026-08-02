package io.github.salyvn.omnipet.core.skill;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public record SkillCastRequest(
        UUID actionId,
        UUID actorId,
        UUID ownerId,
        UUID petInstanceId,
        String skillId,
        SkillTargetPolicy targetPolicy,
        Map<String, Object> context) {
    public SkillCastRequest {
        actionId = Objects.requireNonNull(actionId, "skill action ID");
        actorId = Objects.requireNonNull(actorId, "skill actor ID");
        ownerId = Objects.requireNonNull(ownerId, "skill owner ID");
        petInstanceId = Objects.requireNonNull(petInstanceId, "skill pet instance ID");
        if (skillId == null || skillId.isBlank()) throw new IllegalArgumentException("skill ID is required");
        targetPolicy = Objects.requireNonNull(targetPolicy, "skill target policy");
        context = RawNodeValues.immutableMap(context == null ? Map.of() : context);
    }
}
