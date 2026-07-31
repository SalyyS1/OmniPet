package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class PlayerPetInventoryHolder implements InventoryHolder {
    private final UUID viewerId;
    private final long expectedRevision;
    private final int page;
    private final Map<Integer, Action> actions;
    private Inventory inventory;

    public PlayerPetInventoryHolder(UUID viewerId, long expectedRevision, int page, Map<Integer, Action> actions) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        if (expectedRevision < 0) throw new IllegalArgumentException("expected revision cannot be negative");
        if (page < 1) throw new IllegalArgumentException("page must be one-based");
        this.expectedRevision = expectedRevision;
        this.page = page;
        this.actions = Collections.unmodifiableMap(actions == null ? Map.of() : actions);
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public UUID viewerId() { return viewerId; }

    public long expectedRevision() { return expectedRevision; }

    public int page() { return page; }

    public Action action(int rawSlot) { return actions.get(rawSlot); }

    @Override
    public Inventory getInventory() { return inventory; }

    public record Action(Type type, UUID petId, boolean active) {
        public Action {
            Objects.requireNonNull(type, "action type");
            if (type == Type.PET && petId == null) throw new IllegalArgumentException("pet action requires an id");
            if (type != Type.PET && petId != null) throw new IllegalArgumentException("navigation action cannot carry a pet id");
        }

        public static Action pet(UUID petId, boolean active) { return new Action(Type.PET, petId, active); }

        public static Action previous() { return new Action(Type.PREVIOUS, null, false); }

        public static Action next() { return new Action(Type.NEXT, null, false); }
    }

    public enum Type { PET, PREVIOUS, NEXT }
}
