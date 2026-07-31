package io.github.salyvn.omnipet.core.domain.incubation;

public record EggDefinitionEnvelope(int schemaVersion, EggDefinition definition) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public EggDefinitionEnvelope {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported egg definition schema version: " + schemaVersion);
        }
        if (definition == null) throw new IllegalArgumentException("egg definition is required");
    }
}
