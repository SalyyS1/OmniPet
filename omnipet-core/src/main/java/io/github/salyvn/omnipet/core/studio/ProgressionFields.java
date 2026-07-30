package io.github.salyvn.omnipet.core.studio;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

/** Bounded progression fields retained without a vendor expression engine. */
public record ProgressionFields(int maxLevel, String experienceFormula, Map<String, Double> samples,
                                Map<String, Object> extensions) {
    public ProgressionFields {
        if (maxLevel <= 0) throw new IllegalArgumentException("progression max level must be positive");
        if (experienceFormula != null && experienceFormula.isBlank()) {
            throw new IllegalArgumentException("progression formula cannot be blank");
        }
        samples = samples == null ? Map.of() : Map.copyOf(samples);
        samples.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("progression samples must be finite named values");
            }
        });
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
    }
}
