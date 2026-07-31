package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetInstance;

class PetReferenceScannerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void scansEggYamlReferencesWithoutFollowingLinks() throws Exception {
        Path eggs = temporaryDirectory.resolve("eggs.yml");
        Files.writeString(eggs, "common:\n  pets: [fox]\n");

        assertEquals(Set.of("yaml:eggs.yml"), PetReferenceScanner.yamlFiles(List.of(eggs)).references("fox"));
        assertEquals(Set.of(), PetReferenceScanner.yamlFiles(List.of(eggs)).references("wolf"));
    }

    @Test
    void scansPlayerPetDefinitions() throws Exception {
        UUID playerId = UUID.randomUUID();
        FilePlayerStateRepository repository = new FilePlayerStateRepository(temporaryDirectory.resolve("players"));
        repository.withLocked(playerId, 0, state -> state.withStorage(
                List.of(new PetInstance(UUID.randomUUID(), "fox", 1, Map.of(), Map.of())),
                state.vaultCapacity(),
                state.activeSlotCount(),
                state.desiredActivePetIds(),
                state.slotEntitlements()));

        assertEquals(Set.of("player:" + playerId), repository.referenceScan("fox"));
    }
}
