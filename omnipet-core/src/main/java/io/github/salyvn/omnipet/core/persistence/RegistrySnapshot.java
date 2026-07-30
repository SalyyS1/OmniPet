package io.github.salyvn.omnipet.core.persistence;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetDefinition;

public record RegistrySnapshot(long generation, Map<String, PetDefinition> definitions) {
    public RegistrySnapshot {
        if (generation < 0) throw new IllegalArgumentException("registry generation cannot be negative");
        definitions = Map.copyOf(definitions == null ? Map.of() : definitions);
    }
}
