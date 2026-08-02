package io.github.salyvn.omnipet.paper.render;

import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.runtime.RendererCapabilities;
import io.github.salyvn.omnipet.core.runtime.RendererHandle;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;

final class PaperHeadRendererHandle implements RendererHandle {
    private final UUID ownerId;
    private final UUID petInstanceId;
    private final long rendererGeneration;
    private final PaperHeadRendererBackend.EntityRef carrier;
    private final PaperHeadRendererBackend.EntityRef visual;
    private final PaperHeadRendererBackend.EntityRef interaction;
    private RendererAppearance appearance;
    private RuntimeTransform transform;
    private boolean removed;

    PaperHeadRendererHandle(
            UUID ownerId,
            UUID petInstanceId,
            long rendererGeneration,
            PaperHeadRendererBackend.EntityRef carrier,
            PaperHeadRendererBackend.EntityRef visual,
            PaperHeadRendererBackend.EntityRef interaction,
            RendererAppearance appearance,
            RuntimeTransform transform) {
        this.ownerId = ownerId;
        this.petInstanceId = petInstanceId;
        this.rendererGeneration = rendererGeneration;
        this.carrier = carrier;
        this.visual = visual;
        this.interaction = interaction;
        this.appearance = appearance;
        this.transform = transform;
    }

    @Override public UUID ownerId() { return ownerId; }
    @Override public UUID petInstanceId() { return petInstanceId; }
    @Override public long rendererGeneration() { return rendererGeneration; }
    @Override public RendererCapabilities capabilities() { return RendererCapabilities.head(); }
    @Override public Set<UUID> interactionEntityIds() { return Set.of(interaction.id()); }
    @Override public boolean removed() { return removed; }

    PaperHeadRendererBackend.EntityRef carrier() { return carrier; }
    PaperHeadRendererBackend.EntityRef visual() { return visual; }
    PaperHeadRendererBackend.EntityRef interaction() { return interaction; }
    RendererAppearance appearance() { return appearance; }
    void appearance(RendererAppearance value) { appearance = value; }
    RuntimeTransform transform() { return transform; }
    void transform(RuntimeTransform value) { transform = value; }
    void markRemoved() { removed = true; }
}
