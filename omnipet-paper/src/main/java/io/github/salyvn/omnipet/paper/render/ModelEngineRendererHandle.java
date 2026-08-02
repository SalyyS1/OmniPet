package io.github.salyvn.omnipet.paper.render;

import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;

import io.github.salyvn.omnipet.core.runtime.RendererCapabilities;
import io.github.salyvn.omnipet.core.runtime.RendererHandle;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;

final class ModelEngineRendererHandle implements RendererHandle {
    private final UUID ownerId;
    private final UUID petInstanceId;
    private final long generation;
    private final ArmorStand carrier;
    private final Interaction interaction;
    private final Object modeledEntity;
    private Object activeModel;
    private String assetId;
    private RuntimeTransform transform;
    private boolean removed;

    ModelEngineRendererHandle(
            UUID ownerId,
            UUID petInstanceId,
            long generation,
            ArmorStand carrier,
            Interaction interaction,
            Object modeledEntity,
            Object activeModel,
            String assetId,
            RuntimeTransform transform) {
        this.ownerId = ownerId;
        this.petInstanceId = petInstanceId;
        this.generation = generation;
        this.carrier = carrier;
        this.interaction = interaction;
        this.modeledEntity = modeledEntity;
        this.activeModel = activeModel;
        this.assetId = assetId;
        this.transform = transform;
    }

    @Override public UUID ownerId() { return ownerId; }
    @Override public UUID petInstanceId() { return petInstanceId; }
    @Override public long rendererGeneration() { return generation; }
    @Override public RendererCapabilities capabilities() {
        return new RendererCapabilities(true, false, true, true);
    }
    @Override public Set<UUID> interactionEntityIds() { return Set.of(interaction.getUniqueId()); }
    @Override public boolean removed() { return removed; }

    ArmorStand carrier() { return carrier; }
    Interaction interaction() { return interaction; }
    Object modeledEntity() { return modeledEntity; }
    Object activeModel() { return activeModel; }
    String assetId() { return assetId; }
    RuntimeTransform transform() { return transform; }
    void assetId(String next) { assetId = next; }
    void activeModel(Object next) { activeModel = next; }
    void transform(RuntimeTransform next) { transform = next; }
    void markRemoved() { removed = true; }
}
