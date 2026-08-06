package io.github.salyvn.omnipet.paper.buff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.buff.PetStatBuff;

/**
 * What the stat diagnostic says, for each reason stats can fail to reach a player.
 *
 * <p>Every one of these states previously looked identical from outside: silent, with the plugin reporting
 * success. That is why "pet stats do nothing" took several rounds to pin down — the failing states could
 * not be told apart without reading the code, and no amount of reading settles which one a given server is
 * in. Each has to name itself.
 */
class OwnerBuffDiagnosticsTest {
    @Test
    void anAbsentProviderIsNamedAsTheReason() {
        List<String> lines = OwnerBuffDiagnostics.describe(false, null, 1, buffs("ATTACK_DAMAGE"), null);

        assertTrue(joined(lines).contains("MythicLib is not installed"));
        assertTrue(joined(lines).contains("only the stat numbers are inert"),
                "an operator has to know the rest of the pet still works");
    }

    @Test
    void aQuarantinedAdapterReportsWhy() {
        List<String> lines = OwnerBuffDiagnostics.describe(
                true, "adapter quarantined: NoSuchMethodException", 1, buffs("ATTACK_DAMAGE"), null);

        assertTrue(joined(lines).contains("not usable"));
        assertTrue(joined(lines).contains("NoSuchMethodException"), "the cause has to survive to the reader");
    }

    /** No pet out and a pet with no stats are different problems and must read differently. */
    @Test
    void noActivePetIsDistinguishedFromAPetWithNoStats() {
        String none = joined(OwnerBuffDiagnostics.describe(true, null, 0, List.of(), Set.of("ATTACK_DAMAGE")));
        String noStats = joined(OwnerBuffDiagnostics.describe(true, null, 1, List.of(), Set.of("ATTACK_DAMAGE")));

        assertTrue(none.contains("No pet is out"));
        assertFalse(none.contains("grant no stats"));
        assertTrue(noStats.contains("grant no stats"));
        assertFalse(noStats.contains("No pet is out"));
    }

    /**
     * A healthy server gets a verdict, not an audit.
     *
     * <p>The report used to print every stat on its own line whether or not anything was wrong, so the one
     * sentence that answers the question was buried under a list an operator could already read off their
     * own character sheet. Detail is for the broken cases.
     */
    @Test
    void aWorkingSetupIsReportedInOneLineRatherThanListed() {
        List<String> lines = OwnerBuffDiagnostics.describe(
                true, null, 1, buffs("ATTACK_DAMAGE", "MAX_HEALTH"), Set.of("ATTACK_DAMAGE", "MAX_HEALTH"));
        String text = joined(lines);

        assertTrue(text.startsWith("OK — "), text);
        assertTrue(text.contains("2 stat(s)"), text);
        assertFalse(text.contains("ok  ATTACK_DAMAGE"), "the per-stat list is what made this too long");
        assertTrue(lines.size() <= 4, "a healthy answer must stay short: " + lines.size() + " lines");
    }

    /** An operator who wants the full list asks for it, and then gets it. */
    @Test
    void theFullListIsAvailableOnRequest() {
        String text = joined(OwnerBuffDiagnostics.describeVerbose(
                true, null, 1, buffs("ATTACK_DAMAGE"), Set.of("ATTACK_DAMAGE", "MAX_HEALTH")));

        assertTrue(text.startsWith("OK — "), "the verdict still comes first: " + text);
        assertTrue(text.contains("ok  ATTACK_DAMAGE"), text);
    }

    /** The case that was invisible: an ID MythicLib never registered. */
    @Test
    void anUnknownStatIsNamedAlongsideWhatMythicLibActuallyKnows() {
        String text = joined(OwnerBuffDiagnostics.describe(
                true, null, 1, buffs("mythiclib:attack_damage"), Set.of("ATTACK_DAMAGE", "MAX_HEALTH")));

        assertTrue(text.startsWith("BROKEN —"), text);
        assertTrue(text.contains("mythiclib:attack_damage"), "the bad ID has to be named: " + text);
        assertTrue(text.contains("does not know 1"), text);
        assertTrue(text.contains("ATTACK_DAMAGE"), "the operator needs the name to correct it to");
    }

    @Test
    void anUnreadableRegistryIsAdmittedRatherThanGuessed() {
        String text = joined(OwnerBuffDiagnostics.describe(true, null, 1, buffs("ANYTHING"), null));

        assertTrue(text.contains("unverified"),
                "claiming a stat is fine when the registry could not be read would be a lie");
        assertFalse(text.contains("BAD"));
    }

    /** A long registry is sampled, so the line that matters is not buried under a hundred names. */
    @Test
    void aLargeRegistryIsPreviewedRatherThanDumped() {
        Set<String> many = new java.util.LinkedHashSet<>();
        for (int index = 0; index < 60; index++) many.add("STAT_" + index);

        String text = joined(OwnerBuffDiagnostics.describe(true, null, 1, buffs("NOPE"), many));

        assertTrue(text.contains("60 total"));
        assertFalse(text.contains("STAT_59"), "the tail must be summarised, not printed");
    }

    private static List<PetStatBuff> buffs(String... statIds) {
        UUID pet = UUID.randomUUID();
        return java.util.Arrays.stream(statIds)
                .map(id -> new PetStatBuff(pet, id, "FLAT", 5))
                .toList();
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }
}
