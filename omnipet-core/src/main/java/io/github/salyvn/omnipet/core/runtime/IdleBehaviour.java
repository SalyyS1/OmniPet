package io.github.salyvn.omnipet.core.runtime;

import java.util.UUID;

/**
 * What a pet does when nothing is asking it to move.
 *
 * <p>Idle is the only moment a companion acts without input, so it is the only place personality can
 * appear. It is also where a companion most easily reads as a prop: a pet that stands perfectly still
 * looks switched off, and one that loops a fixed animation looks mechanical. The fix for the second is
 * randomness in the selection, not more animation.
 *
 * <p>Deterministic on purpose. The pet's own UUID seeds both its temperament and its one-shot choices, so
 * the same pet behaves consistently across restarts and a test can reproduce a sequence exactly.
 */
public final class IdleBehaviour {
    /** Below this owner speed, in metres per second, the owner counts as standing still. */
    public static final double OWNER_STILL_SPEED = 0.05;

    /** How long an owner must stand still before a pet settles down, in seconds. */
    public static final double SETTLE_SECONDS = 30;

    /** Shortest and longest gap between two idle one-shots, in seconds. */
    public static final double MINIMUM_ONE_SHOT_GAP = 6;
    public static final double MAXIMUM_ONE_SHOT_GAP = 18;

    private IdleBehaviour() {}

    /** What a pet is doing while nobody is steering it. */
    public enum State {
        /** Following, orbiting, or otherwise being driven by the steering controller. */
        ACTIVE,
        /** Owner has stopped but the pet has not settled yet: alert, watching them. */
        ATTENTIVE,
        /** Owner has been still long enough that the pet has curled up. */
        RESTING
    }

    /** An idle flourish. Which one a pet picks is weighted by its temperament. */
    public enum OneShot { LOOK_AROUND, SHAKE, HOP, SNIFF, STRETCH }

    /**
     * Everything a renderer needs to know about a pet that nothing is steering.
     *
     * <p>Grouped rather than added to {@link RuntimeTransform} one field at a time: the transform is on a
     * public port with four implementors, and idle gained two values in one go.
     *
     * @param state    posture, which selects a looping clip
     * @param flourish the one-shot running this instant, or null between flourishes
     */
    public record Pose(State state, OneShot flourish) {
        public static final Pose ACTIVE = new Pose(State.ACTIVE, null);

        public Pose {
            state = state == null ? State.ACTIVE : state;
            // A flourish only means anything while the pet is idle; steering overrides it.
            if (state == State.ACTIVE) flourish = null;
        }

        /** Whether the pet has settled, which is a different animation from merely being slow. */
        public boolean resting() {
            return state == State.RESTING;
        }
    }

    /**
     * The idle state for one sample.
     *
     * @param ownerSpeed         owner's speed in metres per second
     * @param ownerStillSeconds  how long the owner has been below {@link #OWNER_STILL_SPEED}
     * @param settleSeconds      this pet's own settle threshold; a lazy pet settles sooner
     */
    public static State state(double ownerSpeed, double ownerStillSeconds, double settleSeconds) {
        if (!Double.isFinite(ownerSpeed) || ownerSpeed >= OWNER_STILL_SPEED) return State.ACTIVE;
        if (!Double.isFinite(ownerStillSeconds) || ownerStillSeconds < 0) return State.ATTENTIVE;
        double threshold = Double.isFinite(settleSeconds) && settleSeconds > 0
                ? settleSeconds
                : SETTLE_SECONDS;
        return ownerStillSeconds >= threshold ? State.RESTING : State.ATTENTIVE;
    }

    /**
     * A pet's fixed temperament, rolled from its instance ID.
     *
     * <p>Rolled rather than authored so two pets of the same definition are not the same creature, which
     * is the cheapest way to make a shared model feel individual. Each value is 0..1.
     *
     * @param playfulness how often it performs a one-shot at all
     * @param curiosity   how much it prefers looking around over resting in place
     * @param laziness    how readily it settles when the owner stops
     */
    public record Temperament(double playfulness, double curiosity, double laziness) {
        public Temperament {
            playfulness = clamp(playfulness);
            curiosity = clamp(curiosity);
            laziness = clamp(laziness);
        }

