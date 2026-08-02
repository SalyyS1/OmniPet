package io.github.salyvn.omnipet.core.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;

class RepositoryPetManagementServiceTest {
    @TempDir
    java.nio.file.Path temporary;

    @Test
    void preservesUnknownMetadataAcrossFavoriteLockAndRename() throws Exception {
        UUID playerId = UUID.randomUUID();
        PetInstance pet = pet("wolf", Map.of("management", Map.of("future", "kept"), "other", 7));
        FilePlayerStateRepository repository = repository(playerId, List.of(pet));
        RepositoryPetManagementService service = new RepositoryPetManagementService(repository);

        var favorite = service.favorite(playerId, repository.snapshot(playerId).revision(), pet.id(), true);
        var locked = service.lock(playerId, favorite.state().revision(), pet.id(), true);
        var renamed = service.rename(playerId, locked.state().revision(), pet.id(), "Luna");

        assertTrue(renamed.succeeded());
        PetManagementMetadata metadata = PetManagementMetadata.read(renamed.pet());
        assertTrue(metadata.favorite());
        assertTrue(metadata.locked());
        assertEquals("Luna", metadata.customName());
        assertEquals(7, renamed.pet().extensions().get("other"));
        assertEquals("kept", ((Map<?, ?>) renamed.pet().extensions().get("management")).get("future"));
    }

    @Test
    void movesStablePetIdAndRejectsInvalidPosition() throws Exception {
        UUID playerId = UUID.randomUUID();
        PetInstance first = pet("wolf", Map.of());
        PetInstance second = pet("fox", Map.of());
        FilePlayerStateRepository repository = repository(playerId, List.of(first, second));
        RepositoryPetManagementService service = new RepositoryPetManagementService(repository);

        var moved = service.move(playerId, repository.snapshot(playerId).revision(), second.id(), 0);
        var rejected = service.move(playerId, moved.state().revision(), first.id(), 4);

        assertEquals(second.id(), moved.state().pets().getFirst().id());
        assertFalse(rejected.succeeded());
        assertEquals(PetManagementResult.Status.INVALID_MOVE, rejected.status());
    }

    private static PetInstance pet(String definition, Map<String, Object> extensions) {
        return new PetInstance(UUID.randomUUID(), definition, 1, Map.of(), extensions);
    }

    private FilePlayerStateRepository repository(UUID playerId, List<PetInstance> pets) throws Exception {
        FilePlayerStateRepository repository = new FilePlayerStateRepository(temporary.resolve(playerId.toString()));
        repository.withLocked(playerId, 0, state -> state.withStorage(pets, 30, 1, List.of(), List.of()));
        return repository;
    }
}
