package io.github.salyvn.omnipet.paper.gui.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.economy.PurchaseJournalScanResult;
import io.github.salyvn.omnipet.core.economy.SlotPurchaseTransaction;
import io.github.salyvn.omnipet.core.economy.SlotReconciliationDecision;
import io.github.salyvn.omnipet.paper.config.GuiSettings;
import io.github.salyvn.omnipet.paper.gui.GuiColors;
import io.github.salyvn.omnipet.paper.gui.MenuLayout;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The pending slot-transaction list, and the confirm screen for one decision.
 *
 * <p>Exists to remove the worst of the typing: reconciling a transaction meant reading a UUID out of a
 * chat page and pasting it back with a decision word. Every value the confirm screen needs is already on
 * the row the operator clicked, so clicking is what replaces the copying.
 *
 * <p>The command form stays. Its continuation cursor is an opaque token printed for verbatim re-paste,
 * which is the only way to resume a scan from a log line or a ticket — a menu holds the cursor in holder
 * state but cannot be pasted into an incident report.
 */
public final class AdminTransactionMenuRenderer {
    private static final int LIST_SIZE = 54;
    private static final int ROWS = 45;
    private static final int REFRESH_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int CONFIRM_SIZE = 27;
    private static final int SUBJECT_SLOT = 13;
    private static final int BACK_SLOT = 18;

    /** One decision per slot, in escalating order so the destructive ones are not first. */
    private static final int[] DECISION_SLOTS = {10, 11, 15, 16};
    private static final SlotReconciliationDecision[] DECISIONS = {
        SlotReconciliationDecision.ENTITLEMENT_SYNC_RETRY,
        SlotReconciliationDecision.NO_CHARGE_CONFIRMED,
        SlotReconciliationDecision.CHARGE_CONFIRMED,
        SlotReconciliationDecision.REFUND_CONFIRMED,
    };

    public Inventory renderList(Player viewer, PurchaseJournalScanResult scan, String cursor) {
        MenuLayout<AdminTransactionInventoryHolder.Action> layout = new MenuLayout<>(
                GuiSettings.gui().menu("adminTransactions"), "gui.menus.adminTransactions",
                GuiSettings::warn);

        // Rows are data, not buttons: they fill 0..44 in journal order and there is no meaningful
        // "move the third row" for an operator, so they bind directly rather than through a button name
        // that all 45 of them would have to share.
        List<SlotPurchaseTransaction> rows = scan.transactions();
        for (int index = 0; index < rows.size() && index < ROWS; index++) {
            SlotPurchaseTransaction row = rows.get(index);
            layout.putStack("row" + index, index,
                    AdminTransactionInventoryHolder.Action.open(row.transactionId()),
                    io.github.salyvn.omnipet.paper.gui.GuiItems.of(
                            layout.material("row", Material.PAPER), rowName(row), rowLore(row)));
        }
        layout.put("refresh", REFRESH_SLOT, AdminTransactionInventoryHolder.Action.refresh(),
                Material.CLOCK, Messages.line(MessageKey.GUI_ADMIN_REFRESH),
                List.of(Messages.line(MessageKey.GUI_ADMIN_TX_COUNT,
                        Messages.of("amount", rows.size()))));
        if (scan.nextCursor() != null) {
            layout.put("next", NEXT_SLOT, AdminTransactionInventoryHolder.Action.page(scan.nextCursor()),
                    Material.ARROW, Messages.line(MessageKey.GUI_ADMIN_NEXT_PAGE), List.of());
        }

        AdminTransactionInventoryHolder holder = new AdminTransactionInventoryHolder(
                viewer.getUniqueId(), AdminTransactionInventoryHolder.View.LIST, cursor, null,
                layout.actions());
        Inventory inventory = Bukkit.createInventory(
                holder, layout.size(LIST_SIZE), Messages.line(MessageKey.GUI_TITLE_ADMIN_TRANSACTIONS));
        holder.bind(inventory);
        layout.draw(inventory);
        return inventory;
    }

