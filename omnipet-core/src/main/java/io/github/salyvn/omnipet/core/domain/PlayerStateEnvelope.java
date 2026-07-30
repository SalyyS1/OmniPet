package io.github.salyvn.omnipet.core.domain;

public record PlayerStateEnvelope(int schemaVersion, PlayerState state) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public PlayerStateEnvelope {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported player schema version: " + schemaVersion);
        }
        if (state == null) throw new IllegalArgumentException("player state is required");
    }
}
