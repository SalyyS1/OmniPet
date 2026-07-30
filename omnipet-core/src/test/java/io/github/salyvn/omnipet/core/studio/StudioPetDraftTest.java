package io.github.salyvn.omnipet.core.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;

class StudioPetDraftTest {
    @Test
    void typedUpdatesPreserveUnknownNodesWithoutMutatingSource() {
        Map<String, Object> classification = new LinkedHashMap<>(Map.of("tier", "D", "vendorFlag", true));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("classification", classification);
        source.put("vendor", new LinkedHashMap<>(Map.of("nested", List.of("one", "two"))));
        PetDefinition definition = new PetDefinition("fox", 4, PetTier.D, icon(), display(), source);

        StudioPetDraft original = StudioPetDraft.edit(definition, "hash-4", 9);
        StudioPetDraft updated = original.withTier(PetTier.S).withRawValue("kept", "vendor", "newKey");

        assertNotSame(original, updated);
        assertEquals("D", classification.get("tier"));
        assertEquals(true, nestedMap(updated.rawNode(), "classification").get("vendorFlag"));
        assertEquals("S", nestedMap(updated.rawNode(), "classification").get("tier"));
        assertEquals(List.of("one", "two"), nestedMap(updated.rawNode(), "vendor").get("nested"));
        assertEquals("kept", nestedMap(updated.rawNode(), "vendor").get("newKey"));
        assertThrows(UnsupportedOperationException.class, () -> updated.rawNode().put("lost", true));
    }

    @Test
    void createAndEditIdsAreImmutableAfterAssignment() {
        StudioPetDraft create = draft(null, StudioPetDraft.Mode.CREATE);
        StudioPetDraft assigned = create.withId("new_pet");

        assertEquals("new_pet", assigned.id());
        assertThrows(IllegalStateException.class, () -> assigned.withId("other_pet"));
        assertThrows(IllegalStateException.class, () -> draft("fox", StudioPetDraft.Mode.EDIT).withId("wolf"));
        assertEquals("fox", draft("fox", StudioPetDraft.Mode.EDIT).withId("fox").id());
    }

    @Test
    void cloneToCreatesACreateDraftWithoutChangingTheSourceId() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("definitionId", "fox");
        raw.put("revision", 2);
        raw.put("custom", Map.of("kept", true));
        StudioPetDraft source = StudioPetDraft.edit(
                new PetDefinition("fox", 2, PetTier.D, icon(), display(), raw),
                StudioPetDraft.semanticHash(raw), 5);

        StudioPetDraft clone = source.cloneTo("wolf");

