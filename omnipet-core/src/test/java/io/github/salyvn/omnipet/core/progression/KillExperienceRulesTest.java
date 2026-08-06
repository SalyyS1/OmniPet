package io.github.salyvn.omnipet.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * What a kill is worth to a pet.
 *
 * <p>The rule an operator writes, separated from the listener that applies it, so the arithmetic can be
 * read without a server running.
 */
class KillExperienceRulesTest {
    @Test
    void anUnlistedMobIsWorthTheDefault() {
        KillExperienceRules rules = new KillExperienceRules(10, Map.of(), KillExperienceRules.Share.EACH);

        assertEquals(10, rules.experienceFor("ZOMBIE", 1));
        assertEquals(10, rules.experienceFor("SkeletalKnight", 1));
    }

    @Test
    void aNamedMobOverridesTheDefault() {
        KillExperienceRules rules = new KillExperienceRules(
                10, Map.of("SkeletalKnight", 250.0), KillExperienceRules.Share.EACH);

        assertEquals(250, rules.experienceFor("SkeletalKnight", 1));
        assertEquals(10, rules.experienceFor("ZOMBIE", 1));
    }

    /**
     * Mob IDs match without regard to case.
     *
     * <p>The two sources genuinely disagree: a vanilla type is an uppercase enum constant and a MythicMobs
     * name is whatever its author typed. An operator writing {@code zombie} beside {@code SkeletalKnight}
     * is writing the obvious thing, and a rule that silently paid the default for one of them would be very
     * hard to notice — the number would just be quietly wrong.
     */
    @Test
    void mobIdsMatchWhateverCasingTheOperatorUsed() {
        KillExperienceRules rules = new KillExperienceRules(
                1, Map.of("zombie", 40.0, "SkeletalKnight", 250.0), KillExperienceRules.Share.EACH);

        assertEquals(40, rules.experienceFor("ZOMBIE", 1));
        assertEquals(40, rules.experienceFor("Zombie", 1));
        assertEquals(250, rules.experienceFor("skeletalknight", 1));
        assertEquals(250, rules.experienceFor("  SKELETALKNIGHT  ", 1), "and surrounding space is a typo");
    }

    /** Zero is how a mob is excluded without deleting the feature. */
    @Test
    void aMobWorthZeroGrantsNothing() {
        KillExperienceRules rules = new KillExperienceRules(
                10, Map.of("ARMOR_STAND", 0.0), KillExperienceRules.Share.EACH);

        assertEquals(0, rules.experienceFor("ARMOR_STAND", 1));
        assertTrue(rules.names("armor_stand"), "an explicit zero is still an entry");
    }

    /**
     * Every pet earns the full amount by default.
     *
     * <p>Splitting would mean a player's second pet halves the first one's growth, so unlocking a slot
     * they paid for makes each pet progress more slowly. That is a strange reward for spending, which is
     * why the default is the other way and splitting is opt-in.
     */
    @Test
    void byDefaultEveryActivePetEarnsTheFullAmount() {
        KillExperienceRules rules = new KillExperienceRules(30, Map.of(), KillExperienceRules.Share.EACH);

        assertEquals(30, rules.experienceFor("ZOMBIE", 1));
        assertEquals(30, rules.experienceFor("ZOMBIE", 3));
    }

    /** An operator who wants total experience fixed per kill asks for SPLIT. */
    @Test
    void splittingDividesOneKillBetweenThePetsOut() {
        KillExperienceRules rules = new KillExperienceRules(30, Map.of(), KillExperienceRules.Share.SPLIT);

        assertEquals(30, rules.experienceFor("ZOMBIE", 1));
        assertEquals(15, rules.experienceFor("ZOMBIE", 2));
        assertEquals(10, rules.experienceFor("ZOMBIE", 3));
    }

    /** No pets out is no experience, whatever the rules say, and never a division by zero. */
    @Test
    void noActivePetsEarnsNothing() {
        assertEquals(0, new KillExperienceRules(30, Map.of(), KillExperienceRules.Share.SPLIT)
                .experienceFor("ZOMBIE", 0));
        assertEquals(0, new KillExperienceRules(30, Map.of(), KillExperienceRules.Share.EACH)
                .experienceFor("ZOMBIE", -1));
    }

    /**
     * A rule set that can never pay says so, so the listener can skip the work entirely.
     *
     * <p>This runs on every mob death on the server, so a server that has not enabled the feature must pay
     * as close to nothing as possible.
     */
    @Test
    void aRuleSetThatCanNeverPayReportsItselfDisabled() {
        assertFalse(KillExperienceRules.disabled().enabled());
        assertFalse(new KillExperienceRules(0, Map.of(), KillExperienceRules.Share.EACH).enabled());
        assertFalse(new KillExperienceRules(0, Map.of("ZOMBIE", 0.0), KillExperienceRules.Share.EACH).enabled());

        assertTrue(new KillExperienceRules(1, Map.of(), KillExperienceRules.Share.EACH).enabled());
        assertTrue(new KillExperienceRules(0, Map.of("ZOMBIE", 5.0), KillExperienceRules.Share.EACH).enabled(),
                "a default of zero with one paying mob is still enabled");
    }

    /** Nonsense in the config is refused at load rather than producing a strange number per kill. */
    @Test
    void invalidRulesAreRefusedWhenTheyAreBuilt() {
        assertThrows(IllegalArgumentException.class,
                () -> new KillExperienceRules(-1, Map.of(), KillExperienceRules.Share.EACH));
        assertThrows(IllegalArgumentException.class,
                () -> new KillExperienceRules(Double.NaN, Map.of(), KillExperienceRules.Share.EACH));
        assertThrows(IllegalArgumentException.class,
                () -> new KillExperienceRules(1, Map.of("ZOMBIE", -5.0), KillExperienceRules.Share.EACH));
        assertThrows(IllegalArgumentException.class,
                () -> new KillExperienceRules(1, Map.of("ZOMBIE", Double.POSITIVE_INFINITY),
                        KillExperienceRules.Share.EACH));

        Map<String, Double> blankKey = new LinkedHashMap<>();
        blankKey.put("  ", 5.0);
        assertThrows(IllegalArgumentException.class,
                () -> new KillExperienceRules(1, blankKey, KillExperienceRules.Share.EACH));
    }
}
