package io.github.salyvn.omnipet.paper.studio.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class StatMaterialPaletteTest {
    @Test
    void everyKeywordGroupResolvesToItsSemanticIcon() {
        assertEquals(Material.IRON_SWORD, StatMaterialPalette.iconFor("mythiclib:attack_damage"));
        assertEquals(Material.IRON_SWORD, StatMaterialPalette.iconFor("weapon_power"));
        assertEquals(Material.SHIELD, StatMaterialPalette.iconFor("defense"));
        assertEquals(Material.SHIELD, StatMaterialPalette.iconFor("magic_resistance"));
        assertEquals(Material.GOLDEN_APPLE, StatMaterialPalette.iconFor("max_health"));
        assertEquals(Material.GOLDEN_APPLE, StatMaterialPalette.iconFor("health_regeneration"));
        assertEquals(Material.FEATHER, StatMaterialPalette.iconFor("movement_speed"));
        assertEquals(Material.ENCHANTED_BOOK, StatMaterialPalette.iconFor("mana_regeneration"));
        assertEquals(Material.GOLD_NUGGET, StatMaterialPalette.iconFor("critical_strike_chance"));
        assertEquals(Material.ARROW, StatMaterialPalette.iconFor("bow_power"));
        assertEquals(Material.ARROW, StatMaterialPalette.iconFor("projectile_count"));
    }

    @Test
    void anUnmatchedKeyKeepsThePreviousPaperDefault() {
        assertEquals(Material.PAPER, StatMaterialPalette.iconFor("cooldown_reduction"));
        assertEquals(StatMaterialPalette.FALLBACK, StatMaterialPalette.iconFor("something_unknown"));
    }

    @Test
    void matchingIsCaseInsensitiveAndSubstringBased() {
        assertEquals(Material.IRON_SWORD, StatMaterialPalette.iconFor("MYTHICLIB:ATTACK_DAMAGE"));
        assertEquals(Material.IRON_SWORD, StatMaterialPalette.iconFor("PhysicalAttackPower"));
    }

    @Test
    void aBlankOrNullKeyFallsBackRatherThanThrowing() {
        assertEquals(Material.PAPER, StatMaterialPalette.iconFor(null));
        assertEquals(Material.PAPER, StatMaterialPalette.iconFor(""));
        assertEquals(Material.PAPER, StatMaterialPalette.iconFor("   "));
    }

    @Test
    void theFirstMatchingRowWinsSoResultsAreDeterministic() {
        // These keys match more than one row, and the table order decides. Pinning the outcomes
        // here means a future reorder cannot silently reshuffle the picker.
        // "magic_resistance": resist (shield) precedes magic (book).
        assertEquals(Material.SHIELD, StatMaterialPalette.iconFor("magic_resistance"));
        // "mana_regeneration": magic row precedes health, so mana reads as magic, not regen.
        assertEquals(Material.ENCHANTED_BOOK, StatMaterialPalette.iconFor("mana_regeneration"));
        // "projectile_damage": damage (sword) precedes projectile (arrow) — it is still damage.
        assertEquals(Material.IRON_SWORD, StatMaterialPalette.iconFor("projectile_damage"));
        assertSame(StatMaterialPalette.iconFor("magic_resistance"),
                StatMaterialPalette.iconFor("magic_resistance"));
    }

    @Test
    void selectionAndCatalogHealthOverrideTheSemanticIcon() {
        assertEquals(StatMaterialPalette.SELECTED,
                StatMaterialPalette.rowIcon("attack_damage", true, false));
        assertEquals(StatMaterialPalette.DEGRADED,
                StatMaterialPalette.rowIcon("attack_damage", false, true));
        // Degraded beats selected: an unusable catalog is the more urgent fact.
        assertEquals(StatMaterialPalette.DEGRADED,
                StatMaterialPalette.rowIcon("attack_damage", true, true));
        assertEquals(Material.IRON_SWORD,
                StatMaterialPalette.rowIcon("attack_damage", false, false));
    }

    @Test
    void theSelectedAndDegradedIconsAreDistinctFromTheFallback() {
        assertNotEquals(StatMaterialPalette.FALLBACK, StatMaterialPalette.SELECTED);
        assertNotEquals(StatMaterialPalette.FALLBACK, StatMaterialPalette.DEGRADED);
        assertNotEquals(StatMaterialPalette.SELECTED, StatMaterialPalette.DEGRADED);
    }
}