        assertEquals("fox", source.id());
        assertEquals("wolf", clone.id());
        assertEquals(StudioPetDraft.Mode.CREATE, clone.mode());
        assertEquals(0, clone.baseRevision());
        assertEquals("", clone.baseSemanticHash());
        assertEquals("wolf", clone.rawNode().get("definitionId"));
        assertEquals(0, clone.rawNode().get("revision"));
        assertEquals(Map.of("kept", true), clone.rawNode().get("custom"));
        assertEquals("fox", source.rawNode().get("definitionId"));
        assertThrows(IllegalStateException.class, () -> clone.withId("other"));
    }

    @Test
    void draftCollectionsAndNestedExtensionsAreImmutableCopies() {
        List<StudioStat> sourceStats = new ArrayList<>();
        sourceStats.add(new StudioStat("mythiclib:attack_damage", StatModifierType.FLAT,
                new StatRange(10, 50), Map.of("vendor", Map.of("flag", true))));

        StudioPetDraft draft = draft("fox", StudioPetDraft.Mode.EDIT).withStats(sourceStats);
        sourceStats.clear();

        assertEquals(1, draft.stats().size());
        StudioStat stat = new StudioStat("other:stat", StatModifierType.FLAT, new StatRange(0, 1), Map.of());
        assertThrows(UnsupportedOperationException.class, () -> draft.stats().add(stat));
        assertThrows(UnsupportedOperationException.class, () -> draft.stats().getFirst().extensions().put("x", 1));
    }

    @Test
    void editHydratesLegacyStatKeysAndPreservesEntryExtensions() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("stats", List.of(Map.of(
                "id", "ATTACK_DAMAGE", "type", "FLAT", "min", 10, "max", 50,
                "vendorFlag", true)));
        PetDefinition definition = new PetDefinition("fox", 4, PetTier.D, icon(), display(), raw);

        StudioPetDraft draft = StudioPetDraft.edit(definition, StudioPetDraft.semanticHash(raw), 9);

        assertEquals(1, draft.stats().size());
        assertEquals(StatModifierType.FLAT, draft.stats().getFirst().modifierType());
        assertEquals(true, draft.stats().getFirst().extensions().get("vendorFlag"));
    }

    @Test
    void typedReplacementPreservesMatchingNestedExtensions() {
        Map<String, Object> raw = new LinkedHashMap<>();
        Map<String, Object> statNode = new LinkedHashMap<>(Map.of("id", "ATTACK_DAMAGE", "modifierType", "FLAT",
                "min", 10, "max", 50, "statVendor", true));
        statNode.put("vendorNull", null);
        raw.put("stats", List.of(statNode));
        raw.put("rarity", Map.of("bands", List.of(Map.of("id", "COMMON", "qualityMin", 0,
                "qualityMax", 100, "weight", 1, "hatchMultiplier", 1.25, "rarityVendor", true))));
        raw.put("progression", Map.of("maxLevel", 50, "experienceFormula", "level * 2",
                "samples", Map.of("level", 1), "progressionVendor", true));
        raw.put("skills", List.of(Map.of("provider", "MYTHICMOBS", "id", "omnipet:dash",
                "trigger", "ACTIVE", "cooldown", "PT5S", "chance", 0.5, "staminaCost", 2,
                "targetPolicy", "OWNER", "skillVendor", true)));
        raw.put("release", Map.of("mode", "MANUAL", "releaseVendor", true));
        StudioPetDraft draft = StudioPetDraft.edit(
                new PetDefinition("fox", 4, PetTier.D, icon(), display(), raw),
                StudioPetDraft.semanticHash(raw), 9);

        StudioPetDraft updated = draft
                .withStats(List.of(new StudioStat("ATTACK_DAMAGE", StatModifierType.RELATIVE,
                        new StatRange(20, 60), Map.of())))
                .withRarityBands(List.of(new RarityBand("COMMON", 10, 90, 2,
                        Map.of("hatchMultiplier", 1.5))))
                .withProgression(new ProgressionFields(75, "level * 3", Map.of("level", 1.0), Map.of()))
                .withSkills(List.of(new SkillReference("MYTHICMOBS", "omnipet:dash", "PASSIVE",
                        Duration.ofSeconds(3), 0.75, 1, "OWNER", Map.of())))
                .withReleasePolicy(new ReleasePolicy("REWARD", Map.of()));

        assertEquals(true, updated.stats().getFirst().extensions().get("statVendor"));
        assertEquals(true, updated.stats().getFirst().extensions().containsKey("vendorNull"));
        assertEquals(null, updated.stats().getFirst().extensions().get("vendorNull"));
        assertEquals(true, updated.rarityBands().getFirst().extensions().get("rarityVendor"));
        assertEquals(1.5, updated.rarityBands().getFirst().extensions().get("hatchMultiplier"));
        assertEquals(true, updated.progression().extensions().get("progressionVendor"));
        assertEquals(true, updated.skills().getFirst().extensions().get("skillVendor"));
        assertEquals(true, updated.releasePolicy().extensions().get("releaseVendor"));
    }

    private static StudioPetDraft draft(String id, StudioPetDraft.Mode mode) {
        return new StudioPetDraft(id, mode, 2, "hash", 5, PetTier.D, icon(), display(), Map.of(),
                List.of(), List.of(), null, List.of(), Map.of(), null);
    }

    private static HeadIcon icon() {
        return new HeadIcon("TEXTURE_URL", "https://example.invalid/fox.png");
    }

    private static DisplayDefinition display() {
        return new DisplayDefinition(DisplayDefinition.Provider.HEAD, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }
}
