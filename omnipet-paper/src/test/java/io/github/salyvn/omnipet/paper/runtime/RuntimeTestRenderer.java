package io.github.salyvn.omnipet.paper.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.runtime.PetRendererPort;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.runtime.RendererCapabilities;
import io.github.salyvn.omnipet.core.runtime.RendererHandle;
import io.github.salyvn.omnipet.core.runtime.RendererHealth;
import io.github.salyvn.omnipet.core.runtime.RendererSpawnRequest;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;

class RuntimeTestRenderer implements PetRendererPort {
    final Map<UUID, TestHandle> handles = new LinkedHashMap<>();
    final Map<UUID, Integer> spawnCounts = new LinkedHashMap<>();
    final Map<UUID, RendererSpawnRequest> spawnRequests = new LinkedHashMap<>();
    final Map<UUID, Integer> updateCounts = new LinkedHashMap<>();
    /** The last pose each pet was updated with, which is how idle behaviour is observed end to end. */
    final Map<UUID, RuntimeTransform> updateTransforms = new LinkedHashMap<>();
    final Map<UUID, Integer> removeCounts = new LinkedHashMap<>();
    UUID failSpawnPet;
    RendererHealth health = new RendererHealth(RendererHealth.Status.AVAILABLE, "test");

    @Override public RendererHealth health() { return health; }

    @Override
    public RendererHandle spawn(RendererSpawnRequest request) {
        if (request.petInstanceId().equals(failSpawnPet)) throw new IllegalStateException("test spawn failure");
        TestHandle handle = new TestHandle(request, UUID.randomUUID());
        handles.put(request.petInstanceId(), handle);
        spawnRequests.put(request.petInstanceId(), request);
        spawnCounts.merge(request.petInstanceId(), 1, Integer::sum);
        return handle;
    }

    @Override
    public void update(RendererHandle handle, RuntimeTransform transform) {
        updateCounts.merge(handle.petInstanceId(), 1, Integer::sum);
        updateTransforms.put(handle.petInstanceId(), transform);
    }

    @Override public void updateAppearance(RendererHandle handle, RendererAppearance appearance) {}

    @Override
    public void remove(RendererHandle handle) {
        TestHandle test = (TestHandle) handle;
        if (test.removed) return;
        test.removed = true;
        handles.remove(handle.petInstanceId(), test);
        removeCounts.merge(handle.petInstanceId(), 1, Integer::sum);
    }

    static final class TestHandle implements RendererHandle {
        private final RendererSpawnRequest request;
        private final UUID entityId;
        private boolean removed;

        private TestHandle(RendererSpawnRequest request, UUID entityId) {
            this.request = request;
            this.entityId = entityId;
        }

        @Override public UUID ownerId() { return request.ownerId(); }
        @Override public UUID petInstanceId() { return request.petInstanceId(); }
        @Override public long rendererGeneration() { return request.rendererGeneration(); }
        @Override public RendererCapabilities capabilities() { return RendererCapabilities.head(); }
        @Override public Set<UUID> interactionEntityIds() { return Set.of(entityId); }
        @Override public boolean removed() { return removed; }
    }
}
