package io.github.salyvn.omnipet.core.skill;

import java.util.Map;

public record SkillExecutionResult(
        Status status,
        double stamina,
        Map<String, Long> cooldownDeadlines,
        SkillCastResult providerResult,
        String detail) {
    public SkillExecutionResult {
        if (status == null) throw new IllegalArgumentException("skill execution status is required");
        if (!Double.isFinite(stamina) || stamina < 0) {
            throw new IllegalArgumentException("skill result stamina must be finite and non-negative");
        }
        cooldownDeadlines = Map.copyOf(cooldownDeadlines == null ? Map.of() : cooldownDeadlines);
        detail = detail == null ? "" : detail;
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        COOLDOWN,
        PRECONDITION_FALSE,
        CHANCE_MISS,
        INSUFFICIENT_RESOURCE,
        PROVIDER_UNAVAILABLE,
        INVALID_SKILL,
        FAILED
    }
}
