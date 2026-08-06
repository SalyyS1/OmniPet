package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.buff.PetStatBuff;
import io.github.salyvn.omnipet.core.buff.PetStatBuffProjection;
import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.incubation.RealizedStat;
import io.github.salyvn.omnipet.core.studio.StatModifierType;
import io.github.salyvn.omnipet.core.studio.StatRange;
import io.github.salyvn.omnipet.core.studio.StudioPetDraft;
import io.github.salyvn.omnipet.core.studio.StudioStat;

/**
 * A stat's journey from the Studio picker to the ID handed to the stat provider.
 *
 * <p>Four links, each already tested on its own and each passing: the Studio writes a stat into a
 * definition, the roller realises it onto a hatched pet, the pet stores it, the projection reads it back.
 * The chain still delivered nothing, because the ID the picker mints — {@code mythiclib:attack_damage} — is
 * not the ID MythicLib answers to, and no test ever compared the two ends.
 *
 * <p>That is the shape of this whole defect: every layer correct, the seam between them wrong. So this
 * follows one stat all the way through and asserts what comes out the far end.
 */
class StudioStatToOwnerBuffChainTest {
    /** What the MythicLib picker actually writes: its own namespaced, lower-cased form. */
    private static final String PICKER_ID = "mythiclib:attack_damage";
    /** And what it records alongside, straight from the provider. */
    private static final String VENDOR_ID = "ATTACK_DAMAGE";

    @Test
    void aPickedStatSurvivesTheStudioAndArrivesAsTheVendorId() {
        StudioPetDraft draft = draft(new StudioStat(
                PICKER_ID, StatModifierType.FLAT, new StatRange(5, 5),
                Map.of("vendorStatId", VENDOR_ID)));

        // The Studio's own raw node is what reaches disk, so read the stat back out of it.
        List<?> stats = (List<?>) draft.rawNode().get("stats");
        Map<?, ?> stat = (Map<?, ?>) stats.getFirst();
        assertEquals(PICKER_ID, stat.get("id"));
        assertEquals(VENDOR_ID, stat.get("vendorStatId"),
                "the vendor ID has to survive into the definition, or nothing downstream can recover it");

        // Roll it onto a pet exactly as a hatch would, then read it back as an owner buff.
        List<RealizedStat> rolled = DeterministicHatchRollService.rollStats(
                definition(draft), 50, 12345L);
        assertEquals(1, rolled.size());
        assertEquals(VENDOR_ID, rolled.getFirst().extensions().get("vendorStatId"),
                "the roller must carry the vendor ID onto the hatched pet");

        List<PetStatBuff> buffs = PetStatBuffProjection.read(hatched(rolled));
        assertEquals(1, buffs.size());
        assertEquals(VENDOR_ID, buffs.getFirst().statId(),
                "the far end of the chain must be the ID the provider answers to, not the picker's");
    }

    /** The rolled value lands inside the authored range, so a pet grants what its definition promised. */
    @Test
    void theValueThatArrivesIsTheOneTheDefinitionAuthorised() {
        StudioPetDraft draft = draft(new StudioStat(
                PICKER_ID, StatModifierType.FLAT, new StatRange(4, 10),
                Map.of("vendorStatId", VENDOR_ID)));

        List<PetStatBuff> buffs = PetStatBuffProjection.read(
                hatched(DeterministicHatchRollService.rollStats(definition(draft), 50, 999L)));

        double value = buffs.getFirst().value();
        assertTrue(value >= 4 && value <= 10, "rolled outside the authored range: " + value);
    }

    /** The modifier type survives too — a RELATIVE stat applied as FLAT would be silently wrong. */
    @Test
    void theModifierTypeSurvivesTheWholeChain() {
        StudioPetDraft draft = draft(new StudioStat(
                PICKER_ID, StatModifierType.RELATIVE, new StatRange(12, 12),
                Map.of("vendorStatId", VENDOR_ID)));

        List<PetStatBuff> buffs = PetStatBuffProjection.read(
                hatched(DeterministicHatchRollService.rollStats(definition(draft), 50, 7L)));

        assertEquals("RELATIVE", buffs.getFirst().modifierType());
    }

    /** A hand-typed vendor ID, with no picker metadata, must pass through untouched. */
    @Test
    void aHandTypedVendorIdIsNotRewritten() {
        StudioPetDraft draft = draft(new StudioStat(
                VENDOR_ID, StatModifierType.FLAT, new StatRange(3, 3), Map.of()));

        List<PetStatBuff> buffs = PetStatBuffProjection.read(
                hatched(DeterministicHatchRollService.rollStats(definition(draft), 50, 1L)));

        assertEquals(VENDOR_ID, buffs.getFirst().statId());
    }

    /** A definition with no stats produces a pet that grants none, rather than a malformed one. */
    @Test
    void aDefinitionWithNoStatsProducesNoBuffs() {
        StudioPetDraft draft = StudioPetDraft.create(
                "wolf", 0, PetTier.D,
                new HeadIcon("TEXTURE_URL", "https://example.invalid/wolf.png"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null), Map.of());

        assertTrue(DeterministicHatchRollService.rollStats(definition(draft), 50, 1L).isEmpty());
        assertTrue(PetStatBuffProjection.read(hatched(List.of())).isEmpty());
    }

    /** Only the picker's own namespace is stripped; another plugin's is not ours to touch. */
    @Test
    void aForeignNamespaceReachesTheProviderIntact() {
        StudioPetDraft draft = draft(new StudioStat(
                "someplugin:power", StatModifierType.FLAT, new StatRange(2, 2), Map.of()));

        List<PetStatBuff> buffs = PetStatBuffProjection.read(
                hatched(DeterministicHatchRollService.rollStats(definition(draft), 50, 1L)));

        assertEquals("someplugin:power", buffs.getFirst().statId());
        assertFalse(buffs.getFirst().statId().equals("POWER"));
    }

    private static StudioPetDraft draft(StudioStat stat) {
        return StudioPetDraft.create(
                        "wolf", 0, PetTier.D,
                        new HeadIcon("TEXTURE_URL", "https://example.invalid/wolf.png"),
                        new DisplayDefinition(DisplayDefinition.Provider.HEAD, null), Map.of())
                .withStats(List.of(stat));
    }

    /** The definition the Studio would have written to disk. */
    private static PetDefinition definition(StudioPetDraft draft) {
        return new PetDefinition(
                draft.id(), 1, draft.tier(), draft.icon(), draft.display(), draft.rawNode());
    }

    /** The pet a hatch would produce from those rolled stats, in the shape the projection reads. */
    private static io.github.salyvn.omnipet.core.domain.PetInstance hatched(List<RealizedStat> rolled) {
        List<Map<String, Object>> stats = new java.util.ArrayList<>(rolled.size());
        for (RealizedStat stat : rolled) {
            Map<String, Object> node = new java.util.LinkedHashMap<>(stat.extensions());
            node.put("id", stat.id());
            node.put("modifierType", stat.modifierType().name());
            node.put("value", stat.value());
            stats.add(node);
        }
        return new io.github.salyvn.omnipet.core.domain.PetInstance(
                java.util.UUID.randomUUID(), "wolf", 1, Map.of("stats", stats), Map.of());
    }
}
