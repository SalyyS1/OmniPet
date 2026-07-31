package io.github.salyvn.omnipet.paper.gui.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import io.github.salyvn.omnipet.paper.player.PlayerPetController;
import io.github.salyvn.omnipet.paper.player.PlayerSlotPurchaseController;

public final class PlayerPetMenuListener implements Listener {
    private final PlayerPetController controller;
    private final PlayerSlotPurchaseController slotPurchases;

    public PlayerPetMenuListener(
            PlayerPetController controller,
            PlayerSlotPurchaseController slotPurchases) {
        this.controller = controller;
        this.slotPurchases = slotPurchases;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Object rawHolder = event.getView().getTopInventory().getHolder();
        if (!(rawHolder instanceof PlayerPetInventoryHolder)
                && !(rawHolder instanceof SlotPurchaseInventoryHolder)) return;
        event.setCancelled(true);
        UUIDView holder = UUIDView.of(rawHolder);
        if (!holder.viewerId().equals(player.getUniqueId())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        if (rawHolder instanceof PlayerPetInventoryHolder vault) {
            PlayerPetInventoryHolder.Action action = vault.action(event.getRawSlot());
            if (action != null) controller.click(player, vault, action);
        } else if (rawHolder instanceof SlotPurchaseInventoryHolder purchase) {
            SlotPurchaseInventoryHolder.Action action = purchase.action(event.getRawSlot());
            if (action != null) slotPurchases.click(player, purchase, action);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof PlayerPetInventoryHolder)
                && !(holder instanceof SlotPurchaseInventoryHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof PlayerPetInventoryHolder
                || event.getInventory().getHolder() instanceof SlotPurchaseInventoryHolder) return;
        controller.release(player.getUniqueId());
        slotPurchases.release(player.getUniqueId());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof PlayerPetInventoryHolder) {
            controller.release(player.getUniqueId());
        } else if (event.getInventory().getHolder() instanceof SlotPurchaseInventoryHolder) {
            slotPurchases.release(player.getUniqueId());
        }
    }

    private record UUIDView(java.util.UUID viewerId) {
        static UUIDView of(Object holder) {
            if (holder instanceof PlayerPetInventoryHolder vault) return new UUIDView(vault.viewerId());
            return new UUIDView(((SlotPurchaseInventoryHolder) holder).viewerId());
        }
    }
}
