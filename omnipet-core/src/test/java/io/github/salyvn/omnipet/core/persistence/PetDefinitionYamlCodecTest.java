package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetDefinitionEnvelope;

class PetDefinitionYamlCodecTest {
    private final PetDefinitionYamlCodec codec = new PetDefinitionYamlCodec();

    @TempDir
    Path temporary;

    @Test
    void preservesUnknownDefinitionNodesOnRoundTrip() {
        String yaml = """
                schemaVersion: 2
                definitionId: nahara
                revision: 4
                classification: { tier: A }
                icon:
                  head: { source: TEXTURE_URL, value: 'https://textures.minecraft.net/texture/example' }
                display: { provider: HEAD, model: null }
                stats:
                  - { id: ATTACK_DAMAGE, type: FLAT, min: 10, max: 50 }
                vendorFuture: { nested: [a, b] }
                """;

        var first = codec.decode("nahara", yaml);
        var second = codec.decode("nahara", codec.encode(first));

        assertEquals(first.definition().rawNode().get("vendorFuture"), second.definition().rawNode().get("vendorFuture"));
        assertEquals(4, second.definition().revision());
        assertTrue(codec.encode(first).startsWith("schemaVersion:"));
    }

    /**
     * A stat's {@code vendorStatId} survives being written to disk and read back.
     *
     * <p>Load-bearing rather than incidental. The Studio's stat picker stores its own namespaced ID —
     * {@code mythiclib:attack_damage} — and records the provider's real name in {@code vendorStatId}
     * alongside it. That extra key is the only thing that lets the buff path recover the ID MythicLib
     * actually answers to. If the codec dropped it on the way to disk, every pet would still carry a
     * namespaced ID at runtime, the provider would never recognise it, and the stats would silently do
     * nothing — which is exactly the symptom this whole area was reported for.
     *
     * <p>It survives because the codec copies the raw node wholesale, so this asserts a property the
     * encoder never states explicitly and a future rewrite could quietly lose.
     */
    @Test
    void preservesTheVendorStatIdThatMakesOwnerBuffsResolvable() {
        String yaml = """
                schemaVersion: 2
                definitionId: nahara
                revision: 1
                classification: { tier: A }
                icon:
                  head: { source: TEXTURE_URL, value: 'https://textures.minecraft.net/texture/example' }
                display: { provider: HEAD, model: null }
                stats:
                  - { id: 'mythiclib:attack_damage', vendorStatId: ATTACK_DAMAGE, type: FLAT, min: 10, max: 50 }
                """;

        var reloaded = codec.decode("nahara", codec.encode(codec.decode("nahara", yaml)));

        var stats = (java.util.List<?>) reloaded.definition().rawNode().get("stats");
        var stat = (java.util.Map<?, ?>) stats.getFirst();
        assertEquals("mythiclib:attack_damage", stat.get("id"));
        assertEquals("ATTACK_DAMAGE", stat.get("vendorStatId"),
                "losing this key on disk would make every picked stat inert at runtime");
    }

    @Test
    void preservesUnknownNestedDisplayIconAndClassificationNodes() {
        String yaml = """
                schemaVersion: 2
                definitionId: nahara
                revision: 4
                classification: { tier: A, vendorFlag: true }
                icon:
                  head: { source: TEXTURE_URL, value: 'https://textures.minecraft.net/texture/example', providerFlag: true }
                display: { provider: HEAD, model: null, rendererFlag: true }
                """;

        var roundTripped = codec.decode("nahara", codec.encode(codec.decode("nahara", yaml)));

        assertEquals(true, ((Map<?, ?>) roundTripped.definition().rawNode().get("classification")).get("vendorFlag"));
        assertEquals(true, ((Map<?, ?>) ((Map<?, ?>) roundTripped.definition().rawNode().get("icon")).get("head")).get("providerFlag"));
        assertEquals(true, ((Map<?, ?>) roundTripped.definition().rawNode().get("display")).get("rendererFlag"));
    }

    @Test
    void rejectsInvalidBoundsFilenameMismatchAndMissingModel() {
        String base = """
                schemaVersion: 2
                definitionId: pet
                revision: 0
                classification: { tier: D }
                icon: { head: { source: TEXTURE_URL, value: value } }
                display: { provider: HEAD, model: null }
                stats: [{ id: SPEED, min: 2, max: 1 }]
                """;
        assertThrows(IllegalArgumentException.class, () -> codec.decode("pet", base));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("other", base.replace("min: 2, max: 1", "min: 1, max: 2")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("pet", base
                .replace("provider: HEAD", "provider: MODELENGINE")
                .replace("min: 2, max: 1", "min: 1, max: 2")));
    }

    @Test
    void rejectsMissingNonNumericAndNonFiniteStatBoundsWithFieldPaths() {
        assertInvalidStat("{ id: SPEED, max: 1 }", "stats[0].min");
        assertInvalidStat("{ id: SPEED, min: 0 }", "stats[0].max");
        assertInvalidStat("{ id: SPEED, min: slow, max: 1 }", "stats[0].min");
        assertInvalidStat("{ id: SPEED, min: 0, max: fast }", "stats[0].max");
        assertInvalidStat("{ id: SPEED, min: .NaN, max: 1 }", "stats[0].min");
        assertInvalidStat("{ id: SPEED, min: 0, max: .inf }", "stats[0].max");

        PetDefinition valid = codec.decode("pet", validDefinition()).definition();
        PetDefinition invalid = new PetDefinition(valid.id(), valid.revision(), valid.tier(), valid.icon(),
                valid.display(), Map.of("stats", List.of(Map.of("max", 1))));
        IllegalArgumentException encodeFailure = assertThrows(IllegalArgumentException.class,
                () -> codec.encode(new PetDefinitionEnvelope(PetDefinitionEnvelope.CURRENT_SCHEMA_VERSION, invalid)));
        assertTrue(encodeFailure.getMessage().contains("stats[0].min"), encodeFailure::getMessage);
    }

    @Test
    void repositoryIncrementsRevisionRejectsStaleWriterAndArchivesSafely() throws Exception {
        Path root = temporary.resolve("pets");
        YamlPetDefinitionRepository repository = new YamlPetDefinitionRepository(root);
        var definition = codec.decode("pet", """
                schemaVersion: 2
                definitionId: pet
                revision: 0
                classification: { tier: D }
                icon: { head: { source: TEXTURE_URL, value: value } }
                display: { provider: HEAD, model: null }
                """).definition();

        var first = repository.saveDraft(new PetDefinitionDraft(definition, 0));
        assertEquals(1, first.definition().revision());
        assertThrows(StaleRevisionException.class, () -> repository.saveDraft(new PetDefinitionDraft(definition, 0)));
        repository.archive("pet");
        assertTrue(Files.exists(root.resolve("archive")));
        assertTrue(repository.list().isEmpty());
    }

    private void assertInvalidStat(String stat, String expectedPath) {
        String yaml = validDefinition().replace("stats: []", "stats: [" + stat + "]");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> codec.decode("pet", yaml));
        assertTrue(failure.getMessage().contains(expectedPath), failure::getMessage);
    }

    private static String validDefinition() {
        return """
                schemaVersion: 2
                definitionId: pet
                revision: 0
                classification: { tier: D }
                icon: { head: { source: TEXTURE_URL, value: value } }
                display: { provider: HEAD, model: null }
                stats: []
                """;
    }
}
