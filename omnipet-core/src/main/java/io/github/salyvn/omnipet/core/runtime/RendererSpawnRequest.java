package io.github.salyvn.omnipet.core.runtime;

import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.StableId;

public record RendererSpawnRequest(
        UUID ownerId,
        UUID petInstanceId,
        long rendererGeneration,
        String definitionId,
        RendererAppearance appearance,
        RuntimeTransform transform) {
    public RendererSpawnRequest {
        ownerId = Objects.requireNonNull(ownerId, "renderer owner ID");
        petInstanceId = Objects.requireNonNull(petInstanceId, "renderer pet instance ID");
        if (rendererGeneration < 0) throw new IllegalArgumentException("renderer generation cannot be negative");
        definitionId = StableId.requireValid(definitionId);
        appearance = Objects.requireNonNull(appearance, "renderer appearance");
        transform = Objects.requireNonNull(transform, "renderer transform");
    }
}
