package io.github.salyvn.omnipet.paper.studio.view;

import java.util.Objects;
import java.util.Set;

/** Pure event gate used before any studio controller dispatch. */
public final class StudioInventoryActionPolicy {
    private static final Set<StudioClickType> DENIED_CLICKS = Set.of(
            StudioClickType.SHIFT_LEFT,
            StudioClickType.SHIFT_RIGHT,
            StudioClickType.NUMBER_KEY,
            StudioClickType.DOUBLE_CLICK,
            StudioClickType.SWAP_OFFHAND,
            StudioClickType.CREATIVE,
            StudioClickType.UNKNOWN);
    private static final Set<StudioInventoryAction> DENIED_ACTIONS = Set.of(
            StudioInventoryAction.MOVE_TO_OTHER_INVENTORY,
            StudioInventoryAction.HOTBAR_SWAP,
            StudioInventoryAction.HOTBAR_MOVE_AND_READD,
            StudioInventoryAction.COLLECT_TO_CURSOR,
            StudioInventoryAction.SWAP_WITH_CURSOR,
            StudioInventoryAction.CLONE_STACK,
            StudioInventoryAction.DROP_ALL_CURSOR,
            StudioInventoryAction.DROP_ONE_CURSOR,
            StudioInventoryAction.UNKNOWN);

    private StudioInventoryActionPolicy() {}

    public static StudioActionDecision click(
            boolean viewerMatches,
            boolean studioInventory,
            int rawSlot,
            int topInventorySize,
            StudioClickType click,
            StudioInventoryAction action) {
        if (!viewerMatches) return StudioActionDecision.deny("viewer mismatch");
        if (!studioInventory) return StudioActionDecision.deny("inventory mismatch");
        if (topInventorySize <= 0) return StudioActionDecision.deny("invalid inventory size");
        if (rawSlot < 0 || rawSlot >= topInventorySize) return StudioActionDecision.deny("bottom inventory action");
        if (click == null || DENIED_CLICKS.contains(click)) return StudioActionDecision.deny("unsafe click type");
        if (action == null || DENIED_ACTIONS.contains(action)) return StudioActionDecision.deny("unsafe inventory action");
        return StudioActionDecision.allow();
    }

    public static StudioActionDecision drag(
            boolean viewerMatches,
            boolean studioInventory,
            int topInventorySize,
            Set<Integer> rawSlots) {
        Objects.requireNonNull(rawSlots, "rawSlots");
        if (!viewerMatches) return StudioActionDecision.deny("viewer mismatch");
        if (!studioInventory) return StudioActionDecision.deny("inventory mismatch");
        if (topInventorySize <= 0) return StudioActionDecision.deny("invalid inventory size");
        if (rawSlots.isEmpty()) return StudioActionDecision.deny("empty drag");
        if (rawSlots.stream().anyMatch(slot -> slot < 0 || slot < topInventorySize)) {
            return StudioActionDecision.deny("drag touches studio inventory");
        }
        return StudioActionDecision.deny("studio inventory is read-only");
    }
}
