package io.github.salyvn.omnipet.paper.studio.session;

import java.time.Instant;
import java.util.UUID;

final class MutableSession {
    final UUID sessionId;
    final UUID viewerId;
    final String definitionId;
    final long baseRevision;
    final String baseHash;
    final long registryGeneration;
    Instant expiresAt;
    long viewNonce = 1;
    boolean dirty;
    boolean pendingInput;
    long expiryNonce;
    StudioScheduler.ScheduledHandle expiryHandle = () -> {};

    MutableSession(UUID sessionId, UUID viewerId, String definitionId, long baseRevision,
            String baseHash, long registryGeneration, Instant expiresAt) {
        this.sessionId = sessionId;
        this.viewerId = viewerId;
        this.definitionId = definitionId;
        this.baseRevision = baseRevision;
        this.baseHash = baseHash == null ? "" : baseHash;
        this.registryGeneration = registryGeneration;
        this.expiresAt = expiresAt;
    }

    StudioViewToken token() {
        return new StudioViewToken(sessionId, viewerId, viewNonce, registryGeneration, baseRevision, baseHash);
    }

    PetStudioSession snapshot() {
        return new PetStudioSession(sessionId, viewerId, definitionId, baseRevision, baseHash,
                registryGeneration, expiresAt, dirty, pendingInput, token());
    }

    boolean matches(StudioViewToken token) {
        return token.sessionId().equals(sessionId)
                && token.viewerId().equals(viewerId)
                && token.viewNonce() == viewNonce
                && token.registryGeneration() == registryGeneration
                && token.baseRevision() == baseRevision
                && token.baseHash().equals(baseHash);
    }
}
