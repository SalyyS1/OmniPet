package io.github.salyvn.omnipet.core.runtime;

import java.util.Objects;
import java.util.UUID;

public record InteractionIdentity(UUID ownerId, UUID petInstanceId, long rendererGeneration) {
    public InteractionIdentity {
        ownerId = Objects.requireNonNull(ownerId, "interaction owner ID");
        petInstanceId = Objects.requireNonNull(petInstanceId, "interaction pet instance ID");
        if (rendererGeneration < 0) throw new IllegalArgumentException("renderer generation cannot be negative");
    }
}
