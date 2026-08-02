package io.github.salyvn.omnipet.core.progression;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public record ProgressionState(
        int level,
        double experience,
        int evolution,
        double stamina,
        long lastStaminaEpochMillis,
        Map<String, Object> extensions) {
    public ProgressionState {
        if (level <= 0) throw new IllegalArgumentException("progression level must be positive");
        if (evolution < 0) throw new IllegalArgumentException("progression evolution cannot be negative");
        if (!Double.isFinite(experience) || experience < 0) {
            throw new IllegalArgumentException("progression experience must be finite and non-negative");
        }
        if (!Double.isFinite(stamina) || stamina < 0) {
            throw new IllegalArgumentException("progression stamina must be finite and non-negative");
        }
        if (lastStaminaEpochMillis < 0) throw new IllegalArgumentException("progression stamina time cannot be negative");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
    }

    public static ProgressionState initial(double stamina, long now) {
        return new ProgressionState(1, 0, 0, stamina, now, Map.of());
    }

    public ProgressionState withValues(int nextLevel, double nextExperience, int nextEvolution, double nextStamina,
                                       long nextStaminaTime) {
        return new ProgressionState(nextLevel, nextExperience, nextEvolution, nextStamina,
                nextStaminaTime, extensions);
    }
}
