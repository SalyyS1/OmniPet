package io.github.salyvn.omnipet.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;

class RepositoryPetStorageServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void successfulMutationPersistsUnderRevisionLockAndRejectedMutationDoesNotWrite() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = new FilePlayerStateRepository(temporaryDirectory.resolve("players"));
        RepositoryPetStorageService storage = new RepositoryPetStorageService(repository);
        PetStorageLimits limits = new PetStorageLimits(1, 1, true);
        PetInstance pet = new PetInstance(UUID.randomUUID(), "fox", 1, Map.of(), Map.of());

        PetStorageResult admitted = storage.admit(playerId, 0, pet, limits);
        PetStorageResult rejected = storage.admit(playerId, 1, pet, limits);

        assertEquals(PetStorageResult.Status.ADMITTED, admitted.status());
        assertEquals(1, admitted.state().revision());
        assertEquals(PetStorageResult.Status.DUPLICATE_PET_ID, rejected.status());
        assertEquals(1, repository.snapshot(playerId).revision());
        assertThrows(StaleRevisionException.class, () -> storage.remove(playerId, 0, pet.id(), limits));
    }

    @Test
    void noOpReconciliationDoesNotCreateRevisionChurn() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = new FilePlayerStateRepository(temporaryDirectory.resolve("players"));
        RepositoryPetStorageService storage = new RepositoryPetStorageService(repository);
        PetStorageLimits limits = new PetStorageLimits(30, 1, true);

        PetStorageResult result = storage.reconcileLimits(playerId, 0, limits);

        assertEquals(PetStorageResult.Status.LIMITS_RECONCILED, result.status());
        assertEquals(0, result.state().revision());
        assertEquals(0, repository.snapshot(playerId).revision());
    }
}
