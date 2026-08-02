package io.github.salyvn.omnipet.core.progression;

import java.util.Map;
import java.util.Objects;

public record ProgressionConfig(
        int maxLevel,
        double maxStamina,
        double staminaRegenPerSecond,
        ExperienceFormula defaultFormula,
        Map<String, Double> formulaSamples,
        OverflowPolicy overflowPolicy) {
    public ProgressionConfig {
        if (maxLevel <= 0) throw new IllegalArgumentException("progression max level must be positive");
        if (!Double.isFinite(maxStamina) || maxStamina < 0) throw new IllegalArgumentException("maximum stamina is invalid");
        if (!Double.isFinite(staminaRegenPerSecond) || staminaRegenPerSecond < 0) {
            throw new IllegalArgumentException("stamina regeneration is invalid");
        }
        defaultFormula = Objects.requireNonNull(defaultFormula, "default progression formula");
        formulaSamples = Map.copyOf(formulaSamples == null ? Map.of() : formulaSamples);
        overflowPolicy = Objects.requireNonNull(overflowPolicy, "progression overflow policy");
    }

    public enum OverflowPolicy {
        DISCARD,
        CARRY
    }
}
