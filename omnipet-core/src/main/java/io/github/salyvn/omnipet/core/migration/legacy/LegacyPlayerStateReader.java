package io.github.salyvn.omnipet.core.migration.legacy;

import io.github.salyvn.omnipet.core.persistence.PlayerMigrationResult;
import io.github.salyvn.omnipet.core.persistence.PlayerStateYamlCodec;

public final class LegacyPlayerStateReader {
    private final PlayerStateYamlCodec codec = new PlayerStateYamlCodec();

    public PlayerMigrationResult read(String yaml) {
        PlayerMigrationResult result = codec.decodeWithReport(yaml);
        if (result.report().sourceSchemaVersion() != 1) {
            throw new IllegalArgumentException("legacy reader accepts only schema version 1");
        }
        return result;
    }
}
