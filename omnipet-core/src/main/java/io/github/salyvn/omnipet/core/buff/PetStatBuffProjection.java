package io.github.salyvn.omnipet.core.buff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;

/** Reads deterministic hatch stats as logical owner buffs without vendor linkage. */
public final class PetStatBuffProjection {
    public static final int MAX_STATS_PER_PET = 256;

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
                result.add(new PetStatBuff(pet.id(), statId, type, number.doubleValue()));
            } catch (IllegalArgumentException ignored) {
                // Invalid legacy/provider stats are isolated instead of disabling the pet.
            }
        }
        return List.copyOf(result);
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
