package io.github.salyvn.omnipet.paper.gui.hatch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;

public final class HatchMenuRenderer {
    public Inventory render(Player player, PlayerState state) {
        Map<Integer, HatchInventoryHolder.Action> actions = new HashMap<>();
        IncubationState incubation = state.incubation();
        UUID incubationId = incubation == null ? null : incubation.id();
        HatchInventoryHolder holder = new HatchInventoryHolder(
                player.getUniqueId(), state.revision(), incubationId, actions);
        Inventory inventory = Bukkit.createInventory(
                holder, 27, Component.text("OmniPet Hatch", NamedTextColor.GOLD));
        holder.bind(inventory);
        fill(inventory);

        if (incubation == null || incubation.terminal()) {
            if (incubation != null) {
                inventory.setItem(13, item(
                        Material.ENDER_CHEST,
                        "Previous hatch: " + incubation.status().name(),
                        NamedTextColor.YELLOW,
                        "Resolved pet: " + incubation.outcome().definitionId(),
                        "A new egg can be started below."));
            }
            actions.put(11, HatchInventoryHolder.Action.startMain());
            actions.put(15, HatchInventoryHolder.Action.startOffHand());
            inventory.setItem(11, item(
                    Material.DRAGON_EGG, "Start main-hand egg", NamedTextColor.AQUA,
                    "Capture the egg in your selected hotbar slot."));
            inventory.setItem(15, item(
                    Material.DRAGON_EGG, "Start off-hand egg", NamedTextColor.AQUA,
                    "Capture the egg in your off hand."));
        } else {
            ItemStack egg = new ItemStack(Material.PLAYER_HEAD);
            HatchHeadItems.apply(egg, incubation.outcome().icon());
            List<String> lore = List.of(
                    "Pet: " + incubation.outcome().definitionId(),
                    "Tier: " + incubation.outcome().tier(),
                    "Rarity: " + incubation.outcome().rarityId(),
                    "Remaining: " + format(incubation.remainingActiveMillis()),
                    incubation.status() == IncubationStatus.READY
                            ? "Ready to claim into your vault."
                            : "Time advances only while you are online.");
            inventory.setItem(13, item(egg, "Incubation | " + incubation.status(),
                    incubation.status() == IncubationStatus.READY
                            ? NamedTextColor.GREEN : NamedTextColor.LIGHT_PURPLE,
                    lore.toArray(String[]::new)));
            if (incubation.status() == IncubationStatus.READY) {
                actions.put(13, HatchInventoryHolder.Action.claim());
            } else {
                actions.put(13, HatchInventoryHolder.Action.refresh());
            }
        }
        actions.put(22, HatchInventoryHolder.Action.refresh());
        inventory.setItem(22, item(Material.CLOCK, "Refresh", NamedTextColor.YELLOW,
                "Read the latest durable incubation state."));
        return inventory;
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }

    private static ItemStack item(ItemStack stack, String name, NamedTextColor color, String... lore) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.text(line, NamedTextColor.GRAY));
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, String... lore) {
        return item(new ItemStack(material), name, color, lore);
    }

    private static String format(long millis) {
        long seconds = Math.max(0, Duration.ofMillis(millis).toSeconds());
        long days = seconds / 86_400;
        seconds %= 86_400;
        long hours = seconds / 3_600;
        seconds %= 3_600;
        long minutes = seconds / 60;
        seconds %= 60;
        return days > 0
                ? "%dd %02dh %02dm %02ds".formatted(days, hours, minutes, seconds)
                : "%02dh %02dm %02ds".formatted(hours, minutes, seconds);
    }
}
