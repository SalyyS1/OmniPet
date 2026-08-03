package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.catalog.CatalogHealth;
import io.github.salyvn.omnipet.core.catalog.StatCatalogEntry;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.studio.StatLogicalIdentity;
import io.github.salyvn.omnipet.core.studio.StudioPetDraft;
import io.github.salyvn.omnipet.paper.studio.session.StudioViewToken;

final class StudioInventoryRenderer {
    Inventory tiers(Player player, StudioState state, RegistrySnapshot snapshot) {
        Map<Integer, StudioAction> actions = new HashMap<>();
        StudioInventoryHolder holder = holder(state, StudioInventoryHolder.Screen.TIERS, actions);
        Inventory inventory = create(holder, 27, "OmniPet Studio | Tiers");
        fill(inventory);
        int[] slots = {10, 11, 12, 14, 15};
        for (int index = 0; index < PetTier.values().length; index++) {
            PetTier tier = PetTier.values()[index];
            long count = snapshot.definitions().values().stream().filter(definition -> definition.tier() == tier).count();
            actions.put(slots[index], new StudioAction(StudioActionType.TIER, tier.name()));
            inventory.setItem(slots[index], item(Material.CHEST,
                    tier.name() + " tier", NamedTextColor.GOLD,
                    "Definitions: " + count, "Click to browse"));
        }
        inventory.setItem(22, item(Material.BARRIER, "Close", NamedTextColor.RED, "Exit OmniPet Studio"));
        return inventory;
    }

    Inventory list(Player player, StudioState state, RegistrySnapshot snapshot) {
        Map<Integer, StudioAction> actions = new HashMap<>();
        StudioInventoryHolder holder = holder(state, StudioInventoryHolder.Screen.LIST, actions);
        Inventory inventory = create(holder, 54, "OmniPet Studio | " + state.tier.name());
        fill(inventory);

        List<PetDefinition> definitions = snapshot.definitions().values().stream()
                .filter(definition -> definition.tier() == state.tier)
                .filter(definition -> state.filter.isBlank() || definition.id().toLowerCase().contains(state.filter.toLowerCase()))
                .sorted(Comparator.comparing(PetDefinition::id))
                .toList();
        int pages = Math.max(1, (definitions.size() + 44) / 45);
        state.page = Math.max(0, Math.min(state.page, pages - 1));
        int start = state.page * 45;
        for (int index = start; index < Math.min(start + 45, definitions.size()); index++) {
            PetDefinition definition = definitions.get(index);
            int slot = index - start;
            actions.put(slot, new StudioAction(StudioActionType.PET, definition.id()));
            inventory.setItem(slot, StudioHeadItems.apply(item(Material.PLAYER_HEAD, definition.id(), NamedTextColor.AQUA,
                    "Tier: " + definition.tier(), "Revision: " + definition.revision(),
                    state.archiveMode ? "Click to archive" : "Click to edit"), definition.icon()));
        }
        actions.put(45, new StudioAction(StudioActionType.PREVIOUS, ""));
        actions.put(46, new StudioAction(StudioActionType.TOGGLE_ARCHIVE, ""));
        actions.put(48, new StudioAction(StudioActionType.CREATE, ""));
        actions.put(49, new StudioAction(StudioActionType.BACK, ""));
        actions.put(50, new StudioAction(StudioActionType.SEARCH, ""));
        actions.put(53, new StudioAction(StudioActionType.NEXT, ""));
        inventory.setItem(45, item(Material.ARROW, "Previous", NamedTextColor.YELLOW, "Page " + (state.page + 1) + "/" + pages));
        inventory.setItem(46, item(state.archiveMode ? Material.LIME_DYE : Material.REDSTONE,
                state.archiveMode ? "Archive mode: ON" : "Archive mode: OFF", NamedTextColor.YELLOW,
                "Archive is reversible and reference-aware"));
        inventory.setItem(48, item(Material.EMERALD, "Create definition", NamedTextColor.GREEN, "Enter a stable ID in chat"));
        inventory.setItem(49, item(Material.BARRIER, "Back to tiers", NamedTextColor.RED));
        inventory.setItem(50, item(Material.COMPASS, "Search", NamedTextColor.AQUA,
                state.filter.isBlank() ? "No filter" : "Filter: " + state.filter));
        inventory.setItem(53, item(Material.ARROW, "Next", NamedTextColor.YELLOW, "Page " + (state.page + 1) + "/" + pages));
        return inventory;
    }

