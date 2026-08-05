package io.github.salyvn.omnipet.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * What a pet does when nothing is steering it.
 *
 * <p>Two properties carry this feature. It has to be <em>deterministic</em>, so a pet is the same creature
 * after a restart and a test can reproduce a sequence. And it has to be <em>varied</em>, because a fixed
 * loop is spotted on the second repetition and reads as a machine.
 */
class IdleBehaviourTest {
    private static final UUID PET = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER_PET = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

    @Test
    void aMovingOwnerLeavesThePetActive() {
        assertEquals(IdleBehaviour.State.ACTIVE, IdleBehaviour.state(4.3, 0, 30));
        // Still counted as walking right at the threshold: the boundary belongs to moving, so a pet does
        // not flicker between states while its owner creeps.
        assertEquals(IdleBehaviour.State.ACTIVE,
                IdleBehaviour.state(IdleBehaviour.OWNER_STILL_SPEED, 100, 30));
    }

    @Test
    void aPetWatchesBeforeItSettles() {
        // The intermediate state is the point: a pet that dropped straight from walking to curled up would
        // look like it had been switched off rather than like it had decided to rest.
        assertEquals(IdleBehaviour.State.ATTENTIVE, IdleBehaviour.state(0, 0, 30));
        assertEquals(IdleBehaviour.State.ATTENTIVE, IdleBehaviour.state(0, 29.9, 30));
        assertEquals(IdleBehaviour.State.RESTING, IdleBehaviour.state(0, 30, 30));
        assertEquals(IdleBehaviour.State.RESTING, IdleBehaviour.state(0, 600, 30));
    }

    @Test
    void aLazyPetSettlesSoonerThanAnEagerOne() {
        double lazy = new IdleBehaviour.Temperament(0.5, 0.5, 1.0).settleSeconds();
        double eager = new IdleBehaviour.Temperament(0.5, 0.5, 0.0).settleSeconds();

        assertTrue(lazy < eager, "laziness has to shorten the wait: " + lazy + " vs " + eager);
        assertTrue(lazy > 0, "no temperament may settle instantly");
    }

    @Test
    void aPlayfulPetFidgetsMoreOften() {
        double playful = new IdleBehaviour.Temperament(1.0, 0.5, 0.5).oneShotGapSeconds();
        double placid = new IdleBehaviour.Temperament(0.0, 0.5, 0.5).oneShotGapSeconds();

        assertTrue(playful < placid, "playfulness has to shorten the gap: " + playful + " vs " + placid);
        assertTrue(playful >= IdleBehaviour.MINIMUM_ONE_SHOT_GAP,
                "even the most playful pet needs a floor, or it never stops moving");
    }

    @Test
    void theSamePetIsTheSameCreatureAcrossRestarts() {
        // Rolled from the instance ID rather than stored, so nothing needs migrating and a restart cannot
        // silently change a pet's character.
        assertEquals(IdleBehaviour.temperament(PET), IdleBehaviour.temperament(PET));
        assertEquals(
                IdleBehaviour.oneShot(PET, 7, IdleBehaviour.temperament(PET)),
                IdleBehaviour.oneShot(PET, 7, IdleBehaviour.temperament(PET)));
    }

    @Test
    void twoPetsOfTheSameDefinitionAreNotTheSameCreature() {
        Set<IdleBehaviour.Temperament> seen = new HashSet<>();
        for (int index = 0; index < 200; index++) seen.add(IdleBehaviour.temperament(UUID.randomUUID()));

        assertTrue(seen.size() > 150,
                "temperament has to actually vary between pets, saw " + seen.size() + " of 200");
    }

    @Test
    void temperamentTraitsAreIndependentRatherThanMovingTogether() {
        // Three draws from one seed, not three views of the same bits. If they moved together, a pet whose
        // UUID hashed high would be uniformly lazy and playful and curious, which is one creature repeated.
        int bothHigh = 0;
        int lazyHighPlayfulLow = 0;
        for (int index = 0; index < 400; index++) {
            IdleBehaviour.Temperament rolled = IdleBehaviour.temperament(UUID.randomUUID());
            if (rolled.laziness() > 0.6 && rolled.playfulness() > 0.6) bothHigh++;
            if (rolled.laziness() > 0.6 && rolled.playfulness() < 0.4) lazyHighPlayfulLow++;
        }

        assertTrue(lazyHighPlayfulLow > 20,
                "a lazy pet must be able to be unplayful; saw " + lazyHighPlayfulLow);
        assertTrue(bothHigh > 20, "and it must be able to be playful too; saw " + bothHigh);
    }

    @Test
    void consecutiveOneShotsDoNotFollowAFixedCycle() {
        // The failure this guards against: swish, swish, yawn, repeat. Spotted on the second loop.
        Map<IdleBehaviour.OneShot, Integer> counts = new EnumMap<>(IdleBehaviour.OneShot.class);
        IdleBehaviour.Temperament temperament = IdleBehaviour.temperament(PET);
        IdleBehaviour.OneShot[] sequence = new IdleBehaviour.OneShot[24];
        for (int index = 0; index < sequence.length; index++) {
            sequence[index] = IdleBehaviour.oneShot(PET, index, temperament);
            counts.merge(sequence[index], 1, Integer::sum);
        }

        assertTrue(counts.size() >= 3,
                "a pet that only ever does one or two things reads as broken: " + counts);
        // No period-2 or period-3 loop across the whole run.
        for (int period : new int[] {2, 3}) {
            boolean cyclic = true;
            for (int index = period; index < sequence.length && cyclic; index++) {
                if (sequence[index] != sequence[index - period]) cyclic = false;
            }
            assertTrue(!cyclic, "the sequence repeats every " + period + " flourishes");
        }
    }

