package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import io.github.salyvn.omnipet.core.economy.EconomyAmount;

public final class SlotPurchaseInventoryHolder implements InventoryHolder {
    private final UUID viewerId;
    private final long expectedRevision;
    private final int slot;
    private final SlotPurchaseOrigin origin;
    private final UUID transactionId;
    private final Stage stage;
    private final Map<Integer, Action> actions;
    private Inventory inventory;

    public SlotPurchaseInventoryHolder(
            UUID viewerId,
            long expectedRevision,
            int slot,
            SlotPurchaseOrigin origin,
            UUID transactionId,
            Stage stage,
            Map<Integer, Action> actions) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewer id");
        if (expectedRevision < 0) throw new IllegalArgumentException("expected revision cannot be negative");
        if (slot < 2 || slot > 64) throw new IllegalArgumentException("slot is outside the supported range");
        this.expectedRevision = expectedRevision;
        this.slot = slot;
        this.origin = Objects.requireNonNull(origin, "slot purchase origin");
        this.transactionId = Objects.requireNonNull(transactionId, "transaction id");
        this.stage = Objects.requireNonNull(stage, "purchase stage");
        this.actions = Collections.unmodifiableMap(actions == null ? Map.of() : actions);
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public UUID viewerId() { return viewerId; }
    public long expectedRevision() { return expectedRevision; }
    public int slot() { return slot; }
    public SlotPurchaseOrigin origin() { return origin; }
    public UUID transactionId() { return transactionId; }
    public Stage stage() { return stage; }
    public Action action(int rawSlot) { return actions.get(rawSlot); }

    @Override
    public Inventory getInventory() { return inventory; }

    public record Action(Type type, EconomyAmount amount) {
        public Action {
            Objects.requireNonNull(type, "purchase action type");
            if (type != Type.CANCEL && amount == null) {
                throw new IllegalArgumentException("provider action requires an amount");
            }
            if (type == Type.CANCEL && amount != null) {
                throw new IllegalArgumentException("cancel action cannot carry an amount");
            }
        }

        public static Action select(EconomyAmount amount) { return new Action(Type.SELECT, amount); }
        public static Action confirm(EconomyAmount amount) { return new Action(Type.CONFIRM, amount); }
        public static Action cancel() { return new Action(Type.CANCEL, null); }
    }

    public enum Stage { SELECT_PROVIDER, CONFIRM }
    public enum Type { SELECT, CONFIRM, CANCEL }
}
