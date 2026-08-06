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
    private final ArmorStand plate;
    private final Interaction interaction;
    private final Object modeledEntity;
    private final boolean animation;
    private Object activeModel;
    private String assetId;
    private RuntimeTransform transform;
    private String playingAnimation;
    /**
     * The appearance the plate was last written from.
     *
     * <p>Retained so a per-tick status update can rebuild the plate without the caller having to pass the
     * appearance again — the status changes as the pet walks, the appearance only on a rename.
     */
    private io.github.salyvn.omnipet.core.runtime.RendererAppearance appearance;
    /** The status word currently on the plate, so an unchanged one costs no packet. */
    private io.github.salyvn.omnipet.core.runtime.PetStatus status;
    private boolean removed;

    ModelEngineRendererHandle(
            UUID ownerId,
            UUID petInstanceId,
            long generation,
            ArmorStand carrier,
            ArmorStand plate,
            Interaction interaction,
            Object modeledEntity,
            Object activeModel,
            String assetId,
            RuntimeTransform transform,
            boolean animation) {
        this(ownerId, petInstanceId, generation, carrier, plate, interaction, modeledEntity, activeModel,
                assetId, transform, animation, null);
    }

    ModelEngineRendererHandle(
            UUID ownerId,
            UUID petInstanceId,
            long generation,
            ArmorStand carrier,
            ArmorStand plate,
            Interaction interaction,
            Object modeledEntity,
            Object activeModel,
            String assetId,
            RuntimeTransform transform,
            boolean animation,
            io.github.salyvn.omnipet.core.runtime.RendererAppearance appearance) {
        this.ownerId = ownerId;
        this.petInstanceId = petInstanceId;
        this.generation = generation;
        this.carrier = carrier;
        this.plate = plate;
        this.interaction = interaction;
        this.modeledEntity = modeledEntity;
        this.activeModel = activeModel;
        this.assetId = assetId;
        this.transform = transform;
        this.animation = animation;
        this.appearance = appearance;
    }

    @Override public UUID ownerId() { return ownerId; }
    @Override public UUID petInstanceId() { return petInstanceId; }
    @Override public long rendererGeneration() { return generation; }
    @Override public RendererCapabilities capabilities() {
        // Animation is per instance, not per implementation: a build that does not expose the animation
        // API still renders, and callers must be able to tell the difference.
        return new RendererCapabilities(true, false, true, true, animation);
    }
    @Override public Set<UUID> interactionEntityIds() { return Set.of(interaction.getUniqueId()); }
    @Override public boolean removed() { return removed; }

    ArmorStand carrier() { return carrier; }

    /**
     * The entity the nameplate is written to.
     *
     * <p>Not the carrier. ModelEngine's {@code setBaseEntityVisible(false)} despawns the base entity for
     * every tracking client rather than making it transparent, so a name written to the carrier reaches
     * nobody — which is why model pets had no plate while head pets did. This stand is ModelEngine's to
     * ignore: it rides the carrier, so it follows the model without the model knowing about it.
     */
    ArmorStand plate() { return plate; }
    Interaction interaction() { return interaction; }
    Object modeledEntity() { return modeledEntity; }
    Object activeModel() { return activeModel; }
    String assetId() { return assetId; }
    RuntimeTransform transform() { return transform; }

    /**
     * Whether the model's scale would actually differ from the one already applied.
     *
     * <p>Both writes it guards are expensive in different ways: {@code setScale} is a reflective call, and
     * the interaction's width and height are data-watcher fields, so re-applying an unchanged scale costs
     * a metadata packet per pet per tick to every nearby player and changes nothing on screen.
     */
    boolean scaleChanged(RuntimeTransform next) {
        return transform == null || transform.scale() != next.scale();
    }

    /** The clip currently driven, so a gait change stops the old one before starting the new. */
    String playingAnimation() { return playingAnimation; }
    void playingAnimation(String next) { playingAnimation = next; }

    io.github.salyvn.omnipet.core.runtime.RendererAppearance appearance() { return appearance; }
    void appearance(io.github.salyvn.omnipet.core.runtime.RendererAppearance next) { appearance = next; }

    /**
     * Records the status now on the plate, reporting whether it is new.
     *
     * <p>Compare-and-set in one call, so a caller cannot read the old value, write the plate, and forget
     * to store the new one — which would rewrite an identical plate every tick.
     */
    boolean statusChanged(io.github.salyvn.omnipet.core.runtime.PetStatus next) {
        if (status == next) return false;
        status = next;
        return true;
    }

    void assetId(String next) { assetId = next; }
    void activeModel(Object next) {
        activeModel = next;
        // A replaced model is a fresh handler, so nothing is playing on it yet.
        playingAnimation = null;
    }
    void transform(RuntimeTransform next) { transform = next; }
    void markRemoved() { removed = true; }
}
