package io.github.salyvn.omnipet.paper.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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

    @Test
    void fallingBackToAHeadSaysWhy() {
        /*
         * The whole of the "MODELENGINE pet activates as HEAD" report. The fallback worked exactly as
         * designed and told nobody: the reason was captured into a local and dropped as soon as the head
         * renderer succeeded, so an operator saw a player head where they had authored a model and had no
         * way to learn whether ModelEngine was missing, quarantined, or simply lacking the blueprint.
         */
        RuntimeTestRenderer head = new RuntimeTestRenderer();
        PetRendererPort broken = new RuntimeTestRenderer() {
            @Override public RendererHandle spawn(RendererSpawnRequest request) {
                throw new IllegalStateException("ModelEngine is not enabled");
            }
        };
        List<String> reported = new ArrayList<>();
        HeadFallbackRendererResolver resolver =
                new HeadFallbackRendererResolver(request -> broken, head, reported::add);
        RendererSpawnRequest request = modelRequest();

        resolver.resolve(request).spawn(request);

        assertEquals(1, reported.size(), "the reason must reach the operator exactly once");
        assertTrue(reported.get(0).contains("MODELENGINE"), "it must name the provider that was passed over");
        assertTrue(reported.get(0).contains("ModelEngine is not enabled"),
                "and the actual reason: " + reported.get(0));
    }

    @Test
    void theSameReasonIsNotRepeatedForEveryPet() {
        // The reason is a property of the server's configuration, so it is identical on every pet and
        // every tick. Reporting per spawn would turn one diagnosable line into a flooded log.
        RuntimeTestRenderer head = new RuntimeTestRenderer();
        PetRendererPort broken = new RuntimeTestRenderer() {
            @Override public RendererHandle spawn(RendererSpawnRequest request) {
                throw new IllegalStateException("ModelEngine is not enabled");
            }
        };
        List<String> reported = new ArrayList<>();
        HeadFallbackRendererResolver resolver =
                new HeadFallbackRendererResolver(request -> broken, head, reported::add);

        for (int index = 0; index < 25; index++) {
            RendererSpawnRequest request = modelRequest();
            resolver.resolve(request).spawn(request);
        }

        assertEquals(1, reported.size(), "twenty-five pets must not produce twenty-five warnings");
    }

    @Test
    void aSuccessfulPreferredRendererReportsNothing() {
        RuntimeTestRenderer head = new RuntimeTestRenderer();
        RuntimeTestRenderer preferred = new RuntimeTestRenderer();
        List<String> reported = new ArrayList<>();
        HeadFallbackRendererResolver resolver =
                new HeadFallbackRendererResolver(request -> preferred, head, reported::add);
        RendererSpawnRequest request = modelRequest();

        resolver.resolve(request).spawn(request);

        assertTrue(reported.isEmpty(), "nothing went wrong, so nothing is worth saying");
    }

    @Test
    void aBrokenReporterCannotStopAPetSpawning() {
        // An observer is diagnostics. It must never be able to cost a player their pet.
        RuntimeTestRenderer head = new RuntimeTestRenderer();
        PetRendererPort broken = new RuntimeTestRenderer() {
            @Override public RendererHandle spawn(RendererSpawnRequest request) {
                throw new IllegalStateException("ModelEngine is not enabled");
            }
        };
        HeadFallbackRendererResolver resolver = new HeadFallbackRendererResolver(
                request -> broken, head, reason -> { throw new IllegalStateException("logger exploded"); });
        RendererSpawnRequest request = modelRequest();

        RendererHandle handle = resolver.resolve(request).spawn(request);

        assertEquals(request.petInstanceId(), handle.petInstanceId());
        assertEquals(1, head.spawnCounts.get(request.petInstanceId()));
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
