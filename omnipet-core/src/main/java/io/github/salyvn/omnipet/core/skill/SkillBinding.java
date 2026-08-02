package io.github.salyvn.omnipet.core.skill;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import io.github.salyvn.omnipet.core.domain.StableId;

public record SkillBinding(
        String bindingId,
        String provider,
        String skillId,
        SkillTrigger trigger,
        Duration cooldown,
        double chance,
        double staminaCost,
        SkillTargetPolicy targetPolicy,
        boolean persistCooldown) {
    public SkillBinding {
        bindingId = StableId.requireValid(bindingId);
        provider = require(provider, "skill provider").toUpperCase(Locale.ROOT);
        skillId = require(skillId, "skill ID");
        trigger = Objects.requireNonNull(trigger, "skill trigger");
        cooldown = cooldown == null ? Duration.ZERO : cooldown;
        if (cooldown.isNegative()) throw new IllegalArgumentException("skill cooldown cannot be negative");
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("skill chance must be in [0,1]");
        }
        if (!Double.isFinite(staminaCost) || staminaCost < 0) {
            throw new IllegalArgumentException("skill stamina cost must be finite and non-negative");
        }
        targetPolicy = Objects.requireNonNull(targetPolicy, "skill target policy");
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
