package io.github.salyvn.omnipet.paper.render;

import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;

interface PaperHeadRendererBackend {
    boolean isMainThread();

    WorldRef ownerWorld(UUID ownerId);

    boolean targetChunkLoaded(WorldRef world, RuntimeTransform transform);

    EntityRef spawnCarrier(WorldRef world, RuntimeTransform transform);

    EntityRef spawnVisual(WorldRef world, RuntimeTransform transform);

    EntityRef spawnInteraction(WorldRef world, RuntimeTransform transform);

    void attach(EntityRef carrier, EntityRef passenger);

    boolean valid(EntityRef entity);

    UUID worldId(EntityRef entity);

    boolean currentChunkLoaded(EntityRef entity);

    double distanceSquared(EntityRef entity, RuntimeTransform transform);

    void smoothMove(EntityRef carrier, RuntimeTransform transform, PaperHeadRendererSettings settings);

    /**
     * Turns the head display to match the pet's heading.
     *
     * <p>Its own call rather than part of {@code smoothMove} because the two write different entities for
     * different reasons: the carrier is moved and rotated as a vehicle, while the display is a passenger
     * that Minecraft carries along but never turns. Rotating only the carrier is what left every head
     * frozen at its spawn heading.
     */
    void turnVisual(EntityRef visual, float carrierYaw);

    void hardTeleport(
            EntityRef carrier,
            EntityRef visual,
            EntityRef interaction,
            WorldRef world,
            RuntimeTransform transform);

    void updateAppearance(EntityRef visual, RendererAppearance appearance);

    /**
     * Shows or hides the nameplate above a pet.
     *
     * <p>Applied to the carrier rather than the visual: the carrier is the entity the passengers ride, so
     * its name floats above the whole pet regardless of how the display is scaled or leaned.
     */
    void updateName(EntityRef carrier, RendererAppearance appearance, PaperHeadRendererSettings settings);

    /**
     * Rewrites the nameplate with the pet's current status.
     *
     * <p>Separate from {@link #updateName} because the two change for different reasons and at different
     * rates: a name changes when somebody renames the pet, a status changes as it walks. The renderer only
     * calls this when the resulting text actually differs, so a pet holding one status costs nothing.
     */
    void updateStatus(
            EntityRef carrier,
            RendererAppearance appearance,
            PaperHeadRendererSettings settings,
            io.github.salyvn.omnipet.core.runtime.PetStatus status);

    void updateScale(
            EntityRef visual,
            EntityRef interaction,
            RuntimeTransform transform,
            PaperHeadRendererSettings settings);

    void remove(EntityRef entity);

    record WorldRef(UUID id, Object nativeWorld) {
        public WorldRef {
            Objects.requireNonNull(id, "world ID");
            Objects.requireNonNull(nativeWorld, "native world");
        }
    }

    record EntityRef(UUID id, Object nativeEntity) {
        public EntityRef {
            Objects.requireNonNull(id, "entity ID");
            Objects.requireNonNull(nativeEntity, "native entity");
        }
    }
}
