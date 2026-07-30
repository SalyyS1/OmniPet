package io.github.salyvn.omnipet.core.domain;

import java.util.Map;

public record PetDefinition(
        String id,
        long revision,
        PetTier tier,
        HeadIcon icon,
        DisplayDefinition display,
        Map<String, Object> rawNode) {
    public PetDefinition {
        id = StableId.requireValid(id);
        if (revision < 0) throw new IllegalArgumentException("definition revision cannot be negative");
        if (tier == null) throw new IllegalArgumentException("classification.tier is required");
        if (icon == null) throw new IllegalArgumentException("icon.head is required");
        if (display == null) throw new IllegalArgumentException("display is required");
        rawNode = RawNodeValues.immutableMap(rawNode == null ? Map.of() : rawNode);
        RawNodeValues.rejectNonFinite(rawNode, id);
    }
}
