package io.github.salyvn.omnipet.paper.gui.hub;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Hub inventory capability, bound to one viewer and one observed revision.
 *
 * <p>Mirrors {@code PlayerPetInventoryHolder}: the action map is snapshotted at construction, so a
 * caller cannot mutate it after the inventory is shown, and a stale view is detectable by comparing
 * {@link #expectedRevision()}.
 */
public final class HubInventoryHolder implements InventoryHolder {
    private final UUID viewerId;
    private final long expectedRevision;
    private final Map<Integer, Action> actions;
    private Inventory inventory;

    public HubInventoryHolder(UUID viewerId, long expectedRevision, Map<Integer, Action> actions) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        if (expectedRevision < 0) throw new IllegalArgumentException("expected revision cannot be negative");
        this.expectedRevision = expectedRevision;
        this.actions = Collections.unmodifiableMap(actions == null ? Map.of() : Map.copyOf(actions));
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public UUID viewerId() {
        return viewerId;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public Action action(int rawSlot) {
        return actions.get(rawSlot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Where a hub tile routes. Every destination is an existing controller. */
    public enum Action {
        VAULT,
        HATCH,
        SLOTS,
        HELP,
        STUDIO
    }
}
