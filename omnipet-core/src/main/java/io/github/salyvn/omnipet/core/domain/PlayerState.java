package io.github.salyvn.omnipet.core.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlayerState(
        UUID playerId,
        long revision,
        List<PetInstance> pets,
        Integer legacyCurrentPetIndex,
        Map<String, Object> legacyCurrentEgg,
        Integer legacyCapacity,
        Map<String, Object> extensions) {
    public static final int MAX_LEGACY_CAPACITY = 100_000;

    public PlayerState {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        if (revision < 0) throw new IllegalArgumentException("player revision cannot be negative");
        pets = List.copyOf(pets == null ? List.of() : pets);
        if (new HashSet<>(pets.stream().map(PetInstance::id).toList()).size() != pets.size()) {
            throw new IllegalArgumentException("duplicate pet instance UUID");
        }
        if (legacyCurrentPetIndex != null
                && (legacyCurrentPetIndex < -1 || legacyCurrentPetIndex >= pets.size())) {
            throw new IllegalArgumentException("legacy currentPetIndex is outside the pet list");
        }
        legacyCurrentEgg = RawNodeValues.immutableMap(legacyCurrentEgg == null ? Map.of() : legacyCurrentEgg);
        if (legacyCapacity != null && (legacyCapacity < 0 || legacyCapacity > MAX_LEGACY_CAPACITY)) {
            throw new IllegalArgumentException("legacy capacity is outside the supported range");
        }
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(legacyCurrentEgg, "currentEgg");
        RawNodeValues.rejectNonFinite(extensions, "extensions");
    }

    public PlayerState withRevision(long nextRevision) {
        return new PlayerState(playerId, nextRevision, pets, legacyCurrentPetIndex, legacyCurrentEgg, legacyCapacity, extensions);
    }
}
