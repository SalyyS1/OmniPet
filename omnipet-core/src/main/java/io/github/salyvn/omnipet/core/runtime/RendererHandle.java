package io.github.salyvn.omnipet.core.runtime;

import java.util.Set;
import java.util.UUID;

public interface RendererHandle {
    UUID ownerId();

    UUID petInstanceId();

    long rendererGeneration();

    RendererCapabilities capabilities();

    Set<UUID> interactionEntityIds();

    boolean removed();
}