    Inventory editor(Player player, StudioState state) {
        Map<Integer, StudioAction> actions = new HashMap<>();
        StudioInventoryHolder holder = holder(state, StudioInventoryHolder.Screen.EDITOR, actions);
        Inventory inventory = create(holder, 54, "OmniPet Studio | Edit " + state.draft.id());
        fill(inventory);
        StudioPetDraft draft = state.draft;
        actions.put(10, new StudioAction(StudioActionType.EDIT_TIER, ""));
        actions.put(11, new StudioAction(StudioActionType.EDIT_ICON, ""));
        actions.put(12, new StudioAction(StudioActionType.EDIT_DISPLAY, ""));
        actions.put(13, new StudioAction(StudioActionType.EDIT_STATS, ""));
        actions.put(14, new StudioAction(StudioActionType.EDIT_RARITY, ""));
        actions.put(15, new StudioAction(StudioActionType.EDIT_PROGRESSION, ""));
        actions.put(16, new StudioAction(StudioActionType.EDIT_SKILLS, ""));
        actions.put(19, new StudioAction(StudioActionType.EDIT_BEHAVIOR, ""));
        actions.put(20, new StudioAction(StudioActionType.EDIT_RELEASE, ""));
        if (draft.mode() == StudioPetDraft.Mode.EDIT) actions.put(44, new StudioAction(StudioActionType.CLONE, ""));
        actions.put(45, new StudioAction(StudioActionType.BACK, ""));
        actions.put(49, new StudioAction(StudioActionType.SAVE, ""));
        actions.put(53, new StudioAction(StudioActionType.CANCEL, ""));
        inventory.setItem(10, item(Material.NAME_TAG, "Tier: " + draft.tier(), NamedTextColor.GOLD, "Click to cycle D/C/B/A/S"));
        inventory.setItem(11, StudioHeadItems.apply(item(Material.PLAYER_HEAD, "Head icon", NamedTextColor.AQUA,
                draft.icon().source() + " " + abbreviate(draft.icon().value()), "Click to edit"), draft.icon()));
        inventory.setItem(12, item(Material.ARMOR_STAND, "Renderer: " + draft.display().provider(), NamedTextColor.AQUA,
                draft.display().model() == null ? "Built-in head" : "Model: " + draft.display().model(), "Click to edit"));
        inventory.setItem(13, item(Material.REDSTONE, "MythicLib stats: " + draft.stats().size(), NamedTextColor.LIGHT_PURPLE,
                statCatalogLine(state), "Click to choose or enter manual IDs"));
        inventory.setItem(14, item(Material.NETHER_STAR, "Rarity bands: " + draft.rarityBands().size(), NamedTextColor.LIGHT_PURPLE,
                "Format: id min max weight hatchMultiplier;...", "Click to edit"));
        inventory.setItem(15, item(Material.EXPERIENCE_BOTTLE, "Progression", NamedTextColor.GREEN,
                draft.progression() == null ? "Not configured" : "Max level: " + draft.progression().maxLevel(),
                "Format: maxLevel;formula", "Click to edit"));
        inventory.setItem(16, item(Material.BLAZE_POWDER, "Skills: " + draft.skills().size(), NamedTextColor.LIGHT_PURPLE,
                "Provider-neutral MythicMobs references", "Click to edit"));
        inventory.setItem(19, item(Material.HEART_OF_THE_SEA, "Behavior/effects: " + draft.behaviorExtensions().size(), NamedTextColor.GREEN,
                "Format: key=value;...", "Click to edit"));
        inventory.setItem(20, item(Material.ENDER_CHEST, "Release policy", NamedTextColor.GREEN,
                draft.releasePolicy() == null ? "Not configured" : draft.releasePolicy().mode(), "Click to edit"));
        inventory.setItem(44, item(draft.mode() == StudioPetDraft.Mode.EDIT ? Material.CARTOGRAPHY_TABLE : Material.GRAY_DYE,
                draft.mode() == StudioPetDraft.Mode.EDIT ? "Clone definition" : "Clone unavailable", NamedTextColor.AQUA,
                draft.mode() == StudioPetDraft.Mode.EDIT ? "Create a new stable ID from this draft" : "Save this new definition before cloning",
                "Source definition remains unchanged"));
        inventory.setItem(45, item(Material.ARROW, "Back", NamedTextColor.YELLOW, "Discard navigation only"));
        inventory.setItem(49, item(Material.EMERALD_BLOCK, "Save", NamedTextColor.GREEN, "Validate and atomically publish"));
        inventory.setItem(53, item(Material.BARRIER, "Cancel", NamedTextColor.RED, "Discard draft"));
        return inventory;
    }

