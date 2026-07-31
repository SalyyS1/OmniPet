package io.github.salyvn.omnipet.paper.gui.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.economy.EconomyAmount;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;

public final class SlotPurchaseMenuRenderer {
    public Inventory selection(
            Player player,
            long revision,
            int slot,
            int returnPage,
            UUID transactionId,
            Phase4PaperConfig.SlotUnlock unlock,
            Function<EconomyProvider, Boolean> available,
            Function<EconomyProvider, String> diagnostic,
            Function<EconomyProvider, String> balance) {
        Map<Integer, SlotPurchaseInventoryHolder.Action> actions = new HashMap<>();
        SlotPurchaseInventoryHolder holder = holder(
                player, revision, slot, returnPage, transactionId,
                SlotPurchaseInventoryHolder.Stage.SELECT_PROVIDER, actions);
        Inventory inventory = Bukkit.createInventory(
                holder, 27, Component.text("Unlock active slot " + slot, NamedTextColor.GOLD));
        holder.bind(inventory);
        fill(inventory);

        int[] positions = {11, 15};
        int index = 0;
        for (Map.Entry<EconomyProvider, EconomyAmount> entry : unlock.costs().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            int position = positions[Math.min(index++, positions.length - 1)];
            boolean providerAvailable = available.apply(entry.getKey());
            if (providerAvailable) {
                actions.put(position, SlotPurchaseInventoryHolder.Action.select(entry.getValue()));
            }
            inventory.setItem(position, item(
                    providerAvailable ? material(entry.getKey()) : Material.BARRIER,
                    entry.getKey() == EconomyProvider.VAULT ? "Pay with Vault" : "Pay with PlayerPoints",
                    providerAvailable ? NamedTextColor.GREEN : NamedTextColor.RED,
                    "Cost: " + entry.getValue().value().toPlainString(),
                    balance.apply(entry.getKey()),
                    providerAvailable ? "Click to review" : diagnostic.apply(entry.getKey())));
        }
        actions.put(22, SlotPurchaseInventoryHolder.Action.cancel());
        inventory.setItem(22, item(Material.ARROW, "Back to vault", NamedTextColor.YELLOW));
        return inventory;
    }

    public Inventory confirmation(
            Player player,
            SlotPurchaseInventoryHolder previous,
            EconomyAmount amount) {
        Map<Integer, SlotPurchaseInventoryHolder.Action> actions = new HashMap<>();
        SlotPurchaseInventoryHolder holder = holder(
                player,
                previous.expectedRevision(),
                previous.slot(),
                previous.returnPage(),
                previous.transactionId(),
                SlotPurchaseInventoryHolder.Stage.CONFIRM,
                actions);
        Inventory inventory = Bukkit.createInventory(
                holder, 27, Component.text("Confirm slot " + previous.slot(), NamedTextColor.GOLD));
        holder.bind(inventory);
        fill(inventory);
        actions.put(11, SlotPurchaseInventoryHolder.Action.confirm(amount));
        actions.put(15, SlotPurchaseInventoryHolder.Action.cancel());
        inventory.setItem(11, item(
                Material.LIME_CONCRETE,
                "Confirm purchase",
                NamedTextColor.GREEN,
                "Provider: " + amount.provider(),
                "Cost: " + amount.value().toPlainString(),
                "One click creates one durable transaction"));
        inventory.setItem(15, item(Material.RED_CONCRETE, "Cancel", NamedTextColor.RED));
        return inventory;
    }

    private static SlotPurchaseInventoryHolder holder(
            Player player,
            long revision,
            int slot,
            int returnPage,
            UUID transactionId,
            SlotPurchaseInventoryHolder.Stage stage,
            Map<Integer, SlotPurchaseInventoryHolder.Action> actions) {
        return new SlotPurchaseInventoryHolder(
                player.getUniqueId(), revision, slot, returnPage, transactionId, stage, actions);
    }

    private static Material material(EconomyProvider provider) {
        return provider == EconomyProvider.VAULT ? Material.GOLD_INGOT : Material.NETHER_STAR;
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.text(line, NamedTextColor.GRAY));
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }
}
