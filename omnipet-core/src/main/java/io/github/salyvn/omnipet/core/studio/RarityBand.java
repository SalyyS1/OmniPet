package io.github.salyvn.omnipet.core.studio;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

/** One weighted quality interval in a rarity profile. */
public record RarityBand(String id, double qualityMinimum, double qualityMaximum, double weight,
                         Map<String, Object> extensions) {
    public RarityBand {
        id = StudioStat.requireReference(id, "rarity band id");
        if (!Double.isFinite(qualityMinimum) || !Double.isFinite(qualityMaximum)
                || qualityMinimum > qualityMaximum) {
            throw new IllegalArgumentException("rarity quality bounds must be finite and ordered");
        }
        if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("rarity weight must be non-negative");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
    }
}