        /** Seconds this pet waits before settling. A lazy pet gives up on the walk sooner. */
        public double settleSeconds() {
            // Half to full the shared threshold, so laziness shortens the wait without eliminating it.
            return SETTLE_SECONDS * (1.0 - 0.5 * laziness);
        }

        /** Seconds between this pet's one-shots. A playful pet fidgets more often. */
        public double oneShotGapSeconds() {
            return MAXIMUM_ONE_SHOT_GAP - (MAXIMUM_ONE_SHOT_GAP - MINIMUM_ONE_SHOT_GAP) * playfulness;
        }

        private static double clamp(double value) {
            return !Double.isFinite(value) ? 0.5 : Math.max(0, Math.min(1, value));
        }
    }

    /**
     * The temperament for a pet, derived from its instance ID.
     *
     * <p>Three independent draws from one seed rather than three hashes of the same bits, so a pet cannot
     * come out uniformly lazy-and-playful-and-curious just because its UUID happened to hash high.
     */
    public static Temperament temperament(UUID petInstanceId) {
        if (petInstanceId == null) return new Temperament(0.5, 0.5, 0.5);
        long seed = petInstanceId.getMostSignificantBits() ^ petInstanceId.getLeastSignificantBits();
        return new Temperament(draw(seed, 1), draw(seed, 2), draw(seed, 3));
    }

    /**
     * Which flourish a pet performs on its {@code sequence}-th one-shot.
     *
     * <p>Seeded by the pet and the sequence number, so the choice varies between consecutive flourishes
     * and between pets, but replays identically for a given pet. A fixed cycle is spotted almost
     * immediately -- a pixel-cat looping swish, swish, yawn reads as a machine on the second loop.
     */
    public static OneShot oneShot(UUID petInstanceId, long sequence, Temperament temperament) {
        OneShot[] all = OneShot.values();
        if (petInstanceId == null) return all[0];
        long seed = petInstanceId.getMostSignificantBits()
                ^ Long.rotateLeft(petInstanceId.getLeastSignificantBits(), 23)
                ^ (sequence * 0x9E3779B97F4A7C15L);
        double roll = draw(seed, 7);
        // A curious pet looks around more; the rest are spread evenly over what is left.
        double curiosity = temperament == null ? 0.5 : temperament.curiosity();
        if (roll < 0.2 + 0.4 * curiosity) return OneShot.LOOK_AROUND;
        int remaining = all.length - 1;
        int index = 1 + (int) (draw(seed, 11) * remaining);
        return all[Math.min(index, all.length - 1)];
    }

    /** A stable 0..1 draw from a seed and a stream number, using splitmix64's mixing function. */
    private static double draw(long seed, int stream) {
        long mixed = seed + stream * 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }

    /**
     * How long to wait before the {@code sequence}-th flourish.
     *
     * <p>Jittered around the pet's own gap rather than fired on a fixed period. Two pets side by side on a
     * clean multiple of the gap would flourish in unison, which reads as a mechanism rather than as two
     * creatures -- the same reason the render loop carries a per-pet phase offset.
     *
     * @return seconds to wait, never below {@link #MINIMUM_ONE_SHOT_GAP} / 2
     */
    public static double oneShotDelaySeconds(UUID petInstanceId, long sequence, Temperament temperament) {
        double base = temperament == null
                ? (MINIMUM_ONE_SHOT_GAP + MAXIMUM_ONE_SHOT_GAP) / 2
                : temperament.oneShotGapSeconds();
        if (petInstanceId == null) return base;
        long seed = petInstanceId.getMostSignificantBits()
                ^ Long.rotateLeft(petInstanceId.getLeastSignificantBits(), 41)
                ^ (sequence * 0xD1342543DE82EF95L);
        // Half to one-and-a-half the gap: enough spread that neighbours drift apart within a few rounds.
        double jittered = base * (0.5 + draw(seed, 13));
        return Math.max(MINIMUM_ONE_SHOT_GAP / 2, jittered);
    }

    /** How long one flourish runs. Short: it is punctuation between idle loops, not a state. */
    public static final double ONE_SHOT_SECONDS = 1.5;

    /** How far the play orbit breathes in and out, as a fraction of its radius. */
    public static final double PLAY_DART_FRACTION = 0.45;

