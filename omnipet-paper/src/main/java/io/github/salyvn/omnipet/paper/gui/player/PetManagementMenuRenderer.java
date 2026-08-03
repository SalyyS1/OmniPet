package io.github.salyvn.omnipet.paper.gui.player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;
import io.github.salyvn.omnipet.paper.gui.GuiColors;
import io.github.salyvn.omnipet.paper.gui.GuiItems;
import io.github.salyvn.omnipet.paper.management.PetManagementViewModel;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.Durations;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

public final class PetManagementMenuRenderer {
    public Inventory render(PetManagementViewModel view) {
        Layout layout = layout(view);
        Inventory inventory = Bukkit.createInventory(layout.holder(), layout.size(), layout.title());
        layout.holder().bind(inventory);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, GuiItems.filler());
        layout.entries().forEach((slot, entry) ->
                inventory.setItem(slot, GuiItems.of(entry.material(), entry.name(), entry.lore())));
        return inventory;
    }

    public Layout layout(PetManagementViewModel view) {
        return view.releasePreview() == null ? management(view) : release(view);
    }

    private Layout management(PetManagementViewModel view) {
        Map<Integer, PetManagementInventoryHolder.Action> actions = new LinkedHashMap<>();
        Map<Integer, Entry> entries = new LinkedHashMap<>();
        boolean favorite = view.metadata().favorite();
        boolean locked = view.metadata().locked();

        put(actions, entries, 10,
                PetManagementInventoryHolder.Action.favorite(!favorite),
                favorite ? Material.NETHER_STAR : Material.GRAY_DYE,
                label(favorite ? MessageKey.GUI_MANAGE_UNFAVORITE : MessageKey.GUI_MANAGE_FAVORITE,
                        GuiColors.WARNING),
                Messages.line(MessageKey.GUI_MANAGE_FAVORITE_HINT));
        put(actions, entries, 11,
                PetManagementInventoryHolder.Action.lock(!locked),
                locked ? Material.TRIPWIRE_HOOK : Material.IRON_NUGGET,
                label(locked ? MessageKey.GUI_MANAGE_UNLOCK : MessageKey.GUI_MANAGE_LOCK, GuiColors.ACCENT),
                Messages.line(locked
                        ? MessageKey.GUI_MANAGE_LOCKED_HINT
                        : MessageKey.GUI_MANAGE_UNLOCKED_HINT));
        if (view.petIndex() > 0) {
            put(actions, entries, 12,
                    PetManagementInventoryHolder.Action.move(view.petIndex() - 1),
                    Material.ARROW, label(MessageKey.GUI_MANAGE_MOVE_LEFT, GuiColors.WARNING),
                    Messages.line(MessageKey.GUI_MANAGE_MOVE_TARGET, Messages.of("amount", view.petIndex())));
        }
        if (view.petIndex() + 1 < view.ownerState().pets().size()) {
            put(actions, entries, 13,
                    PetManagementInventoryHolder.Action.move(view.petIndex() + 1),
                    Material.ARROW, label(MessageKey.GUI_MANAGE_MOVE_RIGHT, GuiColors.WARNING),
                    Messages.line(MessageKey.GUI_MANAGE_MOVE_TARGET,
                            Messages.of("amount", view.petIndex() + 2)));
        }
        put(actions, entries, 14,
                PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.ADD_EXPERIENCE),
                Material.EXPERIENCE_BOTTLE, label(MessageKey.GUI_MANAGE_CANDY, GuiColors.POSITIVE),
                Messages.line(MessageKey.GUI_MANAGE_LEVEL, Messages.of("level", view.progression().level())),
                Messages.line(MessageKey.GUI_MANAGE_EXPERIENCE,
                        Messages.of("exp", decimal(view.progression().experience()))),
                Component.empty(),
                Messages.line(MessageKey.GUI_MANAGE_CANDY_HINT));
        put(actions, entries, 15,
                PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.BREAKTHROUGH),
                Material.AMETHYST_SHARD, label(MessageKey.GUI_MANAGE_BREAKTHROUGH, GuiColors.TITLE),
                Messages.line(MessageKey.GUI_MANAGE_EVOLUTION,
                        Messages.of("amount", view.progression().evolution())),
                Component.empty(),
                Messages.line(MessageKey.GUI_MANAGE_BREAKTHROUGH_HINT));
        put(actions, entries, 16,
                PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.PREVIEW_RELEASE),
                locked ? Material.BARRIER : Material.LAVA_BUCKET,
                label(MessageKey.GUI_MANAGE_RELEASE, locked ? GuiColors.SECTION : GuiColors.BLOCKED),
                Messages.line(locked
                        ? MessageKey.GUI_MANAGE_RELEASE_LOCKED
                        : MessageKey.GUI_MANAGE_RELEASE_HINT));
        put(actions, entries, 31,
                PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.REFRESH),
                Material.CLOCK, Messages.line(MessageKey.GUI_MANAGE_REFRESH),
                Messages.line(MessageKey.GUI_MANAGE_REFRESH_HINT));
        put(actions, entries, 35,
                PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.BACK),
                Material.BARRIER, Messages.line(MessageKey.GUI_MANAGE_BACK));
        // Slot 27 is free: 10-16, 22, 31, and 35 carry the existing controls.
        put(actions, entries, 27,
                PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.HUB),
                Material.COMPASS, Messages.line(MessageKey.HUB_BACK));

        // Staff need the full instance UUID here, so it stays — unlike the vault, which dropped it.
        String customName = view.metadata().customName();
        entries.put(22, new Entry(
                Material.PLAYER_HEAD,
                GuiItems.label(customName.isBlank() ? view.pet().definitionId() : customName,
                        view.active() ? GuiColors.POSITIVE : GuiColors.ACCENT),
                List.of(
                        Messages.line(MessageKey.GUI_MANAGE_DEFINITION,
                                Messages.of("pet", view.pet().definitionId())),
                        Messages.line(MessageKey.GUI_MANAGE_INSTANCE,
                                Messages.of("detail", view.pet().id().toString())),
                        Component.empty(),
                        Messages.line(view.active()
                                ? MessageKey.GUI_MANAGE_ACTIVE
                                : MessageKey.GUI_MANAGE_STORED))));
        PetManagementInventoryHolder holder = new PetManagementInventoryHolder(
                view.session(), PetManagementInventoryHolder.View.MANAGEMENT, actions, null);
        return new Layout(45, Messages.line(MessageKey.GUI_TITLE_MANAGE), holder, entries);
    }

    private Layout release(PetManagementViewModel view) {
        Map<Integer, PetManagementInventoryHolder.Action> actions = new LinkedHashMap<>();
        Map<Integer, Entry> entries = new LinkedHashMap<>();
        actions.put(11, PetManagementInventoryHolder.Action.simple(
                PetManagementInventoryHolder.Type.CANCEL_RELEASE));
        entries.put(11, new Entry(Material.BARRIER, Messages.line(MessageKey.GUI_RELEASE_CANCEL),
                List.of(Messages.line(MessageKey.GUI_RELEASE_CANCEL_HINT))));
        actions.put(15, PetManagementInventoryHolder.Action.simple(
                PetManagementInventoryHolder.Type.CONFIRM_RELEASE));
        entries.put(15, new Entry(Material.LAVA_BUCKET, Messages.line(MessageKey.GUI_RELEASE_CONFIRM),
                rewardLore(view.releasePreview().rewards())));
        entries.put(13, new Entry(Material.PAPER, Messages.line(MessageKey.GUI_RELEASE_PREVIEW),
                List.of(
                        Messages.line(MessageKey.GUI_RELEASE_REVISION,
                                Messages.of("amount", view.releasePreview().expectedRevision())),
                        Messages.line(MessageKey.GUI_RELEASE_TRANSACTION,
                                Messages.of("detail", view.releasePreview().transactionId().toString())))));
        PetManagementInventoryHolder holder = new PetManagementInventoryHolder(
                view.session(), PetManagementInventoryHolder.View.RELEASE_CONFIRMATION,
                actions, view.releasePreview());
        return new Layout(27, Messages.line(MessageKey.GUI_TITLE_RELEASE), holder, entries);
    }

    private static List<Component> rewardLore(ReleaseRewardBundle rewards) {
        List<Component> lore = new ArrayList<>();
        rewards.internalRewards().forEach(reward -> lore.add(Messages.line(
                MessageKey.GUI_RELEASE_REWARD_INTERNAL,
                Messages.of("detail", Displays.identifier(reward.rewardId())),
                Messages.of("amount", reward.amount()))));
        rewards.externalRewards().forEach(reward -> lore.add(Messages.line(
                MessageKey.GUI_RELEASE_REWARD_EXTERNAL,
                Messages.of("provider", Displays.identifier(String.valueOf(reward.provider()))),
                Messages.of("detail", Displays.identifier(reward.rewardId())),
                Messages.of("amount", reward.amount().toPlainString()))));
        if (lore.isEmpty()) lore.add(Messages.line(MessageKey.GUI_RELEASE_REWARD_NONE));
        lore.add(Component.empty());
        lore.add(Messages.line(MessageKey.GUI_RELEASE_ATOMIC_NOTE));
        return List.copyOf(lore);
    }

    private static Component label(MessageKey key, net.kyori.adventure.text.format.TextColor color) {
        return Messages.line(key).colorIfAbsent(color);
    }

    private static void put(
            Map<Integer, PetManagementInventoryHolder.Action> actions,
            Map<Integer, Entry> entries,
            int slot,
            PetManagementInventoryHolder.Action action,
            Material material,
            Component name,
            Component... lore) {
        actions.put(slot, action);
        entries.put(slot, new Entry(material, name, List.of(lore)));
    }

    private static String decimal(double value) {
        return Durations.decimal(value);
    }

    public record Layout(
            int size,
            Component title,
            PetManagementInventoryHolder holder,
            Map<Integer, Entry> entries) {
        public Layout {
            entries = Map.copyOf(entries);
        }
    }

    public record Entry(Material material, Component name, List<Component> lore) {
        public Entry {
            lore = List.copyOf(lore);
        }
    }
}
