package io.github.salyvn.omnipet.paper.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.runtime.PetRendererPort;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.runtime.RendererHandle;
import io.github.salyvn.omnipet.core.runtime.RendererHealth;
import io.github.salyvn.omnipet.core.runtime.RendererSpawnRequest;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;
import io.github.salyvn.omnipet.core.runtime.RuntimeVector;

class HeadFallbackRendererResolverTest {
    @Test
    void optionalRendererFailureFallsBackAndKeepsLifecycleRoutedToHead() {
        RuntimeTestRenderer head = new RuntimeTestRenderer();
        PetRendererPort broken = new RuntimeTestRenderer() {
            @Override public RendererHandle spawn(RendererSpawnRequest request) {
                throw new NoClassDefFoundError("wrong optional ABI");
            }
        };
        HeadFallbackRendererResolver resolver = new HeadFallbackRendererResolver(request -> broken, head);
        RendererSpawnRequest request = modelRequest();
        PetRendererPort routed = resolver.resolve(request);

        RendererHandle handle = routed.spawn(request);
        routed.update(handle, request.transform());
        routed.remove(handle);

        assertEquals(1, head.spawnCounts.get(request.petInstanceId()));
        assertEquals(1, head.updateCounts.get(request.petInstanceId()));
        assertEquals(1, head.removeCounts.get(request.petInstanceId()));
        assertTrue(handle.removed());
    }

    @Test
    void healthyOptionalRendererWinsWithoutTouchingHeadFallback() {
        RuntimeTestRenderer head = new RuntimeTestRenderer();
        head.health = new RendererHealth(RendererHealth.Status.UNAVAILABLE, "head disabled");
        RuntimeTestRenderer preferred = new RuntimeTestRenderer();
        HeadFallbackRendererResolver resolver = new HeadFallbackRendererResolver(request -> preferred, head);
        RendererSpawnRequest request = modelRequest();

        RendererHandle handle = resolver.resolve(request).spawn(request);

        assertEquals(1, preferred.spawnCounts.get(request.petInstanceId()));
        assertTrue(head.spawnCounts.isEmpty());
        assertEquals(RendererHealth.Status.DEGRADED, resolver.resolve(request).health().status());
        assertEquals(request.petInstanceId(), handle.petInstanceId());
    }

    private static RendererSpawnRequest modelRequest() {
        return new RendererSpawnRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "fox",
                new RendererAppearance(
                        "MODELENGINE", "fox_model", "TEXTURE_URL",
                        "https://textures.minecraft.net/texture/fox"),
                new RuntimeTransform(RuntimeVector.ZERO, 0, 0, 1));
    }
}
