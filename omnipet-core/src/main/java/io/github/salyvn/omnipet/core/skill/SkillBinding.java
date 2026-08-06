package io.github.salyvn.omnipet.core.skill;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import io.github.salyvn.omnipet.core.domain.StableId;

/**
 * One skill a pet can cast, and what makes it happen.
 *
 * @param trigger what causes the cast
 * @param cooldown the wait between casts of this binding
 * @param chance the odds of firing, in [0,1], rolled after every other gate
 * @param staminaCost stamina spent on a successful cast
 * @param targetPolicy what the skill is aimed at
 * @param persistCooldown whether the cooldown survives a restart
 * @param healthThreshold for {@link SkillTrigger#ON_LOW_HEALTH}: the fraction of maximum health at or below
 *     which it fires, in (0,1]
 * @param targetRange how far the target resolver may look, in blocks
 * @param interval for {@link SkillTrigger#INTERVAL}: the period between casts
 * @param power the provider's power multiplier, scaling the skill's own damage and duration numbers
 */
public record SkillBinding(
        String bindingId,
        String provider,
        String skillId,
        SkillTrigger trigger,
        Duration cooldown,
        double chance,
        double staminaCost,
        SkillTargetPolicy targetPolicy,
        boolean persistCooldown,
        double healthThreshold,
        double targetRange,
        Duration interval,
        double power) {

    /** How far a target may be when a definition does not say. Vanilla melee reach is 3; this is generous. */
    public static final double DEFAULT_TARGET_RANGE = 20;
    /** The health fraction a low-health trigger fires at when a definition does not say. */
    public static final double DEFAULT_HEALTH_THRESHOLD = 0.3;
    /** The period an interval trigger uses when a definition does not say. */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(10);

    public SkillBinding {
        bindingId = StableId.requireValid(bindingId);
        provider = require(provider, "skill provider").toUpperCase(Locale.ROOT);
        skillId = require(skillId, "skill ID");
        trigger = Objects.requireNonNull(trigger, "skill trigger");
        cooldown = cooldown == null ? Duration.ZERO : cooldown;
        if (cooldown.isNegative()) throw new IllegalArgumentException("skill cooldown cannot be negative");
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("skill chance must be in [0,1]");
        }
        if (!Double.isFinite(staminaCost) || staminaCost < 0) {
            throw new IllegalArgumentException("skill stamina cost must be finite and non-negative");
        }
        targetPolicy = Objects.requireNonNull(targetPolicy, "skill target policy");
        // Zero would mean "fires when dead", which is ON_DEATH, and above one would mean "always", which is
        // a trigger with no threshold. Both are better said another way, so both are refused.
        if (!Double.isFinite(healthThreshold) || healthThreshold <= 0 || healthThreshold > 1) {
            throw new IllegalArgumentException("skill health threshold must be in (0,1]");
        }
        if (!Double.isFinite(targetRange) || targetRange <= 0 || targetRange > 64) {
            throw new IllegalArgumentException("skill target range must be in (0,64]");
        }
        interval = interval == null ? DEFAULT_INTERVAL : interval;
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("skill interval must be positive");
        }
        if (!Double.isFinite(power) || power <= 0) {
            throw new IllegalArgumentException("skill power must be positive and finite");
        }
    }

    /**
     * A binding with the per-trigger tuning left at its defaults.
     *
     * <p>Kept so the call sites that predate that tuning keep meaning what they meant, and so a test about
     * cooldowns does not have to state a health threshold it has no opinion about.
     */
    public SkillBinding(
            String bindingId,
            String provider,
            String skillId,
            SkillTrigger trigger,
            Duration cooldown,
            double chance,
            double staminaCost,
            SkillTargetPolicy targetPolicy,
            boolean persistCooldown) {
        this(bindingId, provider, skillId, trigger, cooldown, chance, staminaCost, targetPolicy,
                persistCooldown, DEFAULT_HEALTH_THRESHOLD, DEFAULT_TARGET_RANGE, DEFAULT_INTERVAL, 1);
    }

    /** Whether a refused cast of this binding should tell the player why. */
    public boolean announces() {
        return trigger.announces();
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
