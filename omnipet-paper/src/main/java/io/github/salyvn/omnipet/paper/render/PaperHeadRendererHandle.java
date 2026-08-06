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
    /** The status word currently on the plate, so an unchanged one costs no packet. */
    private io.github.salyvn.omnipet.core.runtime.PetStatus status;
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

    /**
     * Records the status now on the plate, reporting whether it is new.
     *
     * <p>Compare-and-set in one call so a caller cannot read the old value, write the plate, and forget to
     * store the new one — which would rewrite the plate every tick forever.
     */
    boolean statusChanged(io.github.salyvn.omnipet.core.runtime.PetStatus next) {
        if (status == next) return false;
        status = next;
        return true;
    }

    /**
     * Whether the display's transformation would actually differ from the one already sent.
     *
     * <p>Re-sending an identical transformation restarts interpolation and dirties the display's data
     * watcher, so an unconditional per-tick write costs a packet per pet for no visible change. Only the
     * channels the transformation carries are compared; position and yaw move the carrier, not the
     * display, and are handled separately.
     */
    boolean displayTransformChanged(RuntimeTransform next) {
        return transform == null
                || transform.scale() != next.scale()
                || transform.dashing() != next.dashing()
                || tiltDiffers(next);
    }

    /** Tilt follows horizontal speed, so a speed change is what makes the lean visibly different. */
    private boolean tiltDiffers(RuntimeTransform next) {
        return Math.abs(transform.horizontalSpeed() - next.horizontalSpeed()) > 0.02;
    }

    void markRemoved() { removed = true; }
}
