package io.github.salyvn.omnipet.paper.studio.bukkit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.catalog.CatalogCacheKey;
import io.github.salyvn.omnipet.core.catalog.CatalogHealth;
import io.github.salyvn.omnipet.core.catalog.CatalogSnapshot;
import io.github.salyvn.omnipet.core.catalog.StatCatalogEntry;
import io.github.salyvn.omnipet.core.studio.StatModifierType;
import io.github.salyvn.omnipet.core.studio.StatRange;
import io.github.salyvn.omnipet.core.studio.StudioConflictException;
import io.github.salyvn.omnipet.core.studio.StudioStat;

class StudioCatalogSelectionsTest {
    private static final CatalogCacheKey KEY = new CatalogCacheKey(4, "test");

    @Test
    void revalidatesPickerStatsAgainstCurrentIdsAndModifiers() {
        StudioStat selected = new StudioStat("mythiclib:attack_damage", StatModifierType.FLAT,
                new StatRange(1, 2), Map.of("vendorStatId", "ATTACK_DAMAGE"));
        StatCatalogEntry current = new StatCatalogEntry("mythiclib:attack_damage", "Attack Damage",
                Set.of(StatModifierType.FLAT), "MythicLib", CatalogHealth.AVAILABLE, 4,
                Map.of("vendorStatId", "ATTACK_DAMAGE"));

        assertDoesNotThrow(() -> StudioCatalogSelections.validate(
                CatalogSnapshot.available(KEY, List.of(current)), List.of(selected)));
        assertThrows(StudioConflictException.class, () -> StudioCatalogSelections.validate(
                CatalogSnapshot.available(KEY, List.of()), List.of(selected)));
        assertThrows(StudioConflictException.class, () -> StudioCatalogSelections.validate(
                CatalogSnapshot.available(KEY, List.of(new StatCatalogEntry("mythiclib:attack_damage", "Attack Damage",
                        Set.of(StatModifierType.RELATIVE), "MythicLib", CatalogHealth.AVAILABLE, 4,
                        Map.of("vendorStatId", "ATTACK_DAMAGE")))), List.of(selected)));
    }

    @Test
    void unavailableCatalogKeepsManualFallbackWritable() {
        StudioStat manual = new StudioStat("ATTACK_DAMAGE", StatModifierType.FLAT,
                new StatRange(1, 2), Map.of());
        assertDoesNotThrow(() -> StudioCatalogSelections.validate(
                CatalogSnapshot.unavailable(KEY, "missing"), List.of(manual)));

        StudioStat pickerStat = new StudioStat("mythiclib:attack_damage", StatModifierType.FLAT,
                new StatRange(1, 2), Map.of("vendorStatId", "ATTACK_DAMAGE"));
        assertThrows(StudioConflictException.class, () -> StudioCatalogSelections.validate(
                CatalogSnapshot.unavailable(KEY, "missing"), List.of(pickerStat)));
    }
}
