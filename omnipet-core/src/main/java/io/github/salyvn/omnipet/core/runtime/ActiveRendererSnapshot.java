package io.github.salyvn.omnipet.core.runtime;

import java.util.Set;
import java.util.UUID;

public record ActiveRendererSnapshot(
        UUID ownerId,
        UUID petInstanceId,
        long rendererGeneration,
        RendererCapabilities capabilities,
        Set<UUID> interactionEntityIds) {
    public ActiveRendererSnapshot {
        if (ownerId == null || petInstanceId == null) {
            throw new IllegalArgumentException("active renderer identity is required");
        }
        if (rendererGeneration < 0) throw new IllegalArgumentException("renderer generation cannot be negative");
        if (capabilities == null) throw new IllegalArgumentException("renderer capabilities are required");
        interactionEntityIds = Set.copyOf(interactionEntityIds == null ? Set.of() : interactionEntityIds);
    }
}