    /** How fast the orbit darts out and back, in radians per second — slower than the orbit itself. */
    public static final double PLAY_DART_RADIANS_PER_SECOND = 0.9;

    /**
     * Where a playing pet wants to be this instant, or null when nothing is playing.
     *
     * <p>This is the offset that makes idle read as a companion rather than a prop. A head pet is an item
     * display with no animation clips, so the flourish enum never moved it — the only thing that moves any
     * renderer is its target position, and this is that target. It circles the owner, breathes in and out
     * as if darting round them, and hops, all layered on the same orbit and bob numbers the steering
     * profile already carries so nothing new has to be tuned.
     *
     * <p>Purely a function of the phase, like every other idle value, so two ticks with the same phase
     * agree and a test can reproduce a point on the path exactly.
     *
     * @param owner       the owner's position, which the pet plays around
     * @param forward     owner-forward, already horizontal and normalised by the caller
     * @param side        the owner's right, perpendicular to {@code forward}
     * @param phase       seconds plus this pet's phase offset, so neighbours are not in lockstep
     * @param state       {@link State#ACTIVE} yields null; a resting pet plays more gently than an attentive one
     * @param temperament a playful pet ranges wider; may be null, which reads as middling
     * @return the world-space target, or null when the pet is being steered and should follow instead
     */
    public static RuntimeVector playTarget(
            RuntimeVector owner,
            RuntimeVector forward,
            RuntimeVector side,
            double phase,
            State state,
            Temperament temperament,
            MovementProfile profile) {
        if (state == null || state == State.ACTIVE) return null;
        if (owner == null || forward == null || side == null || profile == null) return null;
        if (!Double.isFinite(phase)) return null;
        double playfulness = temperament == null ? 0.5 : temperament.playfulness();
        // A resting pet still circles, but lower and closer; an attentive one plays at full range.
        double stateEnergy = state == State.RESTING ? 0.4 : 1.0;
        double energy = stateEnergy * (0.5 + 0.5 * playfulness);
        double orbitAngle = phase * profile.orbitRadiansPerSecond();
        double dart = 1 + PLAY_DART_FRACTION * Math.sin(phase * PLAY_DART_RADIANS_PER_SECOND);
        // A visible orbit even for a calm pet, widening with energy and breathing with the dart.
        double radius = profile.orbitRadius() * (0.6 + 0.4 * energy) * dart;
        double hop = profile.bobAmplitude() * (1 + energy) * Math.abs(Math.sin(phase * profile.bobRadiansPerSecond()));
        return owner
                .add(side.multiply(Math.cos(orbitAngle) * radius))
                .add(forward.multiply(Math.sin(orbitAngle) * radius))
                .add(new RuntimeVector(0, profile.heightOffset() + hop, 0));
    }

    /**
     * The yaw a pet shows while idle: turned towards its owner.
     *
     * <p>Motion normally decides facing, but an idle pet has no motion to read, so it used to keep
     * whatever heading it stopped on and end up staring away from the player it belongs to. Rate-limited
     * through the same turn allowance as moving, so looking over is a turn rather than a snap.
     *
     * @param currentYaw   the heading it shows now, in degrees
     * @param toOwner      pet-to-owner offset in world space; a near-zero offset holds the heading
     * @param deltaSeconds time since the previous update
     */
    public static float faceOwner(float currentYaw, RuntimeVector toOwner, double deltaSeconds) {
        if (toOwner == null) return MovementFacing.normalize(currentYaw);
        double horizontal = Math.sqrt(toOwner.x() * toOwner.x() + toOwner.z() * toOwner.z());
        // Standing on top of the owner gives a direction that is mostly noise; hold rather than spin.
        if (horizontal < 0.2 || !(deltaSeconds > 0) || !Double.isFinite(deltaSeconds)) {
            return MovementFacing.normalize(currentYaw);
        }
        double desired = Math.toDegrees(Math.atan2(-toOwner.x(), toOwner.z()));
        double difference = MovementFacing.normalize((float) (desired - currentYaw));
        double allowance = MovementFacing.TURN_DEGREES_PER_SECOND * deltaSeconds;
        double step = Math.max(-allowance, Math.min(allowance, difference));
        return MovementFacing.normalize((float) (currentYaw + step));
    }
}
