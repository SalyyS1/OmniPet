package io.github.salyvn.omnipet.core.catalog;

import java.util.Map;
import java.util.Set;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.studio.StatModifierType;

/** One provider-neutral stat exposed by a dynamic source. */
public record StatCatalogEntry(String id, String displayName, Set<StatModifierType> supportedModifierTypes,
                               String provider, CatalogHealth health, long generation,
                               Map<String, Object> extensions) {
    public StatCatalogEntry {
        if (id == null || id.isBlank() || id.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("stat catalog id is required");
        }
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("stat display name is required");
        supportedModifierTypes = Set.copyOf(supportedModifierTypes == null ? Set.of() : supportedModifierTypes);
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("stat provider is required");
        if (health == null) throw new IllegalArgumentException("stat provider health is required");
        if (generation < 0) throw new IllegalArgumentException("stat generation cannot be negative");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
    }

    public String source() {
        return provider;
    }
}