    Inventory stats(Player player, StudioState state) {
        return StudioStatScreens.picker(state, StudioInventoryRenderer::screen);
    }

    Inventory statModifiers(Player player, StudioState state, StatCatalogEntry entry) {
        return StudioStatScreens.modifiers(state, entry, StudioInventoryRenderer::screen);
    }

    /** Creates a bound, filled screen. Shared with {@link StudioStatScreens}. */
    @FunctionalInterface
    interface ScreenFactory {
        Inventory create(StudioState state, StudioInventoryHolder.Screen screen,
                         Map<Integer, StudioAction> actions, int size, String title);
    }

    private static Inventory screen(StudioState state, StudioInventoryHolder.Screen screen,
                                    Map<Integer, StudioAction> actions, int size, String title) {
        Inventory inventory = create(holder(state, screen, actions), size, title);
        fill(inventory);
        return inventory;
    }

    Inventory archiveConfirm(Player player, StudioState state) {
        Map<Integer, StudioAction> actions = new HashMap<>();
        StudioInventoryHolder holder = holder(state, StudioInventoryHolder.Screen.ARCHIVE_CONFIRM, actions);
        Inventory inventory = create(holder, 27, "OmniPet Studio | Archive");
        fill(inventory);
        actions.put(11, new StudioAction(StudioActionType.CONFIRM_ARCHIVE, state.archiveTarget));
        actions.put(13, new StudioAction(StudioActionType.HARD_DELETE, state.archiveTarget));
        actions.put(15, new StudioAction(StudioActionType.CANCEL_ARCHIVE, ""));
        inventory.setItem(11, item(Material.LIME_DYE, "Confirm archive", NamedTextColor.GREEN,
                state.archiveTarget, "References are checked before commit"));
        inventory.setItem(13, item(Material.LAVA_BUCKET, "Hard delete", NamedTextColor.RED,
                "Permanent removal of YAML and backup", "Requires typing the exact definition ID"));
        inventory.setItem(15, item(Material.BARRIER, "Cancel", NamedTextColor.RED));
        return inventory;
    }

    private static StudioInventoryHolder holder(StudioState state, StudioInventoryHolder.Screen screen,
                                                Map<Integer, StudioAction> actions) {
        return new StudioInventoryHolder(state.viewerId, state.token, screen, actions);
    }

    private static Inventory create(StudioInventoryHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text(title, NamedTextColor.GOLD));
        holder.bind(inventory);
        return inventory;
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }

    /** Shared with {@link StudioStatScreens} so every Studio item is built identically. */
    static ItemStack item(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.text(line, NamedTextColor.GRAY));
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String statCatalogLine(StudioState state) {
        if (state.statSnapshot == null) return "Catalog has not been queried";
        return switch (state.statSnapshot.health()) {
            case AVAILABLE -> "Catalog available: " + state.statSnapshot.entries().size() + " registered stats";
            case UNAVAILABLE -> "Catalog unavailable: " + state.statSnapshot.detail();
            case DISABLED -> "Catalog disabled: " + state.statSnapshot.detail();
            case INCOMPATIBLE -> "Catalog incompatible: " + state.statSnapshot.detail();
        };
    }

    private static String abbreviate(String value) { return value.length() <= 32 ? value : value.substring(0, 29) + "..."; }
}
