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

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.economy.EconomyAmount;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;
import io.github.salyvn.omnipet.paper.gui.GuiColors;
import io.github.salyvn.omnipet.paper.gui.GuiItems;
import io.github.salyvn.omnipet.paper.gui.MenuMaterials;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

public final class SlotPurchaseMenuRenderer {
    public Inventory selection(
            Player player,
            long revision,
            int slot,
            SlotPurchaseOrigin origin,
            UUID transactionId,
            Phase4PaperConfig.SlotUnlock unlock,
            Function<EconomyProvider, Boolean> available,
            Function<EconomyProvider, String> diagnostic,
            Function<EconomyProvider, SlotBalanceDisplay> balance) {
        Map<Integer, SlotPurchaseInventoryHolder.Action> actions = new HashMap<>();
        SlotPurchaseInventoryHolder holder = holder(
                player, revision, slot, origin, transactionId,
                SlotPurchaseInventoryHolder.Stage.SELECT_PROVIDER, actions);
        Inventory inventory = Bukkit.createInventory(holder, 27, Messages.line(
                MessageKey.GUI_TITLE_SLOT_SELECT, Messages.of("amount", slot)));
        holder.bind(inventory);
        fill(inventory);

        int[] positions = {11, 15};
        int index = 0;
        for (Map.Entry<EconomyProvider, EconomyAmount> entry : unlock.costs().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            int position = positions[Math.min(index++, positions.length - 1)];
            EconomyProvider provider = entry.getKey();
            boolean providerAvailable = available.apply(provider);
            if (providerAvailable) {
                actions.put(position, SlotPurchaseInventoryHolder.Action.select(entry.getValue()));
            }
            inventory.setItem(position, providerOption(
                    provider, entry.getValue(), providerAvailable,
                    balance.apply(provider), diagnostic.apply(provider)));
        }
        actions.put(22, SlotPurchaseInventoryHolder.Action.cancel());
        inventory.setItem(22, GuiItems.of(
                MenuMaterials.of("slot", "hub", Material.ARROW),
                Messages.line(MessageKey.GUI_SLOT_BACK_TO_VAULT), List.of()));
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
                previous.origin(),
                previous.transactionId(),
                SlotPurchaseInventoryHolder.Stage.CONFIRM,
                actions);
        Inventory inventory = Bukkit.createInventory(holder, 27, Messages.line(
                MessageKey.GUI_TITLE_SLOT_CONFIRM, Messages.of("amount", previous.slot())));
        holder.bind(inventory);
        fill(inventory);
        actions.put(11, SlotPurchaseInventoryHolder.Action.confirm(amount));
        actions.put(15, SlotPurchaseInventoryHolder.Action.cancel());
        inventory.setItem(11, GuiItems.of(
                MenuMaterials.of("slot", "confirm", Material.LIME_CONCRETE),
                Messages.line(MessageKey.GUI_SLOT_CONFIRM),
                List.of(
                        Messages.line(MessageKey.GUI_SLOT_CONFIRM_PROVIDER,
                                Messages.of("provider", Displays.of(amount.provider()))),
                        Messages.line(MessageKey.GUI_SLOT_COST,
                                Messages.of("cost", amount.value().toPlainString())),
                        Component.empty(),
                        Messages.line(MessageKey.GUI_SLOT_CONFIRM_ONCE))));
        inventory.setItem(15, GuiItems.of(
                MenuMaterials.of("slot", "cancel", Material.RED_CONCRETE),
                Messages.line(MessageKey.GUI_SLOT_CANCEL), List.of()));
        return inventory;
    }

    private static ItemStack providerOption(
            EconomyProvider provider,
            EconomyAmount amount,
            boolean available,
            SlotBalanceDisplay balance,
            String diagnostic) {
        List<Component> lore = new ArrayList<>();
        lore.add(Messages.line(MessageKey.GUI_SLOT_COST, Messages.of("cost", amount.value().toPlainString())));
        lore.add(balance != null && balance.known()
                ? Messages.line(MessageKey.GUI_SLOT_BALANCE, Messages.of("balance", balance.plain()))
                : Messages.line(MessageKey.GUI_SLOT_BALANCE_UNAVAILABLE, Messages.of("detail",
                        balance == null ? "unknown" : balance.unavailableDetail())));
        lore.add(Component.empty());
        lore.add(available
                ? Messages.line(MessageKey.GUI_SLOT_REVIEW_HINT)
                : GuiItems.label(diagnostic, GuiColors.BLOCKED));
        return GuiItems.of(
                available ? material(provider) : Material.BARRIER,
                Messages.line(provider == EconomyProvider.VAULT
                                ? MessageKey.GUI_SLOT_PAY_VAULT
                                : MessageKey.GUI_SLOT_PAY_PLAYERPOINTS)
                        .color(GuiColors.availability(available)),
                lore);
    }

    private static SlotPurchaseInventoryHolder holder(
            Player player,
            long revision,
            int slot,
            SlotPurchaseOrigin origin,
            UUID transactionId,
            SlotPurchaseInventoryHolder.Stage stage,
            Map<Integer, SlotPurchaseInventoryHolder.Action> actions) {
        return new SlotPurchaseInventoryHolder(
                player.getUniqueId(), revision, slot, origin, transactionId, stage, actions);
    }

    /** The coin for one payment option, restyleable per provider. */
    private static Material material(EconomyProvider provider) {
        return provider == EconomyProvider.VAULT
                ? MenuMaterials.of("slot", "payVault", Material.GOLD_INGOT)
                : MenuMaterials.of("slot", "payPoints", Material.NETHER_STAR);
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = GuiItems.of(
                MenuMaterials.filler("slot", org.bukkit.Material.GRAY_STAINED_GLASS_PANE),
                GuiItems.label(" ", GuiItems.LORE_COLOR), java.util.List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }
}
