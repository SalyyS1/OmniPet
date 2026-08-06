package io.github.salyvn.omnipet.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.progression.KillExperienceRules;

/**
 * The {@code progression.killExperience:} block.
 *
 * <p>Under {@code progression:} rather than as a root section, because it is a rule about how a pet levels
 * and needs the same {@code maxLevel} and overflow policy that section already owns.
 *
 * <p>The property that matters most here is what an <em>absent</em> block means. Every existing config on
 * every server predates this feature, and enabling something on upgrade that an operator never asked for
 * would change their server's balance without warning.
 */
class OmniPetConfigKillExperienceSectionTest {
    private final OmniPetConfigLoader loader = new OmniPetConfigLoader();

    @Test
    void theShippedBlockParses() {
        KillExperienceRules rules = loader.parse(shipped()).config().killExperience();

        assertTrue(rules.enabled());
        assertEquals(8.0, rules.experienceFor("ZOMBIE", 1), "the shipped per-mob override applies");
        assertEquals(5.0, rules.experienceFor("CREEPER", 1), "and an unlisted mob gets the default");
        assertEquals(0.0, rules.experienceFor("ARMOR_STAND", 1), "an explicit zero excludes a mob");
    }

    /** An absent block is the feature switched off, so an upgraded config behaves as it did. */
    @Test
    void anAbsentBlockLeavesTheFeatureOff() {
        String without = withoutKillExperience();
        assertFalse(without.contains("killExperience"), "the fixture must actually drop the block");

        KillExperienceRules rules = loader.parse(without).config().killExperience();

        assertFalse(rules.enabled());
        assertEquals(0.0, rules.experienceFor("ZOMBIE", 1));
    }

    /** Strict like the rest of `progression:`: a misspelled key is refused rather than ignored. */
    @Test
    void anUnknownKeyIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> loader.parse(withoutKillExperience().replace(
                "  overflowPolicy: CARRY",
                "  overflowPolicy: CARRY\n  killExperience:\n    defualt: 5.0")));
    }

    @Test
    void anInvalidShareIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> loader.parse(withoutKillExperience().replace(
                "  overflowPolicy: CARRY",
                "  overflowPolicy: CARRY\n  killExperience:\n    default: 5.0\n    share: HALF")));
    }

    @Test
    void aNegativeAmountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> loader.parse(withoutKillExperience().replace(
                "  overflowPolicy: CARRY",
                "  overflowPolicy: CARRY\n  killExperience:\n    default: -1.0")));
    }

    /**
     * A configured block survives being written back and read again.
     *
     * <p>Load-bearing because a legacy migration rewrites the file: if {@code encode} dropped this, an
     * operator's kill rules would silently vanish on the upgrade that rewrote their config, and the only
     * symptom would be pets that stopped levelling.
     */
    @Test
    void aConfiguredBlockSurvivesAWriteAndReread() {
        OmniPetConfig first = loader.parse(shipped()).config();

        KillExperienceRules reread = loader.parse(loader.encode(first)).config().killExperience();

        assertEquals(first.killExperience().defaultExperience(), reread.defaultExperience());
        assertEquals(first.killExperience().share(), reread.share());
        assertEquals(8.0, reread.experienceFor("ZOMBIE", 1), "per-mob overrides have to survive too");
        assertEquals(0.0, reread.experienceFor("ARMOR_STAND", 1));
    }

    /**
     * A disabled feature is not written into a config that never had it.
     *
     * <p>Otherwise every migrated file grows a block the operator did not write, and reading it later they
     * could not tell it from one they had chosen deliberately.
     */
    @Test
    void aDisabledFeatureIsNotWrittenBack() {
        OmniPetConfig off = loader.parse(withoutKillExperience()).config();

        assertFalse(loader.encode(off).contains("killExperience"));
    }

    /** Drops the whole block, matching how an operator's pre-upgrade file looks. */
    private static String withoutKillExperience() {
        String shipped = shipped();
        int start = shipped.indexOf("  killExperience:");
        assertTrue(start > 0, "the shipped config must contain the block for this fixture to remove it");
        // To the next top-level section, since the block is the last thing under progression:.
        int end = shipped.indexOf("\n# ---", start);
        assertTrue(end > start, "expected a following section to bound the removal");
        return shipped.substring(0, start) + shipped.substring(end);
    }

    private static String shipped() {
        try (var input = OmniPetConfigKillExperienceSectionTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
