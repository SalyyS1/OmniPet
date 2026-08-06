package io.github.salyvn.omnipet.core.progression;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * How much experience a pet earns when its owner kills something.
 *
 * <p>Pure and platform-free, so the rule can be read and tested without a server. The listener supplies the
 * mob's identity; this decides the number.
 *
 * <p>Three sources, in falling priority: an entry naming this exact mob, the default, and — when neither
 * applies — nothing at all. A zero is a legitimate answer and means the kill grants no experience, which is
 * how an operator excludes a mob without deleting the feature.
 *
 * @param defaultExperience what an unlisted mob is worth
 * @param perMob overrides keyed by mob ID, matched without regard to case
 * @param share how a kill is divided when several pets are out
 */
public record KillExperienceRules(
        double defaultExperience,
        Map<String, Double> perMob,
        Share share) {

    /** What happens when an owner has more than one pet out. */
    public enum Share {
        /**
         * Every active pet earns the full amount.
         *
         * <p>The default because the alternative punishes a player for using slots they paid for: a second
         * pet would halve the first one's growth, so the reward for unlocking a slot is slower progress.
         */
        EACH,
        /** The amount is divided evenly, so total experience per kill does not depend on slot count. */
        SPLIT
    }

    public KillExperienceRules {
        if (!Double.isFinite(defaultExperience) || defaultExperience < 0) {
            throw new IllegalArgumentException("kill experience default must be zero or more and finite");
        }
        Objects.requireNonNull(share, "kill experience share");
        perMob = normalise(perMob);
    }

    /** The rule set that grants nothing, for a server with the feature switched off. */
    public static KillExperienceRules disabled() {
        return new KillExperienceRules(0, Map.of(), Share.EACH);
    }

    /** Whether any kill could ever grant experience, so a listener can skip work entirely. */
    public boolean enabled() {
        return defaultExperience > 0 || perMob.values().stream().anyMatch(amount -> amount > 0);
    }

    /**
     * What one kill is worth to one pet.
     *
     * @param mobId the vanilla entity type or the MythicMobs internal name
     * @param activePets how many pets the owner has out, which only matters when splitting
     * @return the experience for a single pet, never negative
     */
    public double experienceFor(String mobId, int activePets) {
        if (activePets <= 0) return 0;
        double total = perMob.getOrDefault(key(mobId), defaultExperience);
        if (total <= 0) return 0;
        return share == Share.SPLIT ? total / activePets : total;
    }

    /** Whether this mob has an entry of its own, for a diagnostic that explains a surprising number. */
    public boolean names(String mobId) {
        return perMob.containsKey(key(mobId));
    }

    private static Map<String, Double> normalise(Map<String, Double> perMob) {
        if (perMob == null || perMob.isEmpty()) return Map.of();
        Map<String, Double> folded = new java.util.LinkedHashMap<>();
        perMob.forEach((mob, amount) -> {
            if (mob == null || mob.isBlank()) throw new IllegalArgumentException("kill experience mob ID is blank");
            if (amount == null || !Double.isFinite(amount) || amount < 0) {
                throw new IllegalArgumentException(
                        "kill experience for " + mob + " must be zero or more and finite");
            }
            // Folded on the way in rather than at every lookup: a config is read once and consulted on
            // every kill, and a server killing hundreds of mobs a second should not re-case a string each
            // time. MythicMobs IDs are author-typed and vanilla types are an enum, so the two disagree on
            // case constantly and neither casing is the wrong thing to write.
            folded.put(key(mob), amount);
        });
        return Map.copyOf(folded);
    }

    private static String key(String mobId) {
        return mobId == null ? "" : mobId.trim().toLowerCase(Locale.ROOT);
    }
}
