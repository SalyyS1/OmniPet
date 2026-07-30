package io.github.salyvn.omnipet.paper.studio.session;

import java.util.Objects;
import java.util.UUID;

/** Immutable capability for one rendered studio view. */
public record StudioViewToken(
        UUID sessionId,
        UUID viewerId,
        long viewNonce,
        long registryGeneration,
        long baseRevision,
        String baseHash) {
    public StudioViewToken {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(viewerId, "viewerId");
        if (viewNonce < 1) throw new IllegalArgumentException("view nonce must be positive");
        if (registryGeneration < 0) throw new IllegalArgumentException("registry generation cannot be negative");
        if (baseRevision < 0) throw new IllegalArgumentException("base revision cannot be negative");
        baseHash = baseHash == null ? "" : baseHash;
    }
}
