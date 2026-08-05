package io.github.salyvn.omnipet.core.buff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;

/**
 * Reads a hatched pet's rolled stats as owner buffs.
 *
 * <p>The stat ID written into a pet is the Studio's <em>logical</em> one, which for a stat chosen from the
 * MythicLib picker is the namespaced {@code mythiclib:attack_damage} form. MythicLib itself knows only its
 * own {@code ATTACK_DAMAGE}, so the namespace has to come back off before the ID is handed to it. That
 * translation is why a pet could show its stats in the vault and change nothing about the player: the
 * adapter was faithfully installing modifiers on a stat MythicLib had never registered.
 *
 * <p>The vendor ID is carried on the stat node as {@code vendorStatId}, put there by the catalog when the
 * operator picked the stat, and preserved verbatim through the hatch roll. It is preferred over parsing
 * the namespace off the ID, because it is what the vendor actually answered to.
 */
public final class PetStatBuffProjection {
    public static final int MAX_STATS_PER_PET = 256;

    /** Namespace the Studio's stat picker mints, and the only one this projection strips. */
    private static final String MYTHIC_LIB_PREFIX = "mythiclib:";

    private PetStatBuffProjection() {}

    public static List<PetStatBuff> read(PetInstance pet) {
        Object rawStats = pet.rawComponents().get("stats");
        if (!(rawStats instanceof List<?> stats)) return List.of();
        int size = Math.min(stats.size(), MAX_STATS_PER_PET);
        List<PetStatBuff> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            Object raw = stats.get(index);
            if (!(raw instanceof Map<?, ?> node)) continue;
            Object id = node.get("id");
            Object value = node.get("value");
            if (!(id instanceof String statId) || !(value instanceof Number number)) continue;
            String type = node.get("modifierType") instanceof String text ? text : "FLAT";
            try {
                result.add(new PetStatBuff(pet.id(), vendorStatId(statId, node), type, number.doubleValue()));
            } catch (IllegalArgumentException ignored) {
                // Invalid legacy/provider stats are isolated instead of disabling the pet.
            }
        }
        return List.copyOf(result);
    }

    /**
     * The ID the stat provider itself answers to.
     *
     * <p>Prefers the {@code vendorStatId} the catalog recorded, verbatim — that string came from the
     * provider, so it is already exactly what the provider answers to.
     *
     * <p>Otherwise strips the namespace and upper-cases what is left. The upper-casing is not cosmetic: the
     * catalog lower-cases the vendor name when it mints the logical ID ({@code ATTACK_DAMAGE} becomes
     * {@code mythiclib:attack_damage}), so stripping alone would hand MythicLib a name it does not
     * register. {@code StatLogicalIdentity} restores the case the same way for the same reason.
     *
     * <p>An ID with no namespace is passed through untouched, because that is a stat authored directly
     * against the provider's own vocabulary and rewriting it would break the setups that already work.
     */
    private static String vendorStatId(String statId, Map<?, ?> node) {
        if (node.get("vendorStatId") instanceof String vendorId && !vendorId.isBlank()) {
            return vendorId.trim();
        }
        if (!statId.regionMatches(true, 0, MYTHIC_LIB_PREFIX, 0, MYTHIC_LIB_PREFIX.length())) {
            return statId;
        }
        return statId.substring(MYTHIC_LIB_PREFIX.length()).trim().toUpperCase(java.util.Locale.ROOT);
    }

    public static List<PetStatBuff> readActive(List<PetInstance> pets, List<java.util.UUID> desiredIds) {
        if (pets == null || desiredIds == null || desiredIds.isEmpty()) return List.of();
        java.util.Set<java.util.UUID> desired = java.util.Set.copyOf(desiredIds);
        List<PetStatBuff> result = new ArrayList<>();
        for (PetInstance pet : pets) {
            if (desired.contains(pet.id())) result.addAll(read(pet));
        }
        return List.copyOf(result);
    }
}
