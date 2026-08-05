package io.github.salyvn.omnipet.paper.studio.bukkit;

import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;
import io.github.salyvn.omnipet.paper.gui.MenuPage;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.catalog.CatalogHealth;
import io.github.salyvn.omnipet.core.catalog.StatCatalogEntry;
import io.github.salyvn.omnipet.core.studio.StatLogicalIdentity;
import io.github.salyvn.omnipet.core.studio.StatModifierType;
import io.github.salyvn.omnipet.core.studio.StudioStat;
import io.github.salyvn.omnipet.paper.text.Durations;

/**
 * The stat picker and modifier screens, split out of {@link StudioInventoryRenderer} to keep both
 * files under the size limit.
 *
 * <p>Rows carry a semantic icon and grouped lore instead of 45 identical paper sheets printing
 * {@code Set.toString()} of the supported modifiers.
 */
final class StudioStatScreens {
    private StudioStatScreens() {}

    /** The paginated catalog picker. */
    static Inventory picker(StudioState state, StudioInventoryRenderer.ScreenFactory factory) {
        Map<Integer, StudioAction> actions = new HashMap<>();
        Inventory inventory = factory.create(
                state, StudioInventoryHolder.Screen.STAT_PICKER, actions, 54, Messages.line(MessageKey.GUI_TITLE_STUDIO_STATS));

        boolean degraded = state.statSnapshot == null || state.statSnapshot.health() != CatalogHealth.AVAILABLE;
        List<StatCatalogEntry> entries = visibleEntries(state);
        MenuPage page = MenuPage.of(entries.size(), 45, state.statPage);
        state.statPage = page.index();
        for (int index = page.firstItem(); index < page.lastItemExclusive(); index++) {
            StatCatalogEntry entry = entries.get(index);
            String logicalKey = StatLogicalIdentity.key(entry.id(), entry.extensions());
            StudioStat selected = state.draft.stats().stream()
                    .filter(stat -> StatLogicalIdentity.key(stat).equals(logicalKey))
                    .findFirst().orElse(null);
            int slot = index - page.firstItem();
            actions.put(slot, new StudioAction(StudioActionType.STAT, entry.id()));
            inventory.setItem(slot, StudioInventoryRenderer.item(
                    StatMaterialPalette.rowIcon(logicalKey, selected != null, degraded),
                    entry.displayName(),
                    selected != null ? NamedTextColor.GREEN : NamedTextColor.AQUA,
                    rowLore(entry, selected)));
        }

        if (degraded) {
            String detail = state.statSnapshot == null
                    ? "Catalog snapshot is unavailable" : state.statSnapshot.detail();
            inventory.setItem(22, StudioInventoryRenderer.item(Material.RED_STAINED_GLASS_PANE,
                    "Dynamic catalog unavailable", NamedTextColor.RED,
                    new String[] {detail, "Manual stat IDs remain supported"}));
        } else if (entries.isEmpty()) {
            inventory.setItem(22, StudioInventoryRenderer.item(Material.GRAY_DYE, "No matching stats",
                    NamedTextColor.YELLOW, new String[] {state.statFilter.isBlank()
                            ? "MythicLib returned no registered stats" : "Change or clear search"}));
        }

        String pageLabel = "Page " + page.label();
        actions.put(45, new StudioAction(StudioActionType.PREVIOUS, ""));
        actions.put(46, new StudioAction(StudioActionType.STAT_MANUAL, ""));
        actions.put(47, new StudioAction(StudioActionType.STAT_REFRESH, ""));
        actions.put(49, new StudioAction(StudioActionType.BACK, ""));
        actions.put(50, new StudioAction(StudioActionType.STAT_SEARCH, ""));
        actions.put(53, new StudioAction(StudioActionType.NEXT, ""));
        inventory.setItem(45, StudioInventoryRenderer.item(Material.ARROW, "Previous", NamedTextColor.YELLOW,
                new String[] {pageLabel}));
        inventory.setItem(46, StudioInventoryRenderer.item(Material.WRITABLE_BOOK, "Manual stat list",
                NamedTextColor.GOLD,
                new String[] {"Supports legacy/bare IDs", "Format: id MODIFIER min max;..."}));
        inventory.setItem(47, StudioInventoryRenderer.item(Material.CLOCK, "Refresh provider catalog",
                NamedTextColor.YELLOW, new String[] {"Use after MythicLib or MMOItems reloads"}));
        inventory.setItem(49, StudioInventoryRenderer.item(Material.ARROW, "Back to editor",
                NamedTextColor.YELLOW, new String[0]));
        inventory.setItem(50, StudioInventoryRenderer.item(Material.COMPASS, "Search stats", NamedTextColor.AQUA,
                new String[] {state.statFilter.isBlank() ? "No filter" : "Filter: " + state.statFilter}));
        inventory.setItem(53, StudioInventoryRenderer.item(Material.ARROW, "Next", NamedTextColor.YELLOW,
                new String[] {pageLabel}));
        return inventory;
    }

