package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The anticipation curve for a hatching egg.
 *
 * <p>The shape is the point, not the constants. A linear ramp reads as a mechanism winding up; what makes
 * a hatch feel imminent is nothing happening for most of the wait and then something unmistakable. These
 * tests pin that shape so a later tweak to the numbers cannot flatten it back out.
 */
class EggShakeTest {
    private static final long TOTAL = 60_000L;

    @Test
    void aFreshlyPlacedEggIsCompletelyStill() {
        // Most of a wait should be uneventful, or the effect stops meaning "soon".
        assertEquals(0, EggShake.intensity(TOTAL, TOTAL));
        assertEquals(0, EggShake.degrees(TOTAL, TOTAL, 1.0));
    }

    @Test
    void nothingHappensUntilTheFinalQuarter() {
        assertEquals(0, EggShake.intensity((long) (TOTAL * 0.5), TOTAL));
        assertEquals(0, EggShake.intensity((long) (TOTAL * 0.26), TOTAL));
        assertTrue(EggShake.intensity((long) (TOTAL * 0.20), TOTAL) > 0,
                "inside the final quarter the egg has to start moving");
    }

    @Test
    void intensityRisesAsTimeRunsOutAndPeaksAtHatching() {
        double early = EggShake.intensity((long) (TOTAL * 0.20), TOTAL);
        double middle = EggShake.intensity((long) (TOTAL * 0.10), TOTAL);
        double late = EggShake.intensity((long) (TOTAL * 0.02), TOTAL);

        assertTrue(early < middle, "intensity must be monotonic: " + early + " then " + middle);
        assertTrue(middle < late, "intensity must be monotonic: " + middle + " then " + late);
        assertEquals(1, EggShake.intensity(0, TOTAL), "a ready egg is at full intensity");
    }

    @Test
    void theFinalMomentsAccelerateRatherThanRampEvenly() {
        // Squared, so the second half of the final quarter gains far more than the first half. A linear
        // ramp would make the whole quarter feel the same.
        double firstHalfGain = EggShake.intensity((long) (TOTAL * 0.125), TOTAL)
                - EggShake.intensity((long) (TOTAL * 0.25), TOTAL);
        double secondHalfGain = EggShake.intensity(0, TOTAL)
                - EggShake.intensity((long) (TOTAL * 0.125), TOTAL);

        assertTrue(secondHalfGain > firstHalfGain,
                "the curve must accelerate: " + firstHalfGain + " then " + secondHalfGain);
    }

    @Test
    void theEggRocksBothWaysRatherThanLeaningOneWay() {
        // A sine, so it returns through zero. A one-sided offset would read as a broken model, not a rock.
        long nearlyReady = (long) (TOTAL * 0.01);
        boolean sawPositive = false;
        boolean sawNegative = false;
        for (int step = 0; step < 200; step++) {
            double degrees = EggShake.degrees(nearlyReady, TOTAL, step * 0.01);
            if (degrees > 0.5) sawPositive = true;
            if (degrees < -0.5) sawNegative = true;
        }

        assertTrue(sawPositive && sawNegative, "rocking has to go both ways");
    }

    @Test
    void theAngleStaysWithinTheDeclaredLimit() {
        for (int step = 0; step < 500; step++) {
            double degrees = EggShake.degrees(0, TOTAL, step * 0.017);
            assertTrue(Math.abs(degrees) <= EggShake.MAXIMUM_DEGREES + 1.0e-9,
                    "rocked " + degrees + " degrees, past the limit");
        }
    }

    @Test
    void aBurstIsNarrowerThanTheShakeSoItStaysAnEvent() {
        // Something that happens throughout the final quarter is ambient, not an event.
        assertFalse(EggShake.imminent((long) (TOTAL * 0.20), TOTAL));
        assertTrue(EggShake.imminent((long) (TOTAL * 0.05), TOTAL));
        assertTrue(EggShake.imminent(0, TOTAL));
    }

    @Test
    void unusableInputProducesStillnessRatherThanNonsense() {
        assertEquals(0, EggShake.intensity(TOTAL, 0), "a zero-length incubation cannot have a curve");
        assertEquals(0, EggShake.degrees(0, TOTAL, Double.NaN));
        assertEquals(0, EggShake.degrees(0, TOTAL, Double.POSITIVE_INFINITY));
    }
}
