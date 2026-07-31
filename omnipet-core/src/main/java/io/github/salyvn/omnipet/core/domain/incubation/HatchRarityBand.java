package io.github.salyvn.omnipet.core.domain.incubation;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public record HatchRarityBand(
        String id,
        double qualityMinimum,
        double qualityMaximum,
        double weight,
        double hatchMultiplier,
        Map<String, Object> extensions) {
    public HatchRarityBand {
        id = IncubationValues.requireReference(id, "rarity id");
        qualityMinimum = IncubationValues.requireFinite(qualityMinimum, "rarity quality minimum");
        qualityMaximum = IncubationValues.requireFinite(qualityMaximum, "rarity quality maximum");
        weight = IncubationValues.requireFinite(weight, "rarity weight");
        hatchMultiplier = IncubationValues.requireFinite(hatchMultiplier, "hatch multiplier");
        if (qualityMinimum < 0 || qualityMaximum > 100 || qualityMinimum > qualityMaximum) {
            throw new IllegalArgumentException("rarity quality bounds must be ordered inside 0..100");
        }
        if (weight < 0) throw new IllegalArgumentException("rarity weight must be non-negative");
        if (hatchMultiplier <= 0) throw new IllegalArgumentException("hatch multiplier must be positive");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(extensions, "rarity[" + id + "].extensions");
    }
}
