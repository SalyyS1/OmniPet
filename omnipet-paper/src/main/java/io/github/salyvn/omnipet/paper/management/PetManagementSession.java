package io.github.salyvn.omnipet.paper.management;

import java.util.Objects;
import java.util.UUID;

public record PetManagementSession(
        UUID viewerId,
        UUID ownerId,
        UUID petId,
        UUID sessionId,
        long expectedRevision) {
    public PetManagementSession {
        Objects.requireNonNull(viewerId, "management viewer ID");
        Objects.requireNonNull(ownerId, "management owner ID");
        Objects.requireNonNull(petId, "management pet ID");
        Objects.requireNonNull(sessionId, "management session ID");
        if (expectedRevision < 0) throw new IllegalArgumentException("management revision cannot be negative");
    }

    public PetManagementSession withRevision(long revision) {
        return new PetManagementSession(viewerId, ownerId, petId, sessionId, revision);
    }
}
