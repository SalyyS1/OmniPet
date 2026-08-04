package io.github.salyvn.omnipet.paper.gui.admin;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import io.github.salyvn.omnipet.core.economy.SlotReconciliationDecision;

/**
 * Admin transaction inventory capability, bound to one operator and one page of the journal.
 *
 * <p>Mirrors the player holders: the action map is snapshotted at construction so a caller cannot mutate
 * bindings after the inventory is shown.
 *
 * <p>A reconcile decision moves real money, so the holder carries the transaction it was rendered for and
 * the page it came from. The confirm screen re-reads the journal before acting; the holder is what proves
 * the click belongs to the row the operator actually saw.
 */
public final class AdminTransactionInventoryHolder implements InventoryHolder {
    private final UUID viewerId;
    private final View view;
    private final String cursor;
    private final UUID subject;
    private final Map<Integer, Action> actions;
    private Inventory inventory;

    public AdminTransactionInventoryHolder(
            UUID viewerId, View view, String cursor, UUID subject, Map<Integer, Action> actions) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.view = Objects.requireNonNull(view, "view");
        this.cursor = cursor;
        this.subject = subject;
        this.actions = Collections.unmodifiableMap(actions == null ? Map.of() : Map.copyOf(actions));
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public UUID viewerId() {
        return viewerId;
    }

    public View view() {
        return view;
    }

    /** The opaque continuation cursor this page was loaded with, so paging can resume from it. */
    public String cursor() {
        return cursor;
    }

    /** The transaction a confirm screen is about, or null on a list page. */
    public UUID subject() {
        return subject;
    }

    public Action action(int rawSlot) {
        return actions.get(rawSlot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Which screen this is. A confirm screen only ever acts on its own {@link #subject()}. */
    public enum View { LIST, CONFIRM }

    /** What a click does. */
    public record Action(Type type, UUID transactionId, SlotReconciliationDecision decision, String cursor) {
        public Action {
            Objects.requireNonNull(type, "action type");
        }

        public static Action refresh() {
            return new Action(Type.REFRESH, null, null, null);
        }

        /** Loads another page from an opaque cursor the operator never has to see. */
        public static Action page(String cursor) {
            return new Action(Type.PAGE, null, null, cursor);
        }

        /** Opens the confirm screen for one transaction. */
        public static Action open(UUID transactionId) {
            return new Action(Type.OPEN, Objects.requireNonNull(transactionId, "transaction id"), null, null);
        }

        /** Applies one decision to one transaction. Irreversible; only reachable from a confirm screen. */
        public static Action decide(UUID transactionId, SlotReconciliationDecision decision) {
            return new Action(Type.DECIDE,
                    Objects.requireNonNull(transactionId, "transaction id"),
                    Objects.requireNonNull(decision, "decision"), null);
        }

        public static Action back() {
            return new Action(Type.BACK, null, null, null);
        }
    }

    public enum Type { REFRESH, PAGE, OPEN, DECIDE, BACK }
}
