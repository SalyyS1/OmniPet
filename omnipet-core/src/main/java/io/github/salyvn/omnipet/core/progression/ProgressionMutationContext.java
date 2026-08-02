package io.github.salyvn.omnipet.core.progression;

import java.util.Map;
import java.util.Objects;

public record ProgressionMutationContext(
        ProgressionConfig config,
        String petFormula,
        Map<String, Double> formulaValues,
        double initialStamina,
        long nowEpochMillis) {
    public ProgressionMutationContext {
        config = Objects.requireNonNull(config, "progression config");
        formulaValues = Map.copyOf(formulaValues == null ? Map.of() : formulaValues);
        if (!Double.isFinite(initialStamina) || initialStamina < 0 || initialStamina > config.maxStamina()) {
            throw new IllegalArgumentException("initial stamina is outside the configured range");
        }
        if (nowEpochMillis < 0) throw new IllegalArgumentException("progression time cannot be negative");
    }
}
