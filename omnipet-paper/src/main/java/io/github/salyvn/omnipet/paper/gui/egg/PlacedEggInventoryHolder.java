package io.github.salyvn.omnipet.paper.gui.egg;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * The menu opened by right-clicking an egg that is incubating in the world.
 *
 * <p>Identified by the record's block key rather than by a block reference, so a record deleted, reclaimed,
 * or hatched while the menu is open resolves to nothing on the next click instead of acting on a stale
 * block.
 *
 * <p>{@code expectedRemainingMillis} is what the menu was drawn from. A click carries it back so the
 * controller can tell a player looking at a stale screen — a second passes and the countdown moves — from
 * one acting on what they can actually see. It is only used for display; the record on disk decides.
 *
 * <p>There are no real storage slots here on purpose. Letting a player deposit an item into the menu would
 * make this GUI a container, and a container is a place items can be lost: a crash, a plugin conflict, or a
 * close-during-write would take a paid item with it. The player keeps the item in hand and clicks the
 * button that spends it, which reuses the journaled redemption path that already exists for held eggs.
 */
public final class PlacedEggInventoryHolder implements InventoryHolder {
    private final UUID viewerId;
    private final String recordKey;
    private final long expectedRemainingMillis;
    private final Map<Integer, Action> actions;
    private Inventory inventory;

    public PlacedEggInventoryHolder(
            UUID viewerId,
            String recordKey,
            long expectedRemainingMillis,
            Map<Integer, Action> actions) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewer id");
        this.recordKey = requireKey(recordKey);
        if (expectedRemainingMillis < 0) {
            throw new IllegalArgumentException("expected remaining time cannot be negative");
        }
        this.expectedRemainingMillis = expectedRemainingMillis;
        this.actions = Collections.unmodifiableMap(actions == null ? Map.of() : actions);
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public UUID viewerId() { return viewerId; }

    public String recordKey() { return recordKey; }

    public long expectedRemainingMillis() { return expectedRemainingMillis; }

    public Action action(int rawSlot) { return actions.get(rawSlot); }

    @Override
    public Inventory getInventory() { return inventory; }

    private static String requireKey(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("record key is required");
        return value;
    }

    /**
     * One button.
     *
     * <p>The hand is part of the action rather than resolved at click time, because a player holding a
     * reducer in each hand must be able to say which one they meant.
     */
    public record Action(Type type, boolean offHand) {
        public Action {
            Objects.requireNonNull(type, "placed egg action type");
            if (type != Type.REDEEM && offHand) {
                throw new IllegalArgumentException("only a redemption names a hand");
            }
        }

        public static Action redeemMain() { return new Action(Type.REDEEM, false); }

        public static Action redeemOffHand() { return new Action(Type.REDEEM, true); }

        public static Action refresh() { return new Action(Type.REFRESH, false); }

        public static Action hub() { return new Action(Type.HUB, false); }
    }

    public enum Type { REDEEM, REFRESH, HUB }
}
