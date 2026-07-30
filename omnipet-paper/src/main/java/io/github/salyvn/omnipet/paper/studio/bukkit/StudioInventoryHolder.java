package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collections;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import io.github.salyvn.omnipet.paper.studio.session.StudioViewToken;

/** Inventory capability bound to one viewer, session, and immutable action map. */
public final class StudioInventoryHolder implements InventoryHolder {
    public enum Screen { TIERS, LIST, EDITOR, ARCHIVE_CONFIRM }

    private final UUID viewerId;
    private final StudioViewToken token;
    private final Screen screen;
    private final Map<Integer, StudioAction> actions;
    private Inventory inventory;

    public StudioInventoryHolder(
            UUID viewerId,
            StudioViewToken token,
            Screen screen,
            Map<Integer, StudioAction> actions) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.token = Objects.requireNonNull(token, "token");
        if (!viewerId.equals(token.viewerId())) throw new IllegalArgumentException("viewer and token differ");
        this.screen = Objects.requireNonNull(screen, "screen");
        this.actions = actions == null ? Map.of() : Collections.unmodifiableMap(actions);
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public UUID viewerId() { return viewerId; }

    public StudioViewToken token() { return token; }

    public Screen screen() { return screen; }

    public StudioAction action(int rawSlot) { return actions.get(rawSlot); }

    @Override
    public Inventory getInventory() { return inventory; }
}
