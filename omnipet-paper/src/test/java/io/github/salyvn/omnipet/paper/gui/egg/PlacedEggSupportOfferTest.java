package io.github.salyvn.omnipet.paper.gui.egg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * The placed-egg menu decides what each hand's button says and does from this record alone, so every hand
 * state is pinned here rather than through a rendered inventory.
 *
 * <p>The distinction that matters is {@code effectMillis} against {@code creditedMillis}: the first is the
 * item's own figure and is what a player is shown, the second is what actually comes off the clock. Showing
 * the clamped figure would tell someone their accelerator is smaller than it is.
 */
class PlacedEggSupportOfferTest {
    private static final long HOUR = Duration.ofHours(1).toMillis();
    private static final long TEN_MINUTES = Duration.ofMinutes(10).toMillis();

    @Test
    void anEmptyHandOffersNothing() {
        PlacedEggSupportOffer offer = PlacedEggSupportOffer.empty(HOUR);

        assertFalse(offer.usable());
        assertFalse(offer.finishes());
        assertEquals(0, offer.creditedMillis());
        assertEquals(HOUR, offer.remainingAfter());
    }

    @Test
    void anOrdinaryItemOffersNothing() {
        PlacedEggSupportOffer offer = PlacedEggSupportOffer.unsupported(HOUR);

        assertFalse(offer.usable());
        assertEquals(0, offer.creditedMillis());
    }

    @Test
    void aReducerSmallerThanTheClockCreditsItsWholeEffect() {
        PlacedEggSupportOffer offer = PlacedEggSupportOffer.reducer(TEN_MINUTES, HOUR);

        assertTrue(offer.usable());
        assertFalse(offer.finishes());
        assertEquals(TEN_MINUTES, offer.creditedMillis());
        assertEquals(HOUR - TEN_MINUTES, offer.remainingAfter());
    }

    /** The player is shown the item's own hour, not the ten minutes that is all this egg has left. */
    @Test
    void aReducerLargerThanTheClockReportsItsOwnEffectButCreditsOnlyWhatIsLeft() {
        PlacedEggSupportOffer offer = PlacedEggSupportOffer.reducer(HOUR, TEN_MINUTES);

        assertEquals(HOUR, offer.effectMillis(), "the item's figure is what a player is shown");
        assertEquals(TEN_MINUTES, offer.creditedMillis(), "but only what is left comes off the clock");
        assertTrue(offer.finishes());
        assertEquals(0, offer.remainingAfter());
    }

    @Test
    void aReducerExactlyMatchingTheClockFinishesTheEgg() {
        PlacedEggSupportOffer offer = PlacedEggSupportOffer.reducer(HOUR, HOUR);

        assertTrue(offer.finishes());
        assertEquals(0, offer.remainingAfter());
    }

    @Test
    void anInstantHatchFinishesWhateverIsLeft() {
        PlacedEggSupportOffer offer = PlacedEggSupportOffer.instant(HOUR);

        assertTrue(offer.usable());
        assertTrue(offer.finishes());
        assertEquals(HOUR, offer.creditedMillis());
        assertEquals(0, offer.remainingAfter());
    }

    /** A ready egg is claimed by breaking it, so nothing may be spent on one. */
    @Test
    void nothingIsUsableAgainstAnEggThatIsAlreadyReady() {
        assertFalse(PlacedEggSupportOffer.instant(0).usable(),
                "an instant hatch on a ready egg would burn the item for nothing");
        assertFalse(PlacedEggSupportOffer.reducer(HOUR, 0).usable());
        assertFalse(PlacedEggSupportOffer.instant(0).finishes());
        assertEquals(0, PlacedEggSupportOffer.reducer(HOUR, 0).creditedMillis());
    }

    @Test
    void theKindAndTheEffectMustAgree() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlacedEggSupportOffer(PlacedEggSupportOffer.Kind.REDUCER, 0, HOUR),
                "a reducer worth nothing is a malformed item, not a free click");
        assertThrows(IllegalArgumentException.class,
                () -> new PlacedEggSupportOffer(PlacedEggSupportOffer.Kind.INSTANT, HOUR, HOUR),
                "an instant hatch carries no magnitude");
        assertThrows(IllegalArgumentException.class,
                () -> new PlacedEggSupportOffer(PlacedEggSupportOffer.Kind.EMPTY, HOUR, HOUR));
    }

    @Test
    void negativeTimeIsRejectedRatherThanClamped() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlacedEggSupportOffer(PlacedEggSupportOffer.Kind.EMPTY, 0, -1));
    }

    /** Time never runs backwards: no hand state can leave an egg with more time than it had. */
    @Test
    void noOfferEverIncreasesTheRemainingTime() {
        long[] clocks = {0, 1, TEN_MINUTES, HOUR, Long.MAX_VALUE / 4};
        for (long clock : clocks) {
            for (PlacedEggSupportOffer offer : offers(clock)) {
                assertTrue(offer.remainingAfter() <= clock,
                        offer.kind() + " at " + clock + " added time");
                assertTrue(offer.remainingAfter() >= 0,
                        offer.kind() + " at " + clock + " went below zero");
            }
        }
    }

    private static PlacedEggSupportOffer[] offers(long clock) {
        return new PlacedEggSupportOffer[] {
            PlacedEggSupportOffer.empty(clock),
            PlacedEggSupportOffer.unsupported(clock),
            PlacedEggSupportOffer.reducer(1, clock),
            PlacedEggSupportOffer.reducer(Long.MAX_VALUE, clock),
            PlacedEggSupportOffer.instant(clock),
        };
    }
}
