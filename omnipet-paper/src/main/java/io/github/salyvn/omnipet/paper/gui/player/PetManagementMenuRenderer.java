package io.github.salyvn.omnipet.paper.gui.player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;
import io.github.salyvn.omnipet.paper.management.PetManagementViewModel;

public final class PetManagementMenuRenderer {
    public Inventory render(PetManagementViewModel view) {
        Layout layout = layout(view);
        Inventory inventory = Bukkit.createInventory(
                layout.holder(), layout.size(), Component.text(layout.title(), NamedTextColor.GOLD));
        layout.holder().bind(inventory);
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        layout.entries().forEach((slot, entry) -> inventory.setItem(
                slot, item(entry.material(), entry.name(), entry.color(), entry.lore())));
        return inventory;
    }

    public Layout layout(PetManagementViewModel view) {
        return view.releasePreview() == null ? management(view) : release(view);
    }

    private Layout management(PetManagementViewModel view) {
        Map<Integer, PetManagementInventoryHolder.Action> actions = new LinkedHashMap<>();
        Map<Integer, Entry> entries = new LinkedHashMap<>();
        put(actions, entries, 10,
                PetManagementInventoryHolder.Action.favorite(!view.metadata().favorite()),
                view.metadata().favorite() ? Material.NETHER_STAR : Material.GRAY_DYE,
                view.metadata().favorite() ? "Unfavorite" : "Favorite", NamedTextColor.YELLOW,
                "Stable instance: " + shortId(view.pet().id()));
        put(actions, entries, 11,
                PetManagementInventoryHolder.Action.lock(!view.metadata().locked()),
                view.metadata().locked() ? Material.TRIPWIRE_HOOK : Material.IRON_NUGGET,
                view.metadata().locked() ? "Unlock pet" : "Lock pet", NamedTextColor.AQUA,
                view.metadata().locked() ? "Release and destructive actions disabled" : "Protect this pet from release");
        if (view.petIndex() > 0) put(actions, entries, 12,
                PetManagementInventoryHolder.Action.move(view.petIndex() - 1),
                Material.ARROW, "Move left", NamedTextColor.YELLOW, "Target position: " + view.petIndex());
        if (view.petIndex() + 1 < view.ownerState().pets().size()) put(actions, entries, 13,
                PetManagementInventoryHolder.Action.move(view.petIndex() + 1),
                Material.ARROW, "Move right", NamedTextColor.YELLOW, "Target position: " + (view.petIndex() + 2));
        put(actions, entries, 14, PetManagementInventoryHolder.Action.simple(
                        PetManagementInventoryHolder.Type.ADD_EXPERIENCE),
                Material.EXPERIENCE_BOTTLE, "Use EXP candy", NamedTextColor.GREEN,
                "Level " + view.progression().level() + " | EXP " + decimal(view.progression().experience()),
                "The exact captured item is consumed only after persistence");
        put(actions, entries, 15, PetManagementInventoryHolder.Action.simple(
                        PetManagementInventoryHolder.Type.BREAKTHROUGH),
                Material.AMETHYST_SHARD, "Breakthrough", NamedTextColor.LIGHT_PURPLE,
                "Evolution " + view.progression().evolution(), "Requirements come from the captured stone");
        put(actions, entries, 16, PetManagementInventoryHolder.Action.simple(
                        PetManagementInventoryHolder.Type.PREVIEW_RELEASE),
                view.metadata().locked() ? Material.BARRIER : Material.LAVA_BUCKET,
                "Release pet", view.metadata().locked() ? NamedTextColor.DARK_GRAY : NamedTextColor.RED,
                view.metadata().locked() ? "Unlock this pet first" : "Preview frozen rewards before confirmation");
        put(actions, entries, 31, PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.REFRESH),
                Material.CLOCK, "Refresh", NamedTextColor.AQUA, "Revision " + view.session().expectedRevision());
        put(actions, entries, 35, PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.BACK),
                Material.BARRIER, "Back to vault", NamedTextColor.YELLOW);
        entries.put(22, new Entry(
                Material.PLAYER_HEAD,
                view.metadata().customName().isBlank() ? view.pet().definitionId() : view.metadata().customName(),
                view.active() ? NamedTextColor.GREEN : NamedTextColor.AQUA,
                List.of("Definition: " + view.pet().definitionId(), "UUID: " + shortId(view.pet().id()),
                        view.active() ? "Desired active" : "Stored")));
        PetManagementInventoryHolder holder = new PetManagementInventoryHolder(
                view.session(), PetManagementInventoryHolder.View.MANAGEMENT, actions, null);
        return new Layout(45, "OmniPet | Manage", holder, entries);
    }

    private Layout release(PetManagementViewModel view) {
        Map<Integer, PetManagementInventoryHolder.Action> actions = new LinkedHashMap<>();
        Map<Integer, Entry> entries = new LinkedHashMap<>();
        actions.put(11, PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.CANCEL_RELEASE));
        entries.put(11, new Entry(Material.BARRIER, "Cancel", NamedTextColor.YELLOW, List.of("No state changes")));
        actions.put(15, PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.CONFIRM_RELEASE));
        entries.put(15, new Entry(Material.LAVA_BUCKET, "Confirm release", NamedTextColor.RED,
                rewardLore(view.releasePreview().rewards())));
        entries.put(13, new Entry(Material.PAPER, "Frozen reward preview", NamedTextColor.GOLD,
                List.of("Transaction: " + shortId(view.releasePreview().transactionId()),
                        "Revision: " + view.releasePreview().expectedRevision(),
                        "Pet: " + shortId(view.releasePreview().petId()))));
        PetManagementInventoryHolder holder = new PetManagementInventoryHolder(
                view.session(), PetManagementInventoryHolder.View.RELEASE_CONFIRMATION,
                actions, view.releasePreview());
        return new Layout(27, "OmniPet | Confirm Release", holder, entries);
    }

    private static List<String> rewardLore(ReleaseRewardBundle rewards) {
        List<String> lore = new ArrayList<>();
        rewards.internalRewards().forEach(reward -> lore.add("Internal: " + reward.rewardId() + " x" + reward.amount()));
        rewards.externalRewards().forEach(reward -> lore.add(
                reward.provider() + ": " + reward.rewardId() + " " + reward.amount().toPlainString()));
        if (lore.isEmpty()) lore.add("No configured rewards");
        lore.add("Pet removal and internal outbox persist atomically");
        return List.copyOf(lore);
    }

    private static void put(
            Map<Integer, PetManagementInventoryHolder.Action> actions,
            Map<Integer, Entry> entries,
            int slot,
            PetManagementInventoryHolder.Action action,
            Material material,
            String name,
            NamedTextColor color,
            String... lore) {
        actions.put(slot, action);
        entries.put(slot, new Entry(material, name, color, List.of(lore)));
    }

    private static ItemStack item(
            Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private static String shortId(Object value) {
        String text = String.valueOf(value);
        return text.length() <= 18 ? text : text.substring(0, 18) + "...";
    }

    private static String decimal(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    public record Layout(
            int size,
            String title,
            PetManagementInventoryHolder holder,
            Map<Integer, Entry> entries) {
        public Layout {
            entries = Map.copyOf(entries);
        }
    }

    public record Entry(Material material, String name, NamedTextColor color, List<String> lore) {
        public Entry {
            lore = List.copyOf(lore);
        }
    }
}
