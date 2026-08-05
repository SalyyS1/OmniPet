package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.salyvn.omnipet.core.buff.PetStatBuff;
import io.github.salyvn.omnipet.core.buff.PetStatBuffProjection;
import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.management.PetManagementMetadata;

/**
 * Null-safe read of the pet values a vault row shows.
 *
 * <p>Deliberately not {@code PetProgressionProjection.read}: that requires an initial stamina and a
 * clock, and throws {@link IllegalArgumentException} when a component is malformed or a field is
 * non-numeric. A renderer must never throw — one legacy pet with a bad component would blank the
 * whole vault page. This reader type-tests instead of casting and simply omits what it cannot read.
 *
 * <p>Level and experience live under {@code rawComponents.progression}; rarity under
 * {@code rawComponents.hatching.rarityId}; realized stats under {@code rawComponents.stats} — all written
 * by {@code IncubationPetFactory}. Legacy and migrated pets may carry none of them.
 */
public final class VaultPetSummary {
    /**
     * How many stat lines a row will show.
     *
     * <p>A pet may carry up to 256 stats and an item's lore has to stay readable, so the row shows the
     * few largest and says how many were left out rather than either truncating silently or listing
     * everything.
     */
    public static final int MAX_STAT_LINES = 5;

    private final Integer level;
    private final Double experience;
    private final String rarity;
    private final boolean favorite;
    private final List<PetStatBuff> stats;
    private final int hiddenStatCount;

    private VaultPetSummary(
            Integer level,
            Double experience,
            String rarity,
            boolean favorite,
            List<PetStatBuff> stats,
            int hiddenStatCount) {
        this.level = level;
        this.experience = experience;
        this.rarity = rarity;
        this.favorite = favorite;
        this.stats = stats;
        this.hiddenStatCount = hiddenStatCount;
    }

    public static VaultPetSummary of(PetInstance pet) {
        Objects.requireNonNull(pet, "pet");
        Map<String, Object> components = pet.rawComponents();
        Map<?, ?> progression = nested(components, "progression");
        // Reuses the reader the buff port already relies on, which skips an unusable stat rather than
        // throwing — the same tolerance a renderer needs.
        List<PetStatBuff> all = PetStatBuffProjection.read(pet);
        List<PetStatBuff> ranked = all.stream()
                .sorted(Comparator.comparingDouble((PetStatBuff stat) -> Math.abs(stat.value())).reversed()
                        // Ties broken on the ID so two pets with the same stats list them the same way.
                        .thenComparing(PetStatBuff::statId))
                .limit(MAX_STAT_LINES)
                .toList();
        return new VaultPetSummary(
                readLevel(progression),
                readExperience(progression),
                readRarity(nested(components, "hatching")),
                // Pure and I/O-free: read off the already-loaded instance's extensions, and safe on a
                // pet that has no management node at all.
                PetManagementMetadata.read(pet).favorite(),
                ranked,
                all.size() - ranked.size());
    }

    /**
     * The pet's level.
     *
     * <p>A pet with no progression component reads as level 1 rather than as unknown, which is the same
     * answer {@code ProgressionState.initial} gives and therefore the same level the pet actually has.
     * The two used to disagree: the projection defaulted a missing component to level 1 while this reader
     * returned nothing, and since a freshly hatched pet has no progression component written for it, every
     * new pet showed no level at all in the vault until its first cultivation action created the node.
     *
     * <p>Still empty for a component that exists but is corrupt. A level that is present and unreadable is
     * a different situation from a level that was never written, and inventing a number for the first
     * would hide the corruption.
     */
    public Optional<Integer> level() {
        return Optional.ofNullable(level);
    }

    /** The pet's rarity ID, or empty when absent or blank. */
    public Optional<String> rarity() {
        return Optional.ofNullable(rarity);
    }

    /**
     * Experience banked towards the next level, or empty when the pet has never earned any.
     *
     * <p>Shown as a bare total rather than a percentage. The target for the next level comes from a
     * configured formula evaluated against level, rarity, quality, and evolution, and the vault renders
     * from an already-loaded storage snapshot with no access to that configuration. Showing "340" is
     * honest; showing a percentage derived from a guessed target would not be.
     */
    public Optional<Double> experience() {
        return Optional.ofNullable(experience);
    }

    /**
     * The pet's realized stats, largest absolute value first, capped at {@link #MAX_STAT_LINES}.
     *
     * <p>Ordered by magnitude because that is what a player comparing two pets is looking for, and a pet
     * with more stats than the cap is more usefully summarised by its biggest than by its first.
     */
    public List<PetStatBuff> stats() {
        return stats;
    }

    /** How many stats exist beyond the ones {@link #stats()} returns. */
    public int hiddenStatCount() {
        return hiddenStatCount;
    }

    /** Whether the player marked this pet a favorite. */
    public boolean favorite() {
        return favorite;
    }

    private static Map<?, ?> nested(Map<String, Object> components, String key) {
        Object raw = components.get(key);
        return raw instanceof Map<?, ?> map ? map : null;
    }

    /**
     * The level from a progression component.
     *
     * <p>A missing component means the pet has never been cultivated, which is level 1 — the same value
     * {@code ProgressionState.initial} would produce. A component that is present but unreadable stays
     * empty, because that is corruption rather than absence.
     */
    private static Integer readLevel(Map<?, ?> progression) {
        if (progression == null) return INITIAL_LEVEL;
        if (!(progression.get("level") instanceof Number number)) return null;
        double decimal = number.doubleValue();
        long exact = number.longValue();
        // A fractional or out-of-range level is data corruption, not something to round.
        if (!Double.isFinite(decimal) || decimal != exact) return null;
        if (exact < Integer.MIN_VALUE || exact > Integer.MAX_VALUE) return null;
        return (int) exact;
    }

    /** The level a pet has before anything has cultivated it. Mirrors {@code ProgressionState.initial}. */
    private static final Integer INITIAL_LEVEL = 1;

    /**
     * Banked experience, or null when absent or unreadable.
     *
     * <p>Absent reads as nothing rather than as zero. A pet with no progression node has earned no
     * experience, and a row that says "0 exp" on every uncultivated pet is noise where a missing line is
     * simply quiet.
     */
    private static Double readExperience(Map<?, ?> progression) {
        if (progression == null) return null;
        if (!(progression.get("experience") instanceof Number number)) return null;
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value <= 0) return null;
        return value;
    }

    private static String readRarity(Map<?, ?> hatching) {
        if (hatching == null) return null;
        if (!(hatching.get("rarityId") instanceof String rarityId) || rarityId.isBlank()) return null;
        return rarityId;
    }
}
