package io.github.salyvn.omnipet.core.persistence;

import io.github.salyvn.omnipet.core.domain.PlayerStateEnvelope;

public record PlayerMigrationResult(PlayerStateEnvelope envelope, MigrationReport report) {}
