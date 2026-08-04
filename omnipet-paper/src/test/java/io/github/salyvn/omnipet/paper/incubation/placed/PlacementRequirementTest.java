package io.github.salyvn.omnipet.paper.incubation.placed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * What an egg needs around it before a placed egg incubates.
 *
 * <p>Pure rules, so they are driven here without a server: the Paper layer only supplies the names of
 * the blocks it sees around the egg.
 */
class PlacementRequirementTest {
    @Test
    void anOrdinaryEggAcceptsAnySingleHeatSource() {
        PlacementRequirement requirement = PlacementRequirement.defaults();

        assertTrue(requirement.satisfiedBy(List.of("torch")));
        assertTrue(requirement.satisfiedBy(List.of("air", "stone", "campfire")));
        assertFalse(requirement.satisfiedBy(List.of("air", "stone")));
        assertFalse(requirement.satisfiedBy(List.of()));
    }

    @Test
    void aFireAffinityEggWantsAFullRingOfLava() {
        PlacementRequirement requirement = PlacementRequirement.surroundedByLava();

        assertTrue(requirement.satisfiedBy(java.util.Collections.nCopies(8, "lava")));
        assertFalse(requirement.satisfiedBy(java.util.Collections.nCopies(7, "lava")),
                "one gap in the ring is not surrounded");
        assertFalse(requirement.satisfiedBy(List.of("torch", "campfire")),
                "a fire egg does not settle for ordinary heat");
    }

    @Test
    void aNamespacedOrUppercaseBlockNameStillMatches() {
        // The caller may hand us whatever form the server reports, so the rule normalises rather than
        // making every call site remember to.
        assertTrue(PlacementRequirement.defaults().satisfiedBy(List.of("minecraft:TORCH")));
        assertTrue(PlacementRequirement.surroundedByLava()
                .satisfiedBy(java.util.Collections.nCopies(8, "MINECRAFT:Lava")));
    }

    @Test
    void anEggDefinitionCanDeclareTheLavaPreset() {
        PlacementRequirement requirement = PlacementRequirement.from(
                Map.of("placement", Map.of("requires", "lava")));

        assertEquals(PlacementRequirement.SURROUNDED_COUNT, requirement.minimumCount());
        assertTrue(requirement.satisfiedBy(java.util.Collections.nCopies(8, "lava")));
    }

    @Test
    void anEggDefinitionCanNameItsOwnBlocksAndCount() {
        PlacementRequirement requirement = PlacementRequirement.from(Map.of(
                "placement", Map.of("blocks", List.of("ice", "packed_ice"), "count", 3)));

        assertTrue(requirement.satisfiedBy(List.of("ice", "ice", "packed_ice")));
        assertFalse(requirement.satisfiedBy(List.of("ice", "ice")));
        assertFalse(requirement.satisfiedBy(java.util.Collections.nCopies(8, "torch")),
                "declaring blocks replaces the default heat list rather than adding to it");
    }

    @Test
    void aMalformedOrAbsentNodeFallsBackToOrdinaryHeat() {
        // An egg that cannot be incubated at all is a worse outcome than one that incubates too easily,
        // so a broken placement node degrades rather than refusing.
        assertEquals(PlacementRequirement.defaults(), PlacementRequirement.from(null));
        assertEquals(PlacementRequirement.defaults(), PlacementRequirement.from(Map.of()));
        assertEquals(PlacementRequirement.defaults(),
                PlacementRequirement.from(Map.of("placement", "not-a-map")));
        assertEquals(PlacementRequirement.defaults(),
                PlacementRequirement.from(Map.of("placement", Map.of("blocks", List.of()))));
    }

    @Test
    void anEggMayForbidPlacementEntirely() {
        assertTrue(PlacementRequirement.forEgg(Map.of(
                "placement", Map.of("allowed", false))).isEmpty());
        assertTrue(PlacementRequirement.forEgg(Map.of()).isPresent());
    }

    @Test
    void theRefusalMessageNamesWhatIsMissing() {
        assertEquals("next to heat", PlacementRequirement.defaults().describe());
        assertEquals("surrounded by lava", PlacementRequirement.surroundedByLava().describe());
    }
}
