package io.github.salyvn.omnipet.core.studio;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

/** A provider-neutral stat selection and bounded value range. */
public record StudioStat(String id, StatModifierType modifierType, StatRange range, Map<String, Object> extensions) {
    public StudioStat {
        id = requireReference(id, "stat id");
        if (modifierType == null) throw new IllegalArgumentException("stat modifier type is required");
        if (range == null) throw new IllegalArgumentException("stat range is required");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
    }

    static String requireReference(String value, String field) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(field + " must be non-blank and whitespace-free");
        }
        return value;
    }
}
