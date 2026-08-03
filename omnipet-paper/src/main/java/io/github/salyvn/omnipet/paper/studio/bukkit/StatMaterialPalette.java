package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;

/**
 * Maps a stat's logical key to a semantic icon so the picker is scannable.
 *
 * <p>Every row previously rendered {@code PAPER}, which is why the picker was reported as "all paper
 * sheets". Matching is a lowercase substring test against the fixed table below, first match wins,
 * so the result is deterministic for a given key.
 *
 * <p>Presentation only. {@code StatLogicalIdentity.key} remains the identity used for selection, so a
 * wrong-looking icon is cosmetic and cannot affect what is saved.
 */
final class StatMaterialPalette {
    /**
     * Ordered keyword table. Order is the tie-breaker, so it is part of the contract.
     *
     * <p>The magic row deliberately precedes the health row: {@code mana_regeneration} contains both
     * {@code mana} and {@code regen}, and the magic reading is the correct one. {@code
     * health_regeneration} matches no magic keyword, so it still resolves to the health icon.
     */
    private static final List<Entry> ENTRIES = List.of(
            new Entry(List.of("attack", "damage", "weapon"), Material.IRON_SWORD),
            new Entry(List.of("defen", "armor", "resist"), Material.SHIELD),
            new Entry(List.of("magic", "mana", "spell"), Material.ENCHANTED_BOOK),
            new Entry(List.of("health", "regen", "heal"), Material.GOLDEN_APPLE),
            new Entry(List.of("speed", "movement", "haste"), Material.FEATHER),
            new Entry(List.of("crit", "luck"), Material.GOLD_NUGGET),
            new Entry(List.of("range", "projectile", "bow"), Material.ARROW));

    /** The icon used when no keyword matches — the previous behaviour for every row. */
    static final Material FALLBACK = Material.PAPER;

    /** Shown instead of a semantic icon when the stat is already selected. */
    static final Material SELECTED = Material.LIME_DYE;

    /** Shown when the provider catalog is degraded, so the row reads as unusable. */
    static final Material DEGRADED = Material.RED_STAINED_GLASS_PANE;

    private StatMaterialPalette() {}

    /** The semantic icon for a logical stat key, ignoring selection state. */
    static Material iconFor(String logicalKey) {
        if (logicalKey == null || logicalKey.isBlank()) return FALLBACK;
        String key = logicalKey.toLowerCase(Locale.ROOT);
        for (Entry entry : ENTRIES) {
            for (String keyword : entry.keywords()) {
                if (key.contains(keyword)) return entry.material();
            }
        }
        return FALLBACK;
    }

    /**
     * The icon actually rendered for a row. Selection and catalog health override meaning, because a
     * player scanning the screen needs state before category.
     */
    static Material rowIcon(String logicalKey, boolean selected, boolean degraded) {
        if (degraded) return DEGRADED;
        if (selected) return SELECTED;
        return iconFor(logicalKey);
    }

    private record Entry(List<String> keywords, Material material) {
        private Entry {
            Objects.requireNonNull(material, "material");
            keywords = List.copyOf(keywords);
        }
    }
}
