package io.github.salyvn.omnipet.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FallbackPetRendererTest {
    @Test
    void failedPrimaryFallsBackAndAllOperationsStayOnSelectedRenderer() {
        FakeRenderer primary = new FakeRenderer(true);
        FakeRenderer head = new FakeRenderer(false);
        FallbackPetRenderer fallback = new FallbackPetRenderer(List.of(primary, head));

        RendererHandle handle = fallback.spawn(request());
        fallback.update(handle, request().transform());
        fallback.updateAppearance(handle, request().appearance());
        fallback.remove(handle);

        assertEquals(1, primary.spawnCalls);
        assertEquals(1, head.spawnCalls);
        assertEquals(1, head.updateCalls);
        assertEquals(1, head.appearanceCalls);
        assertEquals(1, head.removeCalls);
    }

    @Test
    void unavailableCandidatesFailWithoutReturningHalfHandle() {
        FakeRenderer unavailable = new FakeRenderer(false);
        unavailable.health = new RendererHealth(RendererHealth.Status.QUARANTINED, "wrong ABI");
        FakeRenderer failing = new FakeRenderer(true);

        assertThrows(IllegalStateException.class,
                () -> new FallbackPetRenderer(List.of(unavailable, failing)).spawn(request()));
        assertEquals(0, unavailable.spawnCalls);
    }

    private static RendererSpawnRequest request() {
        return new RendererSpawnRequest(
                UUID.randomUUID(), UUID.randomUUID(), 3, "ember_fox",
                new RendererAppearance("MODELENGINE", "ember_fox", "TEXTURE_URL", "https://example.invalid/a.png"),
                new RuntimeTransform(RuntimeVector.ZERO, 0, 0, 1));
    }

    private static final class FakeRenderer implements PetRendererPort {
        private final boolean failSpawn;
        private RendererHealth health = new RendererHealth(RendererHealth.Status.AVAILABLE, "ok");
        private int spawnCalls;
        private int updateCalls;
        private int appearanceCalls;
        private int removeCalls;

        private FakeRenderer(boolean failSpawn) { this.failSpawn = failSpawn; }
        @Override public RendererHealth health() { return health; }
        @Override public RendererHandle spawn(RendererSpawnRequest request) {
            spawnCalls++;
            if (failSpawn) throw new NoClassDefFoundError("vendor ABI");
            return new Handle(request);
        }
        @Override public void update(RendererHandle handle, RuntimeTransform transform) { updateCalls++; }
        @Override public void updateAppearance(RendererHandle handle, RendererAppearance appearance) { appearanceCalls++; }
        @Override public void remove(RendererHandle handle) { removeCalls++; ((Handle) handle).removed = true; }
    }

    private static final class Handle implements RendererHandle {
        private final RendererSpawnRequest request;
        private boolean removed;
        private Handle(RendererSpawnRequest request) { this.request = request; }
        @Override public UUID ownerId() { return request.ownerId(); }
        @Override public UUID petInstanceId() { return request.petInstanceId(); }
        @Override public long rendererGeneration() { return request.rendererGeneration(); }
        @Override public RendererCapabilities capabilities() { return RendererCapabilities.head(); }
        @Override public Set<UUID> interactionEntityIds() { return Set.of(UUID.randomUUID()); }
        @Override public boolean removed() { return removed; }
    }
}
