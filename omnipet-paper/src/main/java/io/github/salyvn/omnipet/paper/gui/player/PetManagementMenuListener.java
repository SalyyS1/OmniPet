package io.github.salyvn.omnipet.paper.gui.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import io.github.salyvn.omnipet.paper.management.PetManagementMenuController;

public final class PetManagementMenuListener implements Listener {
    private final PetManagementMenuController controller;

    public PetManagementMenuListener(PetManagementMenuController controller) {
        this.controller = java.util.Objects.requireNonNull(controller, "management menu controller");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof PetManagementInventoryHolder holder)) return;
        event.setCancelled(true);
        if (!PetManagementInventoryGuard.acceptsAction(
                holder, player.getUniqueId(), event.getView().getTopInventory(), event.getRawSlot(),
                event.getView().getTopInventory().getSize(), event.getClick())) return;
        controller.click(player, holder, holder.action(event.getRawSlot()));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PetManagementInventoryHolder)) return;
        if (PetManagementInventoryGuard.cancelsDrag(
                event.getRawSlots(), event.getView().getTopInventory().getSize())) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PetManagementInventoryHolder holder) {
            controller.close(holder, event.getInventory());
        }
    }
}
