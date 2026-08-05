package io.github.salyvn.omnipet.paper.gui.egg;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import io.github.salyvn.omnipet.paper.incubation.placed.PlacedEggSupportController;

/**
 * Clicks in the placed-egg menu.
 *
 * <p>Every click in the top inventory is cancelled before anything else happens, including clicks on slots
 * with no action. The menu has no real storage — the whole point of the design is that a support item is
 * never deposited anywhere it could be lost — so a click that moved an item would be a way to lose one.
 * Drags are refused for the same reason.
 */
public final class PlacedEggMenuListener implements Listener {
    private final PlacedEggSupportController controller;

    public PlacedEggMenuListener(PlacedEggSupportController controller) {
        this.controller = Objects.requireNonNull(controller, "placed egg support controller");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof PlacedEggInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!holder.viewerId().equals(player.getUniqueId())) return;
        if (event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        PlacedEggInventoryHolder.Action action = holder.action(event.getRawSlot());
        if (action != null) controller.click(player, holder, action);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PlacedEggInventoryHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }
}
