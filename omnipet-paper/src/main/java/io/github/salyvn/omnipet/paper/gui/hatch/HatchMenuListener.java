package io.github.salyvn.omnipet.paper.gui.hatch;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import io.github.salyvn.omnipet.paper.player.PlayerHatchController;

public final class HatchMenuListener implements Listener {
    private final PlayerHatchController controller;

    public HatchMenuListener(PlayerHatchController controller) {
        this.controller = Objects.requireNonNull(controller, "hatch controller");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof HatchInventoryHolder holder)) return;
        event.setCancelled(true);
        if (!holder.viewerId().equals(player.getUniqueId())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        HatchInventoryHolder.Action action = holder.action(event.getRawSlot());
        if (action != null) controller.click(player, holder, action);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HatchInventoryHolder)) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < size)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof HatchInventoryHolder) {
            controller.release(player.getUniqueId());
        }
    }
}
