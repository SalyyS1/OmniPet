package io.github.salyvn.omnipet.core.domain;

public record PetDefinitionEnvelope(int schemaVersion, PetDefinition definition) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public PetDefinitionEnvelope {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported pet definition schema version: " + schemaVersion);
        }
        if (definition == null) throw new IllegalArgumentException("pet definition is required");
    }
}
