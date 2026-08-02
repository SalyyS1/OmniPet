package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.runtime.RendererHandle;
import io.github.salyvn.omnipet.core.runtime.RendererSpawnRequest;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;
import io.github.salyvn.omnipet.core.runtime.RuntimeVector;

class PaperHeadRendererTest {
    @Test
    void spawnIsIdempotentAndExposesHeadCapabilities() {
        FakeBackend backend = new FakeBackend();
        PaperHeadRenderer renderer = renderer(backend);
        RendererSpawnRequest request = request();

        RendererHandle first = renderer.spawn(request);
        RendererHandle retry = renderer.spawn(request);

        assertSame(first, retry);
        assertEquals(3, backend.spawned);
        assertEquals(true, first.capabilities().interaction());
        assertEquals(false, first.capabilities().model());
        assertEquals(false, first.capabilities().riding());
        assertEquals(1, first.interactionEntityIds().size());
    }

    @Test
    void routineUpdateUsesSmoothMovementAndUpdatesScaleAppearance() {
        FakeBackend backend = new FakeBackend();
        PaperHeadRenderer renderer = renderer(backend);
        RendererHandle handle = renderer.spawn(request());
        RuntimeTransform moved = new RuntimeTransform(new RuntimeVector(2, 3, 4), 5, 6, 1.5);

        renderer.update(handle, moved);
        renderer.updateAppearance(handle, new RendererAppearance("HEAD", "", "TEXTURE_URL", "https://example.invalid/b.png"));

        assertEquals(1, backend.smoothMoves);
        assertEquals(0, backend.hardTeleports);
        assertEquals(2, backend.appearanceUpdates);
        assertEquals(3, backend.scaleUpdates);
    }

    @Test
    void safetyDistanceUsesHardTeleportAndRemoveIsIdempotent() {
        FakeBackend backend = new FakeBackend();
        PaperHeadRenderer renderer = renderer(backend);
        RendererHandle handle = renderer.spawn(request());
        backend.distanceSquared = 100;

        renderer.update(handle, new RuntimeTransform(new RuntimeVector(10, 0, 0), 0, 0, 1));
        renderer.remove(handle);
        renderer.remove(handle);

        assertEquals(1, backend.hardTeleports);
        assertEquals(List.of("interaction", "visual", "carrier"), backend.removed);
        assertEquals(true, handle.removed());
    }

    @Test
    void partialSpawnFailureRollsBackCreatedEntities() {
        FakeBackend backend = new FakeBackend();
        backend.failInteraction = true;
        PaperHeadRenderer renderer = renderer(backend);

        assertThrows(IllegalStateException.class, () -> renderer.spawn(request()));

        assertEquals(List.of("visual", "carrier"), backend.removed);
    }

    @Test
    void invalidEntitiesRetireHandleForActivationRespawn() {
        FakeBackend backend = new FakeBackend();
        PaperHeadRenderer renderer = renderer(backend);
        RendererHandle stale = renderer.spawn(request());
        backend.entitiesValid = false;

        assertThrows(IllegalStateException.class, () -> renderer.update(stale,
                new RuntimeTransform(new RuntimeVector(1, 1, 1), 0, 0, 1)));

        assertEquals(true, stale.removed());
        assertEquals(0, backend.hardTeleports);
        assertEquals(List.of("interaction", "visual", "carrier"), backend.removed);

        backend.entitiesValid = true;
        RendererHandle replacement = renderer.spawn(request());
        assertEquals(false, replacement.removed());
        assertEquals(6, backend.spawned);
    }

    @Test
    void absentOwnerAndWrongThreadFailBeforeSpawning() {
        FakeBackend backend = new FakeBackend();
        backend.world = null;
        assertThrows(IllegalStateException.class, () -> renderer(backend).spawn(request()));
        backend.world = new PaperHeadRendererBackend.WorldRef(UUID.randomUUID(), "world");
        backend.mainThread = false;
        assertThrows(IllegalStateException.class, () -> renderer(backend).spawn(request()));
        assertEquals(0, backend.spawned);
    }

    private static PaperHeadRenderer renderer(FakeBackend backend) {
        return new PaperHeadRenderer(backend, PaperHeadRendererSettings.defaults());
    }

    private static RendererSpawnRequest request() {
        return new RendererSpawnRequest(
                UUID.fromString("541642d7-62ee-4620-b35f-1fdb14cddae1"),
                UUID.fromString("e08e475f-d2ee-4249-aef6-9955f30de59f"),
                7,
                "wolf",
                new RendererAppearance("HEAD", "", "TEXTURE_URL", "https://example.invalid/a.png"),
                new RuntimeTransform(new RuntimeVector(0, 1, 0), 0, 0, 1));
    }

    private static final class FakeBackend implements PaperHeadRendererBackend {
        private boolean mainThread = true;
        private WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        private final Map<EntityRef, String> names = new HashMap<>();
        private final List<String> removed = new ArrayList<>();
        private int spawned;
        private int smoothMoves;
        private int hardTeleports;
        private int appearanceUpdates;
        private int scaleUpdates;
        private double distanceSquared;
        private boolean failInteraction;
        private boolean entitiesValid = true;

        @Override public boolean isMainThread() { return mainThread; }
        @Override public WorldRef ownerWorld(UUID ownerId) { return world; }
        @Override public boolean targetChunkLoaded(WorldRef world, RuntimeTransform transform) { return true; }
        @Override public EntityRef spawnCarrier(WorldRef world, RuntimeTransform transform) { return spawn("carrier"); }
        @Override public EntityRef spawnVisual(WorldRef world, RuntimeTransform transform) { return spawn("visual"); }
        @Override public EntityRef spawnInteraction(WorldRef world, RuntimeTransform transform) {
            if (failInteraction) throw new IllegalStateException("interaction failed");
            return spawn("interaction");
        }
        private EntityRef spawn(String name) {
            EntityRef ref = new EntityRef(UUID.randomUUID(), name);
            names.put(ref, name);
            spawned++;
            return ref;
        }
        @Override public void attach(EntityRef carrier, EntityRef passenger) {}
        @Override public boolean valid(EntityRef entity) { return entitiesValid; }
        @Override public UUID worldId(EntityRef entity) { return world.id(); }
        @Override public boolean currentChunkLoaded(EntityRef entity) { return true; }
        @Override public double distanceSquared(EntityRef entity, RuntimeTransform transform) { return distanceSquared; }
        @Override public void smoothMove(EntityRef carrier, RuntimeTransform transform, PaperHeadRendererSettings settings) { smoothMoves++; }
        @Override public void hardTeleport(EntityRef carrier, EntityRef visual, EntityRef interaction, WorldRef world, RuntimeTransform transform) { hardTeleports++; }
        @Override public void updateAppearance(EntityRef visual, RendererAppearance appearance) { appearanceUpdates++; }
        @Override public void updateScale(EntityRef visual, EntityRef interaction, RuntimeTransform transform, PaperHeadRendererSettings settings) { scaleUpdates++; }
        @Override public void remove(EntityRef entity) { removed.add(names.get(entity)); }
    }
}
