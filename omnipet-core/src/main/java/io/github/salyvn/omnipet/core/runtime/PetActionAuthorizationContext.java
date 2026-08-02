package io.github.salyvn.omnipet.core.runtime;

import java.util.Objects;
import java.util.UUID;

public record PetActionAuthorizationContext(
        UUID actorId,
        UUID ownerId,
        UUID petInstanceId,
        PetAction action,
        boolean sameWorld,
        double distance,
        long resolvedRendererGeneration,
        long currentRendererGeneration,
        boolean publicAccess,
        boolean adminOverride) {
    public PetActionAuthorizationContext {
        actorId = Objects.requireNonNull(actorId, "action actor ID");
        ownerId = Objects.requireNonNull(ownerId, "action owner ID");
        petInstanceId = Objects.requireNonNull(petInstanceId, "action pet instance ID");
        action = Objects.requireNonNull(action, "pet action");
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("action distance must be finite and non-negative");
        }
        if (resolvedRendererGeneration < 0 || currentRendererGeneration < 0) {
            throw new IllegalArgumentException("renderer generation cannot be negative");
        }
    }
}
