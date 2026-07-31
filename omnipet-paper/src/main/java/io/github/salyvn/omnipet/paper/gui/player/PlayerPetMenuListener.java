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

public final class PlayerPetMenuListener implements Listener {
    private final PlayerPetController controller;

    public PlayerPetMenuListener(PlayerPetController controller) {
        this.controller = controller;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof PlayerPetInventoryHolder holder)) return;
        event.setCancelled(true);
        if (!holder.viewerId().equals(player.getUniqueId())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        PlayerPetInventoryHolder.Action action = holder.action(event.getRawSlot());
        if (action != null) controller.click(player, holder, action);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PlayerPetInventoryHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof PlayerPetInventoryHolder) return;
        controller.release(player.getUniqueId());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof PlayerPetInventoryHolder) {
            controller.release(player.getUniqueId());
        }
    }
}
