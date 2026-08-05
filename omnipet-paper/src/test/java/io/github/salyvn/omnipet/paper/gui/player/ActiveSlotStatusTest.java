package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.economy.EconomyAmount;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;

/**
 * The vault's locked-slot tile decides what it says from this record alone, so the four states a player
 * can be in — can buy, cannot afford the permission, nothing left to buy, single-pet server — are pinned
 * here rather than through a rendered inventory.
 */
class ActiveSlotStatusTest {
    @Test
    void offersTheSlotAfterTheOneThePlayerHas() {
        ActiveSlotStatus status = ActiveSlotStatus.of(slots(5, Map.of(2, free(), 3, free())), 2, node -> true);

        assertEquals(3, status.nextSlot());
        assertEquals(2, status.unlocked());
        assertEquals(5, status.max());
        assertTrue(status.purchasable());
        assertFalse(status.exhausted());
    }

    @Test
    void reportsTheConfiguredPricesForThatSlot() {
        ActiveSlotStatus status = ActiveSlotStatus.of(
                slots(5, Map.of(2, priced("1500", 30))), 1, node -> true);

        // Asserted as plain text because that is what the lore prints: EconomyAmount strips trailing
        // zeros, so the stored value is 1.5E+3 and a player must never be shown that.
        assertEquals(2, status.costs().size());
        assertEquals("1500", status.costs().get(EconomyProvider.VAULT).value().toPlainString());
        assertEquals("30", status.costs().get(EconomyProvider.PLAYER_POINTS).value().toPlainString());
    }

    /** A priced slot behind a permission is visible but not clickable: the player can see what to earn. */
    @Test
    void aPricedSlotBehindAMissingPermissionIsBlockedRatherThanHidden() {
        ActiveSlotStatus status = ActiveSlotStatus.of(
                slots(5, Map.of(2, gated("omnipet.slot.purchase.2"))), 1, node -> false);

        assertEquals(2, status.nextSlot());
        assertFalse(status.purchasable());
        assertTrue(status.blockedByPermission());
        assertFalse(status.exhausted());
        assertFalse(status.costs().isEmpty(), "a blocked slot still shows what it would cost");
    }

    @Test
    void theSlotAfterTheLastPricedOneIsExhaustedEvenBelowTheCap() {
        ActiveSlotStatus status = ActiveSlotStatus.of(slots(5, Map.of(2, free())), 2, node -> true);

        assertTrue(status.exhausted(), "slot 3 has no unlocks entry, so nothing is for sale");
        assertEquals(0, status.nextSlot());
        assertTrue(status.costs().isEmpty());
        assertEquals(3, status.locked(), "the cap still allows three more than the player holds");
    }

    @Test
    void reachingTheCapExhaustsThePurchasePath() {
        ActiveSlotStatus status = ActiveSlotStatus.of(slots(2, Map.of(2, free())), 2, node -> true);

        assertTrue(status.exhausted());
        assertEquals(0, status.locked());
        assertFalse(status.blockedByPermission());
    }

    /** Single-pet servers pin everyone to one active pet, so a purchase would buy nothing visible. */
    @Test
    void singlePetServersOfferNothingRegardlessOfConfiguredPrices() {
        Phase4PaperConfig.ActiveSlots config = new Phase4PaperConfig.ActiveSlots(
                false, 1, 5, Phase4PaperConfig.Entitlement.omniPetDefault(), Map.of(2, free()));

        ActiveSlotStatus status = ActiveSlotStatus.of(config, 1, node -> true);

        assertFalse(status.multiPetEnabled());
        assertFalse(status.purchasable());
        assertTrue(status.exhausted());
        assertTrue(status.costs().isEmpty());
    }

    @Test
    void anAbsentConfigReportsTheCountAndOffersNothing() {
        ActiveSlotStatus status = ActiveSlotStatus.of(null, 3, node -> true);

        assertEquals(3, status.unlocked());
        assertEquals(3, status.max());
        assertEquals(0, status.locked());
        assertTrue(status.exhausted());
        assertFalse(status.purchasable());
    }

    /** A renderer must never be handed a negative count, so the factory floors rather than throwing. */
    @Test
    void aNegativeCountIsFlooredByTheFactoryAndRejectedByTheConstructor() {
        assertEquals(0, ActiveSlotStatus.unknown(-4).unlocked());
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveSlotStatus(-1, 5, 2, true, true, Map.of()));
    }

    @Test
    void aNullPermissionCheckTreatsEveryGateAsSatisfied() {
        ActiveSlotStatus status = ActiveSlotStatus.of(
                slots(5, Map.of(2, gated("omnipet.slot.purchase.2"))), 1, null);

        assertTrue(status.purchasable(), "no permission source means the gate cannot be evaluated");
    }

    private static Phase4PaperConfig.ActiveSlots slots(
            int max, Map<Integer, Phase4PaperConfig.SlotUnlock> unlocks) {
        return new Phase4PaperConfig.ActiveSlots(
                true, 1, max, Phase4PaperConfig.Entitlement.omniPetDefault(), unlocks);
    }

    private static Phase4PaperConfig.SlotUnlock free() {
        return priced("1000", 10);
    }

    private static Phase4PaperConfig.SlotUnlock gated(String permission) {
        return new Phase4PaperConfig.SlotUnlock(permission, costs("1000", 10));
    }

    private static Phase4PaperConfig.SlotUnlock priced(String vault, long points) {
        return new Phase4PaperConfig.SlotUnlock("", costs(vault, points));
    }

    private static Map<EconomyProvider, EconomyAmount> costs(String vault, long points) {
        return Map.of(
                EconomyProvider.VAULT, EconomyAmount.vault(new BigDecimal(vault)),
                EconomyProvider.PLAYER_POINTS, EconomyAmount.playerPoints(points));
    }
}
