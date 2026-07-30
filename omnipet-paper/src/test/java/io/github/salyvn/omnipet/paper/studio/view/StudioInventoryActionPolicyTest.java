package io.github.salyvn.omnipet.paper.studio.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class StudioInventoryActionPolicyTest {
    @Test
    void allowsOnlyNormalTopInventoryButtonClicks() {
        StudioActionDecision decision = StudioInventoryActionPolicy.click(true, true, 10, 54,
                StudioClickType.LEFT, StudioInventoryAction.PICKUP);
        assertTrue(decision.allowed());
        assertTrue(decision.cancelInventoryMutation());
        assertTrue(StudioInventoryActionPolicy.click(true, true, 11, 54,
                StudioClickType.RIGHT, StudioInventoryAction.PLACE).allowed());
    }

    @Test
    void rejectsBottomViewerAndInventoryMismatches() {
        assertFalse(StudioInventoryActionPolicy.click(true, true, 54, 54,
                StudioClickType.LEFT, StudioInventoryAction.PICKUP).allowed());
        assertFalse(StudioInventoryActionPolicy.click(false, true, 10, 54,
                StudioClickType.LEFT, StudioInventoryAction.PICKUP).allowed());
        assertFalse(StudioInventoryActionPolicy.click(true, false, 10, 54,
                StudioClickType.LEFT, StudioInventoryAction.PICKUP).allowed());
    }

    @Test
    void rejectsShiftNumberDoubleOffhandCreativeAndCollectionActions() {
        for (StudioClickType click : Set.of(
                StudioClickType.SHIFT_LEFT,
                StudioClickType.SHIFT_RIGHT,
                StudioClickType.NUMBER_KEY,
                StudioClickType.DOUBLE_CLICK,
                StudioClickType.SWAP_OFFHAND,
                StudioClickType.CREATIVE)) {
            assertFalse(StudioInventoryActionPolicy.click(true, true, 10, 54,
                    click, StudioInventoryAction.PICKUP).allowed());
        }
        for (StudioInventoryAction action : Set.of(
                StudioInventoryAction.MOVE_TO_OTHER_INVENTORY,
                StudioInventoryAction.HOTBAR_SWAP,
                StudioInventoryAction.HOTBAR_MOVE_AND_READD,
                StudioInventoryAction.COLLECT_TO_CURSOR,
                StudioInventoryAction.SWAP_WITH_CURSOR,
                StudioInventoryAction.CLONE_STACK)) {
            assertFalse(StudioInventoryActionPolicy.click(true, true, 10, 54,
                    StudioClickType.LEFT, action).allowed());
        }
    }

    @Test
    void rejectsAllStudioDragsIncludingTopInventorySlots() {
        assertFalse(StudioInventoryActionPolicy.drag(true, true, 54, Set.of(1, 60)).allowed());
        assertFalse(StudioInventoryActionPolicy.drag(true, true, 54, Set.of(60, 61)).allowed());
    }
}
