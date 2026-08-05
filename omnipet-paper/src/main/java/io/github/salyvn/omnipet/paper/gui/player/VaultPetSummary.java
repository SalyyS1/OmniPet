package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.management.PetManagementMetadata;

/**
 * Null-safe read of the few pet values a vault row shows.
 *
 * <p>Deliberately not {@code PetProgressionProjection.read}: that requires an initial stamina and a
 * clock, and throws {@link IllegalArgumentException} when a component is malformed or a field is
 * non-numeric. A renderer must never throw — one legacy pet with a bad component would blank the
 * whole vault page. This reader type-tests instead of casting and simply omits what it cannot read.
 *
 * <p>Level lives at {@code rawComponents.progression.level}; rarity at
 * {@code rawComponents.hatching.rarityId} (written by {@code IncubationPetFactory}). Legacy and
 * migrated pets may carry neither.
 */
public final class VaultPetSummary {
    private final Integer level;
    private final String rarity;
    private final boolean favorite;

    private VaultPetSummary(Integer level, String rarity, boolean favorite) {
        this.level = level;
        this.rarity = rarity;
        this.favorite = favorite;
    }

    public static VaultPetSummary of(PetInstance pet) {
        Objects.requireNonNull(pet, "pet");
        Map<String, Object> components = pet.rawComponents();
        return new VaultPetSummary(
                readLevel(nested(components, "progression")),
                readRarity(nested(components, "hatching")),
                // Pure and I/O-free: read off the already-loaded instance's extensions, and safe on a
                // pet that has no management node at all.
                PetManagementMetadata.read(pet).favorite());
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

    private static String readRarity(Map<?, ?> hatching) {
        if (hatching == null) return null;
        if (!(hatching.get("rarityId") instanceof String rarityId) || rarityId.isBlank()) return null;
        return rarityId;
    }
}