    /**
     * One explained button per modifier the stat actually supports.
     *
     * <p>Unsupported modifiers are omitted, so the screen cannot offer a choice that
     * {@code catalogStat} would then reject.
     */
    static Inventory modifiers(StudioState state, StatCatalogEntry entry,
                               StudioInventoryRenderer.ScreenFactory factory) {
        Map<Integer, StudioAction> actions = new HashMap<>();
        Inventory inventory = factory.create(
                state, StudioInventoryHolder.Screen.STAT_MODIFIER, actions, 27,
                Messages.line(MessageKey.GUI_TITLE_STUDIO_STAT,
                        Messages.of("detail", abbreviate(entry.displayName()))));

        int[] slots = {11, 13, 15};
        int index = 0;
        for (StatModifierType modifier : StatModifierType.values()) {
            if (!entry.supportedModifierTypes().contains(modifier)) continue;
            if (index >= slots.length) break;
            int slot = slots[index++];
            actions.put(slot, new StudioAction(StudioActionType.STAT_MODIFIER, modifier.name()));
            inventory.setItem(slot, StudioInventoryRenderer.item(
                    modifierIcon(modifier),
                    StatModifierPresentation.label(modifier),
                    NamedTextColor.AQUA,
                    new String[] {
                            StatModifierPresentation.explanation(modifier),
                            "Example: " + StatModifierPresentation.example(modifier),
                            "Click to enter min and max"}));
        }

        inventory.setItem(4, StudioInventoryRenderer.item(
                StatMaterialPalette.iconFor(StatLogicalIdentity.key(entry.id(), entry.extensions())),
                entry.displayName(), NamedTextColor.GOLD,
                new String[] {"ID: " + entry.id(), "Provider: " + entry.provider(),
                        "Choose how the value is applied"}));
        actions.put(22, new StudioAction(StudioActionType.BACK, ""));
        inventory.setItem(22, StudioInventoryRenderer.item(Material.ARROW, "Back to stats",
                NamedTextColor.YELLOW, new String[] {"No changes are saved yet"}));
        return inventory;
    }

    private static List<StatCatalogEntry> visibleEntries(StudioState state) {
        if (state.statSnapshot == null) return List.of();
        return state.statSnapshot.entries().stream()
                .filter(entry -> state.statFilter.isBlank()
                        || entry.id().toLowerCase(Locale.ROOT).contains(state.statFilter)
                        || entry.displayName().toLowerCase(Locale.ROOT).contains(state.statFilter))
                .sorted(Comparator.comparing(StatCatalogEntry::displayName).thenComparing(StatCatalogEntry::id))
                .toList();
    }

    private static String[] rowLore(StatCatalogEntry entry, StudioStat selected) {
        String state = selected == null
                ? "Click to configure"
                : "Selected: " + StatModifierPresentation.label(selected.modifierType())
                        + " " + number(selected.range().minimum()) + " to " + number(selected.range().maximum());
        return new String[] {
                "ID: " + entry.id(),
                "Provider: " + entry.provider(),
                "Modifiers: " + StatModifierPresentation.labels(entry.supportedModifierTypes()),
                state};
    }

    private static Material modifierIcon(StatModifierType modifier) {
        return switch (modifier) {
            case FLAT -> Material.IRON_INGOT;
            case RELATIVE -> Material.GOLD_INGOT;
            case ADDITIVE_MULTIPLIER -> Material.DIAMOND;
        };
    }

    private static String number(double value) {
        return Durations.decimal(value);
    }

    private static String abbreviate(String value) {
        return value.length() <= 24 ? value : value.substring(0, 21) + "...";
    }
}
