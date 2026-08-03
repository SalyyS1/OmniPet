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
    private final VaultViewState view;
    private final Map<Integer, Action> actions;
    private Inventory inventory;

    /** Retained so existing callers and their tests keep working; the view defaults to page-only. */
    public PlayerPetInventoryHolder(UUID viewerId, long expectedRevision, int page, Map<Integer, Action> actions) {
        // Validated here rather than after conversion: VaultViewState clamps a page so that paging
        // arithmetic cannot walk off the front, but a caller passing a page directly is stating a fact
        // and a nonsense value is a bug worth surfacing.
        this(viewerId, expectedRevision, requireOneBased(page), actions);
    }

    public PlayerPetInventoryHolder(
            UUID viewerId, long expectedRevision, VaultViewState view, Map<Integer, Action> actions) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        if (expectedRevision < 0) throw new IllegalArgumentException("expected revision cannot be negative");
        this.view = Objects.requireNonNull(view, "vault view state");
        this.expectedRevision = expectedRevision;
        this.page = view.page();
        this.actions = Collections.unmodifiableMap(actions == null ? Map.of() : actions);
    }

    private static VaultViewState requireOneBased(int page) {
        if (page < 1) throw new IllegalArgumentException("page must be one-based");
        return VaultViewState.page(page);
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public UUID viewerId() { return viewerId; }

    public long expectedRevision() { return expectedRevision; }

    public int page() { return page; }

    /** Sort, filter, and page, so a re-render can preserve what the player was looking at. */
    public VaultViewState view() { return view; }

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

        public static Action purchaseSlot() { return new Action(Type.PURCHASE_SLOT, null, false); }

        public static Action hub() { return new Action(Type.HUB, null, false); }

        public static Action sort() { return new Action(Type.SORT, null, false); }

        public static Action filter() { return new Action(Type.FILTER, null, false); }
    }

    public enum Type { PET, PREVIOUS, NEXT, PURCHASE_SLOT, HUB, SORT, FILTER }
}
