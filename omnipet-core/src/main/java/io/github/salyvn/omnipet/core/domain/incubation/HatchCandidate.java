package io.github.salyvn.omnipet.core.domain.incubation;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;

public record HatchCandidate(String definitionId, double weight, Map<String, Object> extensions) {
    public HatchCandidate {
        definitionId = StableId.requireValid(definitionId);
        weight = IncubationValues.requireFinite(weight, "candidate weight");
        if (weight < 0) throw new IllegalArgumentException("candidate weight must be non-negative");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(extensions, "candidate[" + definitionId + "].extensions");
    }
}
