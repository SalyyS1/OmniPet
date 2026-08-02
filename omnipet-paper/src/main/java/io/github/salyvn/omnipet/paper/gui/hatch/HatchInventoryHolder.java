package io.github.salyvn.omnipet.paper.gui.hatch;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class HatchInventoryHolder implements InventoryHolder {
    private final UUID viewerId;
    private final long expectedRevision;
    private final UUID incubationId;
    private final Map<Integer, Action> actions;
    private Inventory inventory;

    public HatchInventoryHolder(
            UUID viewerId,
            long expectedRevision,
            UUID incubationId,
            Map<Integer, Action> actions) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewer id");
        if (expectedRevision < 0) throw new IllegalArgumentException("expected revision cannot be negative");
        this.expectedRevision = expectedRevision;
        this.incubationId = incubationId;
        this.actions = Collections.unmodifiableMap(actions == null ? Map.of() : actions);
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public UUID viewerId() { return viewerId; }

    public long expectedRevision() { return expectedRevision; }

    public UUID incubationId() { return incubationId; }

    public Action action(int rawSlot) { return actions.get(rawSlot); }

    @Override
    public Inventory getInventory() { return inventory; }

    public record Action(Type type) {
        public Action { Objects.requireNonNull(type, "hatch action type"); }

        public static Action startMain() { return new Action(Type.START_MAIN); }

        public static Action startOffHand() { return new Action(Type.START_OFF_HAND); }

        public static Action claim() { return new Action(Type.CLAIM); }

        public static Action refresh() { return new Action(Type.REFRESH); }
    }

    public enum Type { START_MAIN, START_OFF_HAND, CLAIM, REFRESH }
}