    /**
     * The confirm screen for one transaction.
     *
     * <p>Built from a freshly read row rather than the one the list rendered, so a transaction another
     * operator already reconciled cannot be acted on from a stale menu.
     */
    public Inventory renderConfirm(Player viewer, SlotPurchaseTransaction row, String cursor) {
        MenuLayout<AdminTransactionInventoryHolder.Action> layout = new MenuLayout<>(
                GuiSettings.gui().menu("adminReconcile"), "gui.menus.adminReconcile", GuiSettings::warn);

        layout.decorate("subject", SUBJECT_SLOT,
                io.github.salyvn.omnipet.paper.gui.GuiItems.of(Material.PAPER, rowName(row), rowLore(row)));
        for (int index = 0; index < DECISIONS.length; index++) {
            SlotReconciliationDecision decision = DECISIONS[index];
            layout.put(button(decision), DECISION_SLOTS[index],
                    AdminTransactionInventoryHolder.Action.decide(row.transactionId(), decision),
                    material(decision),
                    Messages.line(MessageKey.GUI_ADMIN_DECISION,
                            Messages.of("status", Displays.words(decision))),
                    List.of(
                            Messages.line(decisionHint(decision)),
                            Component.empty(),
                            Messages.line(MessageKey.GUI_ADMIN_DECISION_IRREVERSIBLE)));
        }
        layout.put("back", BACK_SLOT, AdminTransactionInventoryHolder.Action.back(),
                Material.COMPASS, Messages.line(MessageKey.GUI_ADMIN_BACK), List.of());

        AdminTransactionInventoryHolder holder = new AdminTransactionInventoryHolder(
                viewer.getUniqueId(), AdminTransactionInventoryHolder.View.CONFIRM, cursor,
                row.transactionId(), layout.actions());
        Inventory inventory = Bukkit.createInventory(
                holder, layout.size(CONFIRM_SIZE), Messages.line(MessageKey.GUI_TITLE_ADMIN_RECONCILE));
        holder.bind(inventory);
        layout.draw(inventory);
        return inventory;
    }

    private static Component rowName(SlotPurchaseTransaction row) {
        return Messages.line(MessageKey.GUI_ADMIN_TX_NAME,
                        Messages.of("amount", row.slot()),
                        Messages.of("status", Displays.words(row.state())))
                .colorIfAbsent(GuiColors.TITLE);
    }

    /**
     * Everything a decision needs, so the operator never has to cross-reference a chat page.
     *
     * <p>The transaction UUID is shown but never has to be typed: the click carries it.
     */
    private static List<Component> rowLore(SlotPurchaseTransaction row) {
        List<Component> lore = new ArrayList<>();
        lore.add(Messages.line(MessageKey.GUI_ADMIN_TX_PLAYER,
                Messages.of("detail", playerName(row.playerId()))));
        lore.add(Messages.line(MessageKey.GUI_ADMIN_TX_AMOUNT,
                Messages.of("detail", row.amount().provider() + " " + row.amount().value().toPlainString())));
        lore.add(Messages.line(MessageKey.GUI_ADMIN_TX_ID,
                Messages.of("detail", row.transactionId().toString())));
        if (!row.detail().isBlank()) {
            lore.add(Messages.line(MessageKey.GUI_ADMIN_TX_DETAIL, Messages.of("detail", row.detail())));
        }
        lore.add(Component.empty());
        lore.add(Messages.line(MessageKey.GUI_ADMIN_TX_HINT));
        return lore;
    }

    /** An online name when there is one; the UUID is always in the lore, so this can be a convenience. */
    private static String playerName(UUID playerId) {
        var online = Bukkit.getServer() == null ? null : Bukkit.getPlayer(playerId);
        return online == null ? playerId.toString() : online.getName();
    }

    private static String button(SlotReconciliationDecision decision) {
        return switch (decision) {
            case CHARGE_CONFIRMED -> "charge";
            case NO_CHARGE_CONFIRMED -> "noCharge";
            case REFUND_CONFIRMED -> "refund";
            case ENTITLEMENT_SYNC_RETRY -> "sync";
        };
    }

    private static Material material(SlotReconciliationDecision decision) {
        return switch (decision) {
            case CHARGE_CONFIRMED -> Material.GOLD_INGOT;
            case NO_CHARGE_CONFIRMED -> Material.LIME_DYE;
            case REFUND_CONFIRMED -> Material.REDSTONE;
            case ENTITLEMENT_SYNC_RETRY -> Material.COMPARATOR;
        };
    }

    private static MessageKey decisionHint(SlotReconciliationDecision decision) {
        return switch (decision) {
            case CHARGE_CONFIRMED -> MessageKey.GUI_ADMIN_DECISION_CHARGE;
            case NO_CHARGE_CONFIRMED -> MessageKey.GUI_ADMIN_DECISION_NO_CHARGE;
            case REFUND_CONFIRMED -> MessageKey.GUI_ADMIN_DECISION_REFUND;
            case ENTITLEMENT_SYNC_RETRY -> MessageKey.GUI_ADMIN_DECISION_SYNC;
        };
    }
}
