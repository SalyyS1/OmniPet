package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.economy.EconomyAmount;

class SlotPurchaseInventoryHolderTest {
    @Test
    void retainsDisplayedAmountAndTransactionIdentity() {
        UUID transactionId = UUID.randomUUID();
        Map<Integer, SlotPurchaseInventoryHolder.Action> actions = new HashMap<>();
        var holder = new SlotPurchaseInventoryHolder(
                UUID.randomUUID(), 7, 2, 3, transactionId,
                SlotPurchaseInventoryHolder.Stage.CONFIRM, actions);
        actions.put(11, SlotPurchaseInventoryHolder.Action.confirm(EconomyAmount.playerPoints(50)));

        assertEquals(transactionId, holder.transactionId());
        assertEquals(50, holder.action(11).amount().playerPointsValue());
        assertThrows(IllegalArgumentException.class,
                () -> new SlotPurchaseInventoryHolder.Action(
                        SlotPurchaseInventoryHolder.Type.CONFIRM, null));
    }
}
