package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Objects;

/**
 * Where a slot purchase screen was opened from, so cancelling or completing it returns the player
 * to that screen rather than always to the vault.
 *
 * <p>The purchase flow replays a command to navigate back. Before this type it replayed
 * {@code "pet " + returnPage} unconditionally, so a player who opened the slot menu from the hub was
 * silently relocated to vault page 1. The return command now belongs to the origin, in one place,
 * rather than being reconstructed at each of the three return points.
 */
public record SlotPurchaseOrigin(Kind kind, int vaultPage) {
    public SlotPurchaseOrigin {
        Objects.requireNonNull(kind, "slot purchase origin kind");
        if (vaultPage < 1) throw new IllegalArgumentException("vault page must be one-based");
    }

    /**
     * Opened from the hub. {@code vaultPage} is a placeholder that satisfies the one-based invariant
     * shared with {@link #vault(int)} and is never read: {@link #returnCommand()} ignores it for
     * {@link Kind#HUB}.
     */
    public static SlotPurchaseOrigin hub() {
        return new SlotPurchaseOrigin(Kind.HUB, 1);
    }

    /** Opened from vault page {@code page}, which is where the player returns to. */
    public static SlotPurchaseOrigin vault(int page) {
        return new SlotPurchaseOrigin(Kind.VAULT, page);
    }

    /**
     * The command replayed to return the player to where they started. A bare {@code pet} opens the
     * hub; {@code pet <page>} opens that vault page.
     */
    public String returnCommand() {
        return kind == Kind.HUB ? "pet" : "pet " + vaultPage;
    }

    public enum Kind { HUB, VAULT }
}
