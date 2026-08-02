package io.github.salyvn.omnipet.core.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PetActivationResult(
        List<UUID> activePetIds,
        List<UUID> spawnedPetIds,
        List<UUID> removedPetIds,
        Map<UUID, String> failures) {
    public PetActivationResult {
        activePetIds = List.copyOf(activePetIds == null ? List.of() : activePetIds);
        spawnedPetIds = List.copyOf(spawnedPetIds == null ? List.of() : spawnedPetIds);
        removedPetIds = List.copyOf(removedPetIds == null ? List.of() : removedPetIds);
        failures = Map.copyOf(failures == null ? Map.of() : failures);
    }

    public boolean converged() {
        return failures.isEmpty();
    }
}
