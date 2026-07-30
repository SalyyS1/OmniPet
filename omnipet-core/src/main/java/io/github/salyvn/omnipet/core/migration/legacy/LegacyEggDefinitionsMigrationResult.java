package io.github.salyvn.omnipet.core.migration.legacy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public record LegacyEggDefinitionsMigrationResult(
        int journalSchemaVersion,
        String sourceSemanticSha256,
        Map<String, Map<String, Object>> eggDefinitions,
        String journalPayload,
        boolean journalWritten) {
    public LegacyEggDefinitionsMigrationResult {
        if (journalSchemaVersion < 1) throw new IllegalArgumentException("journal schema version must be positive");
        if (sourceSemanticSha256 == null || !sourceSemanticSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("source semantic SHA-256 is invalid");
        }
        if (journalPayload == null) throw new IllegalArgumentException("journal payload is required");
        eggDefinitions = immutableDefinitions(eggDefinitions);
    }

    LegacyEggDefinitionsMigrationResult withJournalWritten(boolean written) {
        return new LegacyEggDefinitionsMigrationResult(
                journalSchemaVersion,
                sourceSemanticSha256,
                eggDefinitions,
                journalPayload,
                written);
    }

    private static Map<String, Map<String, Object>> immutableDefinitions(
            Map<String, ? extends Map<String, ?>> definitions) {
        if (definitions == null) throw new IllegalArgumentException("egg definitions are required");
        LinkedHashMap<String, Map<String, Object>> copy = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (id == null || definition == null) throw new IllegalArgumentException("egg definition is required");
            copy.put(id, RawNodeValues.immutableMap(definition));
        });
        return Collections.unmodifiableMap(copy);
    }
}
