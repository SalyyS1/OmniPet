package io.github.salyvn.omnipet.core.domain;

import java.util.Map;
import java.util.UUID;

public record PetInstance(
        UUID id,
        String definitionId,
        long definitionRevision,
        Map<String, Object> rawComponents,
        Map<String, Object> extensions) {
    public PetInstance {
        if (id == null) throw new IllegalArgumentException("pet instance id is required");
        definitionId = StableId.requireValid(definitionId);
        if (definitionRevision < 0) throw new IllegalArgumentException("definition revision cannot be negative");
        rawComponents = RawNodeValues.immutableMap(rawComponents == null ? Map.of() : rawComponents);
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(rawComponents, "pet[" + id + "].components");
        RawNodeValues.rejectNonFinite(extensions, "pet[" + id + "].extensions");
    }
}
