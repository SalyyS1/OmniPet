package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetTier;

class EggDefinitionYamlCodecTest {
    private final EggDefinitionYamlCodec codec = new EggDefinitionYamlCodec();

    @Test
    void roundTripsTierDurationCandidatesAndUnknownNodes() {
        String yaml = """
                schemaVersion: 1
                eggId: tier_d_egg
                tier: D
                baseDuration: 1h30m
                candidates:
                  - definitionId: ember_fox
                    weight: 3
                    vendorCandidate: keep
                  - definitionId: stone_wolf
                    weight: 1
                vendorEgg: { enabled: true }
                """;

        var first = codec.decode("tier_d_egg", yaml);
        var second = codec.decode("tier_d_egg", codec.encode(first));

        assertEquals(first, second);
        assertEquals(PetTier.D, first.definition().tier());
        assertEquals(5_400_000, first.definition().baseActiveMillis());
        assertEquals("keep", first.definition().candidates().getFirst().extensions().get("vendorCandidate"));
        assertTrue(first.definition().extensions().containsKey("vendorEgg"));
    }

    @Test
    void rejectsFilenameMismatchInvalidDurationAndDuplicateCandidates() {
        String base = """
                schemaVersion: 1
                eggId: tier_d_egg
                tier: D
                baseDuration: %s
                candidates:
                  - { definitionId: ember_fox, weight: 1 }
                """;
        assertThrows(IllegalArgumentException.class, () -> codec.decode("other", base.formatted("1h")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("tier_d_egg", base.formatted("zero")));
        String duplicate = base.formatted("1h")
                + "  - { definitionId: ember_fox, weight: 2 }\n";
        assertThrows(IllegalArgumentException.class, () -> codec.decode("tier_d_egg", duplicate));
    }
}
