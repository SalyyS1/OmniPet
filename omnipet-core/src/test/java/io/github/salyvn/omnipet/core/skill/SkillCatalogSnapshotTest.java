package io.github.salyvn.omnipet.core.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Matching a skill name against what the provider actually registered.
 *
 * <p>This is where an active skill silently did nothing. MythicMobs keys its skills by the YAML node name
 * exactly as the author typed it, so a skill headed {@code Fireball} is registered as {@code Fireball} — but
 * MythicMobs' own lookup trims and folds the caller's input, so casting it as {@code fireball} works there.
 * The membership check here was an exact {@code Set.contains}, so a definition written in any other casing
 * was refused <em>before the vendor was ever called</em>. Nothing threw, nothing logged a cause, and the
 * player got a message that named neither the skill nor the reason.
 *
 * <p>So matching folds case and casting does not: the ID handed back is the provider's own spelling, because
 * that is what MythicMobs answers to and rewriting it to the definition's casing would move the bug rather
 * than fix it.
 */
class SkillCatalogSnapshotTest {
    private static final SkillCatalogSnapshot CATALOG = catalog("Fireball", "ICE_SHARD", "heal");

    @Test
    void aSkillIsFoundWhateverCasingTheDefinitionUsed() {
        assertTrue(CATALOG.contains("Fireball"), "the provider's own spelling");
        assertTrue(CATALOG.contains("fireball"), "the casing a definition author is most likely to type");
        assertTrue(CATALOG.contains("FIREBALL"));
        assertTrue(CATALOG.contains("FiReBaLl"));
    }

    /**
     * And the cast uses the provider's spelling, not the definition's.
     *
     * <p>The half that makes the fold safe. Folding only the comparison and then casting whatever the
     * definition said would pass this catalog and fail at MythicMobs, which is the same silent failure one
     * layer further down.
     */
    @Test
    void theProvidersOwnSpellingIsWhatComesBack() {
        assertEquals("Fireball", CATALOG.canonical("fireball"));
        assertEquals("Fireball", CATALOG.canonical("FIREBALL"));
        assertEquals("ICE_SHARD", CATALOG.canonical("ice_shard"));
        assertEquals("heal", CATALOG.canonical("HEAL"));
    }

    /** Surrounding space is a typo, not a different skill — MythicMobs trims too. */
    @Test
    void surroundingSpaceIsForgiven() {
        assertTrue(CATALOG.contains("  fireball  "));
        assertEquals("Fireball", CATALOG.canonical(" Fireball "));
    }

    /** A skill the provider genuinely does not have still has to be refused. */
    @Test
    void anUnknownSkillIsStillRefused() {
        assertFalse(CATALOG.contains("meteor"));
        assertNull(CATALOG.canonical("meteor"));
        assertFalse(CATALOG.contains(""));
        assertFalse(CATALOG.contains("   "));
        assertFalse(CATALOG.contains(null));
        assertNull(CATALOG.canonical(null));
    }

    /** An empty catalog refuses everything rather than throwing, which is what a quarantine leaves behind. */
    @Test
    void anEmptyCatalogRefusesEverything() {
        SkillCatalogSnapshot empty = catalog();

        assertFalse(empty.contains("fireball"));
        assertNull(empty.canonical("fireball"));
    }

    /**
     * Two spellings differing only in case resolve the same way every time.
     *
     * <p>Not a case anyone should author, but folding makes it reachable. The obvious rule — first
     * registered wins — turns out to be unimplementable: the compact constructor stores {@code Set.copyOf},
     * which does not keep insertion order, so "first" is whatever the hash happens to yield and could
     * differ across restarts. A cast that works today and fails after a reboot is far harder to diagnose
     * than one that is consistently wrong, so the tie breaks on the name itself.
     */
    @Test
    void aCollidingRegistrationResolvesPredictably() {
        assertEquals("Dash", catalog("Dash", "dash").canonical("DASH"));
        assertEquals("Dash", catalog("dash", "Dash").canonical("DASH"),
                "the answer must not depend on which was registered first");
    }

    private static SkillCatalogSnapshot catalog(String... skillIds) {
        return new SkillCatalogSnapshot(
                1, new SkillProviderHealth(SkillProviderHealth.Status.AVAILABLE, "test"),
                new java.util.LinkedHashSet<>(java.util.Arrays.asList(skillIds)));
    }

    /** Guards the assumption the fold rests on: the set itself stays exactly what the provider gave. */
    @Test
    void theRegisteredIdsThemselvesAreNotRewritten() {
        assertEquals(Set.of("Fireball", "ICE_SHARD", "heal"), CATALOG.skillIds());
    }
}
