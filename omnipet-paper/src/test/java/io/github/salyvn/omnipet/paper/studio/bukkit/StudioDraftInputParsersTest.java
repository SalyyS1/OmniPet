package io.github.salyvn.omnipet.paper.studio.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.catalog.CatalogHealth;
import io.github.salyvn.omnipet.core.catalog.StatCatalogEntry;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.studio.StatModifierType;
import io.github.salyvn.omnipet.core.studio.StatRange;
import io.github.salyvn.omnipet.core.studio.StudioStat;

class StudioDraftInputParsersTest {
    @Test
    void parsesEveryEditorFieldWithoutVendorApis() {
        assertEquals("TEXTURE_URL", StudioDraftInputParsers.icon("TEXTURE_URL https://textures.minecraft.net/texture/x").source());
        assertEquals("MODELENGINE", StudioDraftInputParsers.display("MODELENGINE dragon").provider().name());
        assertEquals(1, StudioDraftInputParsers.stats("ATTACK_DAMAGE FLAT 10 50").size());
        assertEquals(1, StudioDraftInputParsers.rarity("COMMON 0 100 1 1.25").size());
        assertEquals(50, StudioDraftInputParsers.progression("50;level * 2").maxLevel());
        assertEquals(1, StudioDraftInputParsers.skills("MYTHICMOBS:omnipet:dash|ACTIVE|5s|0.5|2|OWNER").size());
        assertEquals("follow", StudioDraftInputParsers.behavior("mode=follow").get("mode"));
        assertEquals("MANUAL", StudioDraftInputParsers.release("manual").mode());
    }

    @Test
    void rejectsMalformedEditorInput() {
        assertThrows(IllegalArgumentException.class, () -> StudioDraftInputParsers.icon("HEAD nope"));
        assertThrows(IllegalArgumentException.class, () -> StudioDraftInputParsers.stats("ATTACK_DAMAGE FLAT 50 10"));
        assertThrows(IllegalArgumentException.class, () -> StudioDraftInputParsers.rarity("COMMON 100 0 1 1"));
        assertThrows(IllegalArgumentException.class, () -> StudioDraftInputParsers.progression("50;level + unknown"));
        assertThrows(IllegalArgumentException.class, () -> StudioDraftInputParsers.skills("MYTHICMOBS:dash|ACTIVE|bad|1|0|OWNER"));
    }

    @Test
    void parsesAndUpsertsASelectedCatalogStat() {
        StatCatalogEntry entry = new StatCatalogEntry("mythiclib:attack_damage", "Attack Damage",
                Set.of(StatModifierType.FLAT), "MythicLib", CatalogHealth.AVAILABLE, 4,
                Map.of("vendorStatId", "ATTACK_DAMAGE"));
        StudioStat previous = new StudioStat(entry.id(), StatModifierType.FLAT, new StatRange(1, 2), Map.of());

        StudioStat parsed = StudioDraftInputParsers.catalogStat("FLAT 10 50", entry);
        List<StudioStat> result = StudioDraftInputParsers.upsertStat(List.of(previous), parsed);

        assertEquals(1, result.size());
        assertEquals(new StatRange(10, 50), result.getFirst().range());
        assertEquals("ATTACK_DAMAGE", result.getFirst().extensions().get("vendorStatId"));
        assertEquals(1, StudioDraftInputParsers.upsertStat(
                List.of(new StudioStat("ATTACK_DAMAGE", StatModifierType.FLAT, new StatRange(1, 2), Map.of())),
                parsed).size());
        assertThrows(IllegalArgumentException.class,
                () -> StudioDraftInputParsers.catalogStat("RELATIVE 10 50", entry));
    }

    @Test
    void hardDeleteConfirmationRequiresTheExactDefinitionId() {
        assertEquals("wolf", StudioDraftInputParsers.exactDefinitionId("wolf", "wolf"));
        assertThrows(IllegalArgumentException.class,
                () -> StudioDraftInputParsers.exactDefinitionId("Wolf", "wolf"));
        assertThrows(IllegalArgumentException.class,
                () -> StudioDraftInputParsers.exactDefinitionId(" wolf ", "wolf"));
    }

    @Test
    void aPastedBase64HeadIsAcceptedAsASingleToken() {
        String pasted = java.util.Base64.getEncoder().encodeToString(
                ("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/"
                        + "a".repeat(64) + "\"}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        HeadIcon icon = StudioDraftInputParsers.icon(pasted);

        assertEquals("BASE64", icon.source());
        assertEquals(pasted, icon.value());
    }

    @Test
    void aBareTextureUrlAndHashAreDetectedWithoutASourceKeyword() {
        assertEquals("TEXTURE_URL",
                StudioDraftInputParsers.icon("https://textures.minecraft.net/texture/x").source());
        assertEquals("https://textures.minecraft.net/texture/" + "b".repeat(64),
                StudioDraftInputParsers.icon("b".repeat(64)).value());
    }

    @Test
    void bothTheTwoTokenAndThreeTokenStatFormsProduceTheSameStat() {
        StatCatalogEntry entry = entry(StatModifierType.FLAT);

        StudioStat preselected = StudioDraftInputParsers.catalogStat("10 50", entry, StatModifierType.FLAT);
        StudioStat fullForm = StudioDraftInputParsers.catalogStat("FLAT 10 50", entry, StatModifierType.FLAT);

        assertEquals(fullForm, preselected);
        assertEquals(new StatRange(10, 50), preselected.range());
        assertEquals(StatModifierType.FLAT, preselected.modifierType());
    }

    @Test
    void anExplicitModifierOverridesTheClickedOne() {
        StatCatalogEntry entry = entry(StatModifierType.FLAT, StatModifierType.RELATIVE);

        StudioStat parsed = StudioDraftInputParsers.catalogStat("RELATIVE 10 50", entry, StatModifierType.FLAT);

        assertEquals(StatModifierType.RELATIVE, parsed.modifierType());
    }

    @Test
    void twoTokensWithoutAPreselectedModifierAskForTheFullForm() {
        StatCatalogEntry entry = entry(StatModifierType.FLAT);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> StudioDraftInputParsers.catalogStat("10 50", entry, null));

        assertEquals("expected: <modifier> <min> <max>", failure.getMessage());
    }

    @Test
    void aPreselectedModifierTheStatDoesNotSupportIsStillRejected() {
        StatCatalogEntry entry = entry(StatModifierType.FLAT);

        assertThrows(IllegalArgumentException.class,
                () -> StudioDraftInputParsers.catalogStat("10 50", entry, StatModifierType.RELATIVE));
    }

    @Test
    void aWrongTokenCountReportsTheFormatTheOperatorIsActuallyIn() {
        StatCatalogEntry entry = entry(StatModifierType.FLAT);

        assertEquals("expected: <min> <max>", assertThrows(IllegalArgumentException.class,
                () -> StudioDraftInputParsers.catalogStat("10", entry, StatModifierType.FLAT)).getMessage());
        assertEquals("expected: <modifier> <min> <max>", assertThrows(IllegalArgumentException.class,
                () -> StudioDraftInputParsers.catalogStat("FLAT 10 50 90", entry, null)).getMessage());
    }

    private static StatCatalogEntry entry(StatModifierType... supported) {
        return new StatCatalogEntry("mythiclib:attack_damage", "Attack Damage",
                Set.of(supported), "MythicLib", CatalogHealth.AVAILABLE, 4,
                Map.of("vendorStatId", "ATTACK_DAMAGE"));
    }
}
