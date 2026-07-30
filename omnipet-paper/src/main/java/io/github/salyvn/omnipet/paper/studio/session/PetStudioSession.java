package io.github.salyvn.omnipet.paper.studio.session;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PetStudioSession(
        UUID sessionId,
        UUID viewerId,
        String definitionId,
        long baseRevision,
        String baseHash,
        long registryGeneration,
        Instant expiresAt,
        boolean dirty,
        boolean pendingInput,
        StudioViewToken viewToken) {
    public PetStudioSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(viewToken, "viewToken");
        if (baseRevision < 0) throw new IllegalArgumentException("base revision cannot be negative");
        if (registryGeneration < 0) throw new IllegalArgumentException("registry generation cannot be negative");
        baseHash = baseHash == null ? "" : baseHash;
    }
}
