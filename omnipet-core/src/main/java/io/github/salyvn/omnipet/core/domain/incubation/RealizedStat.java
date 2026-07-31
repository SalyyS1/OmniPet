package io.github.salyvn.omnipet.core.domain.incubation;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.studio.StatModifierType;

public record RealizedStat(
        String id,
        StatModifierType modifierType,
        double value,
        Map<String, Object> extensions) {
    public RealizedStat {
        id = IncubationValues.requireReference(id, "realized stat id");
        if (modifierType == null) throw new IllegalArgumentException("realized stat modifier type is required");
        value = IncubationValues.requireFinite(value, "realized stat value");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(extensions, "realizedStat[" + id + "].extensions");
    }
}
