package io.github.salyvn.omnipet.paper.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.economy.EconomyProvider;
import io.github.salyvn.omnipet.core.economy.SlotPurchaseResult;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.studio.StatModifierType;

class DisplaysTest {
    @Test
    void anUnderscoredConstantBecomesOneReadableSentence() {
        assertEquals("Entitlement sync pending",
                Displays.of(SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING));
    }

    @Test
    void aSingleWordConstantIsCapitalisedNotShouted() {
        assertEquals("Vault", Displays.of(EconomyProvider.VAULT));
        assertEquals("Flat", Displays.of(StatModifierType.FLAT));
    }

    @Test
    void wordsKeepsLowerCaseForMidSentenceUse() {
        assertEquals("entitlement sync pending",
                Displays.words(SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING));
        assertEquals("vault", Displays.words(EconomyProvider.VAULT));
    }

    @Test
    void aSingleLetterTierStaysReadable() {
        assertEquals("D", Displays.of(PetTier.D));
    }

    @Test
    void rawIdentifiersAreSentenceCasedToo() {
        assertEquals("Pet dust", Displays.identifier("pet_dust"));
        assertEquals("Legendary", Displays.identifier("LEGENDARY"));
        assertEquals("Internal only", Displays.identifier("INTERNAL_ONLY"));
    }

    @Test
    void aBlankOrNullIdentifierYieldsEmptyRatherThanThrowing() {
        assertEquals("", Displays.identifier(null));
        assertEquals("", Displays.identifier("   "));
    }

    @Test
    void surroundingWhitespaceInAnIdentifierIsTrimmed() {
        assertEquals("Rare", Displays.identifier("  rare  "));
    }

    @Test
    void aNullEnumIsRejectedLoudlyBecauseItIsAProgrammingError() {
        assertThrows(NullPointerException.class, () -> Displays.of(null));
        assertThrows(NullPointerException.class, () -> Displays.words(null));
    }

    @Test
    void aRemainingWaitReadsTheWayAPlayerWouldSayIt() {
        assertEquals("7s", Displays.remaining(7_000));
        assertEquals("1m 20s", Displays.remaining(80_000));
        assertEquals("2m", Displays.remaining(120_000));
        assertEquals("1h 5m", Displays.remaining(3_900_000));
    }

    /**
     * A wait still in force must never read as zero.
     *
     * <p>Truncating 400ms to "0s" tells a player the skill is ready while the cast is still refused, which
     * reads as the skill being broken. Rounding up overstates by at most a fraction of a second, which
     * nobody can perceive.
     */
    @Test
    void aSubSecondWaitIsNeverReportedAsReady() {
        assertEquals("0.4s", Displays.remaining(400));
        assertEquals("0.1s", Displays.remaining(20));
        assertEquals("2s", Displays.remaining(1_001));
        assertEquals("0s", Displays.remaining(0));
    }
}
