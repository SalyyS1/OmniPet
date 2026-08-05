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
    void anUnchangedDisplayTransformIsNotResentEveryTick() {
        // The display's transformation used to be written on every update. Re-sending an identical one
        // restarts interpolation and dirties the entity's data watcher, so a stationary pet cost a packet
        // per tick for no visible change - multiplied by every pet on the server.
        FakeBackend backend = new FakeBackend();
        PaperHeadRenderer renderer = renderer(backend);
        RendererHandle handle = renderer.spawn(request());
        int afterSpawn = backend.scaleUpdates;

        RuntimeTransform still = new RuntimeTransform(new RuntimeVector(1, 0, 0), 0, 0, 1);
        renderer.update(handle, still);
        renderer.update(handle, still);
        renderer.update(handle, still);

        assertEquals(afterSpawn, backend.scaleUpdates, "an unchanged transform must not be resent");
    }

    @Test
    void aChangedScaleOrGaitIsStillSentOnce() {
        FakeBackend backend = new FakeBackend();
        PaperHeadRenderer renderer = renderer(backend);
        RendererHandle handle = renderer.spawn(request());
        int afterSpawn = backend.scaleUpdates;

        renderer.update(handle, new RuntimeTransform(new RuntimeVector(1, 0, 0), 0, 0, 2.0));
        assertEquals(afterSpawn + 1, backend.scaleUpdates, "a resized pet must be redrawn");

        // Speed drives the lean, so a pet breaking into a run looks different even at the same scale.
        renderer.update(handle, new RuntimeTransform(
                new RuntimeVector(1, 0, 0), 0, 0, 2.0, new RuntimeVector(0, 0, 3), true));
        assertEquals(afterSpawn + 2, backend.scaleUpdates, "a pet that started moving must be redrawn");
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

    @Test
    void aNamedPetGetsANameplateAndARenameReachesIt() {
        // Before this an activated pet was an anonymous floating head: no nameplate code existed anywhere
        // and no path carried a name to a renderer. The name rides the appearance, so a rename arrives
        // through updateAppearance rather than needing its own port method.
        FakeBackend backend = new FakeBackend();
        PaperHeadRenderer renderer = renderer(backend);

        RendererHandle handle = renderer.spawn(named("Shadow  Lv.7"));
        assertEquals("Shadow  Lv.7", backend.nameplate);

        renderer.updateAppearance(handle, new RendererAppearance(
                "HEAD", "", "TEXTURE_URL", "https://example.invalid/a.png", "Ember  Lv.8"));
        assertEquals("Ember  Lv.8", backend.nameplate);
    }

    @Test
    void aPetWithNoNameHasNoNameplate() {
        FakeBackend backend = new FakeBackend();

        renderer(backend).spawn(request());

        assertEquals(null, backend.nameplate, "an unnamed pet must not carry an empty plate");
    }

    @Test
    void anOperatorCanTurnNameplatesOffEntirely() {
        // A server with many pets out at once is a wall of floating text, so this has to be switchable
        // without giving up the names in menus.
        FakeBackend backend = new FakeBackend();
        PaperHeadRenderer renderer = new PaperHeadRenderer(backend,
                new PaperHeadRendererSettings(8.0, 0.35, 1.2, 3, 12.0, false));

        renderer.spawn(named("Shadow  Lv.7"));

        assertEquals(null, backend.nameplate);
    }

    private static RendererSpawnRequest named(String displayName) {
        return new RendererSpawnRequest(
                UUID.fromString("541642d7-62ee-4620-b35f-1fdb14cddae1"),
                UUID.fromString("e08e475f-d2ee-4249-aef6-9955f30de59f"),
                7,
                "wolf",
                new RendererAppearance(
                        "HEAD", "", "TEXTURE_URL", "https://example.invalid/a.png", displayName),
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
        /** The text currently on the pet's nameplate, or null when it has none. */
        private String nameplate;
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
        @Override public void updateName(EntityRef carrier, RendererAppearance appearance, PaperHeadRendererSettings settings) {
            // Recorded rather than counted: whether the plate shows the right text is the question, and a
            // call count cannot answer it.
            nameplate = settings.nameplates() && appearance.named() ? appearance.displayName() : null;
        }
        @Override public void updateScale(EntityRef visual, EntityRef interaction, RuntimeTransform transform, PaperHeadRendererSettings settings) { scaleUpdates++; }
        @Override public void remove(EntityRef entity) { removed.add(names.get(entity)); }
    }
}
