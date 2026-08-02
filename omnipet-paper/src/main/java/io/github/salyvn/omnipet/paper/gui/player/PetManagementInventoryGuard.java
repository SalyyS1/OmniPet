package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Set;
import java.util.UUID;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

public final class PetManagementInventoryGuard {
    private PetManagementInventoryGuard() {}

    public static boolean acceptsAction(
            PetManagementInventoryHolder holder,
            UUID viewerId,
            Inventory currentTop,
            int rawSlot,
            int topSize,
            ClickType click) {
        if (holder == null || viewerId == null || currentTop == null || click == null) return false;
        return holder.viewerId().equals(viewerId)
                && holder.getInventory() == currentTop
                && rawSlot >= 0
                && rawSlot < topSize
                && (click == ClickType.LEFT || click == ClickType.RIGHT)
                && holder.action(rawSlot) != null;
    }

    public static boolean cancelsDrag(Set<Integer> rawSlots, int topSize) {
        return rawSlots != null && rawSlots.stream().anyMatch(slot -> slot >= 0 && slot < topSize);
    }
}