    @Test
    void aCuriousPetLooksAroundMoreThanAnIncuriousOne() {
        assertTrue(lookArounds(1.0) > lookArounds(0.0),
                "curiosity has to change what the pet chooses to do");
    }

    @Test
    void neighbouringPetsDoNotFlourishInUnison() {
        // A fixed period would have two pets side by side fidgeting on the same beat, which reads as one
        // mechanism driving both rather than as two creatures.
        IdleBehaviour.Temperament temperament = IdleBehaviour.temperament(PET);
        Set<Double> delays = new HashSet<>();
        for (long sequence = 0; sequence < 12; sequence++) {
            delays.add(IdleBehaviour.oneShotDelaySeconds(PET, sequence, temperament));
        }

        assertTrue(delays.size() >= 10, "consecutive gaps have to vary, saw " + delays.size() + " of 12");
        for (double delay : delays) {
            assertTrue(delay >= IdleBehaviour.MINIMUM_ONE_SHOT_GAP / 2,
                    "a gap must never collapse to a stutter: " + delay);
        }
        // Deterministic all the same, so a restart does not reshuffle a pet's rhythm.
        assertEquals(
                IdleBehaviour.oneShotDelaySeconds(PET, 3, temperament),
                IdleBehaviour.oneShotDelaySeconds(PET, 3, temperament));
        assertNotEquals(
                IdleBehaviour.oneShotDelaySeconds(PET, 3, temperament),
                IdleBehaviour.oneShotDelaySeconds(OTHER_PET, 3, temperament));
    }

    @Test
    void anIdlePetTurnsToFaceItsOwner() {
        // Facing normally comes from velocity, and an idle pet has none -- so without this it keeps
        // whatever heading it stopped on and stares past the player it belongs to.
        float facingAway = 180;
        RuntimeVector ownerIsNorth = new RuntimeVector(0, 0, 5);

        float turned = IdleBehaviour.faceOwner(facingAway, ownerIsNorth, 1.0);

        assertTrue(Math.abs(turned) < Math.abs(facingAway),
                "the pet has to turn towards the owner, went from " + facingAway + " to " + turned);
        // A full second at the shared turn rate is enough to complete this turn, so it arrives.
        assertEquals(0, turned, 0.001);
    }

    @Test
    void turningToFaceIsARotationRatherThanASnap() {
        // One tick, not one second: a pet that swung a half-turn in 50ms reads as a teleport.
        float afterOneTick = IdleBehaviour.faceOwner(180, new RuntimeVector(0, 0, 5), 0.05);

        assertTrue(Math.abs(afterOneTick) > 90,
                "one tick may not complete a half turn, got " + afterOneTick);
    }

    @Test
    void aPetStandingOnItsOwnerHoldsItsHeadingRatherThanSpinning() {
        // The direction to an owner you are standing inside is mostly noise; following it would jitter.
        assertEquals(42, IdleBehaviour.faceOwner(42, new RuntimeVector(0.01, 0, 0.01), 1.0), 0.001);
        assertEquals(42, IdleBehaviour.faceOwner(42, null, 1.0), 0.001);
        assertEquals(42, IdleBehaviour.faceOwner(42, new RuntimeVector(0, 0, 5), 0), 0.001);
    }

    @Test
    void aFlourishOnlyMeansSomethingWhileThePetIsIdle() {
        // Steering wins: a flourish that survived the owner walking off would play while the pet ran.
        assertEquals(null,
                new IdleBehaviour.Pose(IdleBehaviour.State.ACTIVE, IdleBehaviour.OneShot.HOP).flourish());
        assertEquals(IdleBehaviour.OneShot.HOP,
                new IdleBehaviour.Pose(IdleBehaviour.State.RESTING, IdleBehaviour.OneShot.HOP).flourish());

        assertTrue(new IdleBehaviour.Pose(IdleBehaviour.State.RESTING, null).resting());
        assertTrue(!new IdleBehaviour.Pose(IdleBehaviour.State.ATTENTIVE, null).resting());
        assertEquals(IdleBehaviour.State.ACTIVE, new IdleBehaviour.Pose(null, null).state());
    }

    @Test
    void unusableInputDegradesToSomethingSafe() {
        assertEquals(IdleBehaviour.State.ACTIVE, IdleBehaviour.state(Double.NaN, 100, 30));
        assertEquals(IdleBehaviour.State.ATTENTIVE, IdleBehaviour.state(0, Double.NaN, 30));
        // A non-positive threshold falls back to the shared one rather than settling instantly.
        assertEquals(IdleBehaviour.State.ATTENTIVE, IdleBehaviour.state(0, 1, 0));

        IdleBehaviour.Temperament fallback = IdleBehaviour.temperament(null);
        assertEquals(0.5, fallback.playfulness());
        assertNotEquals(null, IdleBehaviour.oneShot(null, 0, fallback));

        IdleBehaviour.Temperament broken = new IdleBehaviour.Temperament(Double.NaN, 5, -3);
        assertEquals(0.5, broken.playfulness());
        assertEquals(1.0, broken.curiosity(), "out of range clamps rather than throwing at a click");
        assertEquals(0.0, broken.laziness());
    }

    private static int lookArounds(double curiosity) {
        IdleBehaviour.Temperament temperament = new IdleBehaviour.Temperament(0.5, curiosity, 0.5);
        int count = 0;
        for (int index = 0; index < 300; index++) {
            if (IdleBehaviour.oneShot(PET, index, temperament) == IdleBehaviour.OneShot.LOOK_AROUND) {
                count++;
            }
        }
        return count;
    }
}
