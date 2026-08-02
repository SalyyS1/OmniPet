package io.github.salyvn.omnipet.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PetActivationServiceTest {
    @Test
    void reconcileSpawnsUpdatesAndRemovesTowardDesiredState() {
        InteractionIndex interactions = new InteractionIndex();
        PetActivationService activation = new PetActivationService(interactions);
        FakeRenderer renderer = new FakeRenderer();
        UUID owner = UUID.randomUUID();
        RendererSpawnRequest first = request(owner, UUID.randomUUID(), 1);
        RendererSpawnRequest second = request(owner, UUID.randomUUID(), 1);

        PetActivationResult initial = activation.reconcile(owner, java.util.List.of(first, second), ignored -> renderer);
        PetActivationResult reduced = activation.reconcile(owner, java.util.List.of(second), ignored -> renderer);

        assertEquals(java.util.List.of(first.petInstanceId(), second.petInstanceId()), initial.spawnedPetIds());
        assertEquals(java.util.List.of(first.petInstanceId()), reduced.removedPetIds());
        assertEquals(java.util.List.of(second.petInstanceId()), reduced.activePetIds());
        assertEquals(1, interactions.size());
        assertEquals(1, activation.activeCount());
        assertEquals(1, activation.activeRenderers(owner).size());
        assertEquals(second.petInstanceId(), activation.activeRenderers(owner).getFirst().petInstanceId());
    }

    @Test
    void failedSpawnAndInteractionRegistrationAreCompensated() {
        InteractionIndex interactions = new InteractionIndex();
        PetActivationService activation = new PetActivationService(interactions);
        FakeRenderer renderer = new FakeRenderer();
        UUID owner = UUID.randomUUID();
        RendererSpawnRequest request = request(owner, UUID.randomUUID(), 1);
        renderer.failSpawn = true;

        PetActivationResult failed = activation.reconcile(owner, java.util.List.of(request), ignored -> renderer);

        assertFalse(failed.converged());
        assertEquals(0, activation.activeCount());
        assertEquals(0, interactions.size());

        renderer.failSpawn = false;
        renderer.sharedEntity = UUID.randomUUID();
        RendererSpawnRequest existing = request(owner, UUID.randomUUID(), 1);
        assertTrue(activation.reconcile(owner, java.util.List.of(existing), ignored -> renderer).converged());
        RendererSpawnRequest collision = request(UUID.randomUUID(), UUID.randomUUID(), 1);
        PetActivationResult collided = activation.reconcile(
                collision.ownerId(), java.util.List.of(collision), ignored -> renderer);

        assertFalse(collided.converged());
        assertTrue(renderer.removed.contains(collision.petInstanceId()));
        assertEquals(1, activation.activeCount());
    }

    @Test
    void failedRemovalStaysTrackedForTheNextReconciliation() {
        InteractionIndex interactions = new InteractionIndex();
        PetActivationService activation = new PetActivationService(interactions);
        FakeRenderer renderer = new FakeRenderer();
        UUID owner = UUID.randomUUID();
        RendererSpawnRequest request = request(owner, UUID.randomUUID(), 1);
        activation.reconcile(owner, java.util.List.of(request), ignored -> renderer);
        renderer.failRemove = true;

        PetActivationResult failed = activation.reconcile(owner, java.util.List.of(), ignored -> renderer);
        renderer.failRemove = false;
        PetActivationResult retried = activation.reconcile(owner, java.util.List.of(), ignored -> renderer);

        assertFalse(failed.converged());
        assertEquals(java.util.List.of(request.petInstanceId()), failed.activePetIds());
        assertTrue(retried.converged());
        assertEquals(java.util.List.of(request.petInstanceId()), retried.removedPetIds());
        assertEquals(0, activation.activeCount());
    }

    @Test
    void rendererInvalidationIsReconciledWithFreshHandle() {
        InteractionIndex interactions = new InteractionIndex();
        PetActivationService activation = new PetActivationService(interactions);
        FakeRenderer renderer = new FakeRenderer();
        UUID owner = UUID.randomUUID();
        RendererSpawnRequest request = request(owner, UUID.randomUUID(), 1);
        activation.reconcile(owner, java.util.List.of(request), ignored -> renderer);
        UUID originalEntity = renderer.handles.get(request.petInstanceId()).entity;
        renderer.invalidateOnUpdate = true;

        PetActivationResult recovered = activation.reconcile(owner, java.util.List.of(request), ignored -> renderer);

        UUID replacementEntity = renderer.handles.get(request.petInstanceId()).entity;
        assertTrue(recovered.converged());
        assertEquals(java.util.List.of(request.petInstanceId()), recovered.spawnedPetIds());
        assertFalse(originalEntity.equals(replacementEntity));
        assertEquals(1, interactions.size());
    }

    private static RendererSpawnRequest request(UUID owner, UUID pet, long generation) {
        return new RendererSpawnRequest(
                owner,
                pet,
                generation,
                "wolf",
                new RendererAppearance("HEAD", "", "TEXTURE_URL", "https://example.invalid/wolf.png"),
                new RuntimeTransform(RuntimeVector.ZERO, 0, 0, 1));
    }

    private static final class FakeRenderer implements PetRendererPort {
        private final Map<UUID, FakeHandle> handles = new HashMap<>();
        private final Set<UUID> removed = new LinkedHashSet<>();
        private boolean failSpawn;
        private boolean failRemove;
        private boolean invalidateOnUpdate;
        private UUID sharedEntity;

        @Override public RendererHealth health() {
            return new RendererHealth(RendererHealth.Status.AVAILABLE, "test");
        }

        @Override public RendererHandle spawn(RendererSpawnRequest request) {
            if (failSpawn) throw new IllegalStateException("spawn failed");
            UUID entity = sharedEntity == null ? UUID.randomUUID() : sharedEntity;
            FakeHandle handle = new FakeHandle(request, entity);
            handles.put(request.petInstanceId(), handle);
            return handle;
        }

        @Override public void update(RendererHandle handle, RuntimeTransform transform) {
            if (!invalidateOnUpdate) return;
            invalidateOnUpdate = false;
            FakeHandle fake = (FakeHandle) handle;
            fake.removed = true;
            handles.remove(handle.petInstanceId());
            throw new IllegalStateException("invalidated");
        }

        @Override public void updateAppearance(RendererHandle handle, RendererAppearance appearance) {}

        @Override public void remove(RendererHandle handle) {
            if (failRemove) throw new IllegalStateException("remove failed");
            FakeHandle fake = (FakeHandle) handle;
            fake.removed = true;
            removed.add(handle.petInstanceId());
            handles.remove(handle.petInstanceId());
        }
    }

    private static final class FakeHandle implements RendererHandle {
        private final RendererSpawnRequest request;
        private final UUID entity;
        private boolean removed;

        private FakeHandle(RendererSpawnRequest request, UUID entity) {
            this.request = request;
            this.entity = entity;
        }

        @Override public UUID ownerId() { return request.ownerId(); }
        @Override public UUID petInstanceId() { return request.petInstanceId(); }
        @Override public long rendererGeneration() { return request.rendererGeneration(); }
        @Override public RendererCapabilities capabilities() { return RendererCapabilities.head(); }
        @Override public Set<UUID> interactionEntityIds() { return Set.of(entity); }
        @Override public boolean removed() { return removed; }
    }
}
