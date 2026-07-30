package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import io.github.salyvn.omnipet.paper.studio.view.StudioClickType;
import io.github.salyvn.omnipet.paper.studio.view.StudioInventoryAction;
import io.github.salyvn.omnipet.paper.studio.view.StudioInventoryActionPolicy;

public final class PetStudioListener implements Listener {
    private final PetStudioController controller;

    public PetStudioListener(PetStudioController controller) {
        this.controller = controller;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof StudioInventoryHolder holder)) return;
        event.setCancelled(true);
        var decision = StudioInventoryActionPolicy.click(
                holder.viewerId().equals(player.getUniqueId()),
                event.getView().getTopInventory().getHolder() == holder,
                event.getRawSlot(),
                event.getView().getTopInventory().getSize(),
                clickType(event.getClick()),
                inventoryAction(event.getAction()));
        if (!decision.allowed()) return;
        StudioAction action = holder.action(event.getRawSlot());
        if (action != null) controller.click(player, holder, action);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof StudioInventoryHolder holder)) return;
        event.setCancelled(true);
        StudioInventoryActionPolicy.drag(
                holder.viewerId().equals(player.getUniqueId()),
                event.getView().getTopInventory().getHolder() == holder,
                event.getView().getTopInventory().getSize(),
                Set.copyOf(event.getRawSlots()));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getHolder() instanceof StudioInventoryHolder holder) {
            controller.onClose(holder, player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { controller.onQuit(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onKick(PlayerKickEvent event) { controller.onKick(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!controller.capturesChat(player.getUniqueId())) return;
        event.setCancelled(true);
        controller.captureChat(player.getUniqueId(), PlainTextComponentSerializer.plainText().serialize(event.message()));
    }

    private static StudioClickType clickType(ClickType click) {
        if (click == null) return StudioClickType.UNKNOWN;
        if (click.isShiftClick()) return click.isLeftClick() ? StudioClickType.SHIFT_LEFT : StudioClickType.SHIFT_RIGHT;
        if (click.isKeyboardClick()) return StudioClickType.NUMBER_KEY;
        return switch (click) {
            case LEFT -> StudioClickType.LEFT;
            case RIGHT -> StudioClickType.RIGHT;
            case DOUBLE_CLICK -> StudioClickType.DOUBLE_CLICK;
            case SWAP_OFFHAND -> StudioClickType.SWAP_OFFHAND;
            case CREATIVE -> StudioClickType.CREATIVE;
            default -> StudioClickType.UNKNOWN;
        };
    }

    private static StudioInventoryAction inventoryAction(InventoryAction action) {
        if (action == null) return StudioInventoryAction.UNKNOWN;
        return switch (action) {
            case PICKUP_ALL -> StudioInventoryAction.PICKUP_ALL;
            case PICKUP_HALF, PICKUP_ONE, PICKUP_SOME -> StudioInventoryAction.PICKUP;
            case PLACE_ALL, PLACE_SOME, PLACE_ONE -> StudioInventoryAction.PLACE;
            case MOVE_TO_OTHER_INVENTORY -> StudioInventoryAction.MOVE_TO_OTHER_INVENTORY;
            case HOTBAR_SWAP -> StudioInventoryAction.HOTBAR_SWAP;
            case HOTBAR_MOVE_AND_READD -> StudioInventoryAction.HOTBAR_MOVE_AND_READD;
            case COLLECT_TO_CURSOR -> StudioInventoryAction.COLLECT_TO_CURSOR;
            case SWAP_WITH_CURSOR -> StudioInventoryAction.SWAP_WITH_CURSOR;
            case CLONE_STACK -> StudioInventoryAction.CLONE_STACK;
            case DROP_ALL_CURSOR -> StudioInventoryAction.DROP_ALL_CURSOR;
            case DROP_ONE_CURSOR -> StudioInventoryAction.DROP_ONE_CURSOR;
            default -> StudioInventoryAction.UNKNOWN;
        };
    }
}
