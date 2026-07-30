package io.github.salyvn.omnipet.core.studio;

import java.time.Instant;
import java.util.UUID;

public record StudioAuditEntry(
        Instant timestamp,
        UUID idempotencyKey,
        Operation operation,
        String definitionId,
        long registryGeneration,
        boolean success,
        String message) {
    public enum Operation { SAVE, ARCHIVE, HARD_DELETE, RELOAD }

    public StudioAuditEntry {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        if (idempotencyKey == null) throw new IllegalArgumentException("audit idempotency key is required");
        if (operation == null) throw new IllegalArgumentException("audit operation is required");
        message = message == null ? "" : message;
    }
}
