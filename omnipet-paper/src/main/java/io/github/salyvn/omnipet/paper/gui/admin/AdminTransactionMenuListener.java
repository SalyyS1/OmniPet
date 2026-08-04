package io.github.salyvn.omnipet.paper.gui.admin;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import io.github.salyvn.omnipet.paper.economy.SlotTransactionAdminController;

/**
 * Admin transaction click guards, copied guard-for-guard from {@code HubMenuListener}.
 *
 * <p>Cancels every click, verifies the viewer, bound-checks the raw slot, accepts only left and right
 * clicks, and cancels any drag touching the top inventory.
 *
 * <p>Adds one guard the player menus do not need: the permission is re-checked on every click. A menu can
 * sit open across a permission change, and these clicks move money.
 *
 * <p>A {@code DECIDE} click is additionally required to come from a {@code CONFIRM} view whose subject is
 * the transaction being decided. That makes a decision unreachable from a list row even if a future
 * renderer bound one there by mistake — the same class of protection the release confirmation uses.
 */
public final class AdminTransactionMenuListener implements Listener {
    private final SlotTransactionAdminController controller;
    private final String permission;

    public AdminTransactionMenuListener(SlotTransactionAdminController controller, String permission) {
        this.controller = Objects.requireNonNull(controller, "transaction controller");
        this.permission = Objects.requireNonNull(permission, "reconcile permission");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder()
                instanceof AdminTransactionInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!holder.viewerId().equals(player.getUniqueId())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        // Re-checked per click: a menu can outlive the permission that opened it, and these clicks move
        // real money.
        if (!player.hasPermission(permission)) return;
        AdminTransactionInventoryHolder.Action action = holder.action(event.getRawSlot());
        if (action == null) return;
        dispatch(player, holder, action);
    }

    private void dispatch(
            Player player,
            AdminTransactionInventoryHolder holder,
            AdminTransactionInventoryHolder.Action action) {
        switch (action.type()) {
            case REFRESH -> controller.openMenu(player, holder.cursor());
            case PAGE -> controller.openMenu(player, action.cursor());
            case OPEN -> controller.openConfirm(player, action.transactionId(), holder.cursor(),
                    () -> player.sendMessage(
                            io.github.salyvn.omnipet.paper.text.Messages.line(
                                    io.github.salyvn.omnipet.paper.text.MessageKey.GUI_ADMIN_TX_GONE)));
            case BACK -> controller.openMenu(player, holder.cursor());
            case DECIDE -> {
                // Only from the confirm screen, and only for the transaction that screen is about.
                if (holder.view() != AdminTransactionInventoryHolder.View.CONFIRM) return;
                if (!action.transactionId().equals(holder.subject())) return;
                controller.reconcileFromMenu(
                        player, action.transactionId(), action.decision(), holder.cursor());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AdminTransactionInventoryHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }
}
