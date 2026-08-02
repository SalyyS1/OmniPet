package io.github.salyvn.omnipet.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;

class RepositoryProgressionServiceTest {
    @TempDir Path temporary;

    @Test
    void stablePetMutationPersistsUnderRevisionLock() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        FilePlayerStateRepository repository = seeded(playerId, petId);
        RepositoryProgressionService service = new RepositoryProgressionService(repository);

        RepositoryProgressionResult result = service.addExperience(
                playerId, 1, petId, 250, context("default"));

        assertTrue(result.persisted());
        assertEquals(2, result.playerState().revision());
        assertEquals(2, result.progression().state().level());
        assertEquals(100, result.progression().state().experience());
        assertEquals(2, PetProgressionProjection.read(
                repository.snapshot(playerId).pets().getFirst(), 0, 0).level());
        assertEquals("keep", result.pet().rawComponents().get("legacy"));
    }

    @Test
    void rejectedFormulaAndMissingPetDoNotCreateRevisionChurn() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        FilePlayerStateRepository repository = seeded(playerId, petId);
        RepositoryProgressionService service = new RepositoryProgressionService(repository);

        RepositoryProgressionResult invalid = service.addExperience(
                playerId, 1, petId, 20, context("unknown(level)"));
        RepositoryProgressionResult missing = service.addExperience(
                playerId, 1, UUID.randomUUID(), 20, context("default"));

        assertFalse(invalid.persisted());
        assertEquals(ProgressionResult.Status.INVALID_FORMULA, invalid.progression().status());
        assertEquals(RepositoryProgressionResult.Status.PET_NOT_FOUND, missing.status());
        assertEquals(1, repository.snapshot(playerId).revision());
    }

    @Test
    void duplicateActionReceiptCannotAdvanceRevisionTwice() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        FilePlayerStateRepository repository = seeded(playerId, petId);
        RepositoryProgressionService service = new RepositoryProgressionService(repository);
        CultivationItemActionTransaction action = action(token, playerId, petId, "a".repeat(64));

        RepositoryProgressionResult first = service.addExperience(
                playerId, 1, petId, 250, context("default"), action);
        RepositoryProgressionResult duplicate = service.addExperience(
                playerId, first.playerState().revision(), petId, 250, context("default"), action);

        assertTrue(first.persisted());
        assertTrue(duplicate.persisted());
        assertEquals(2, first.playerState().revision());
        assertEquals(2, duplicate.playerState().revision());
        assertEquals(first.progression().state(), duplicate.progression().state());
        assertEquals(2, repository.snapshot(playerId).revision());
        assertTrue(service.hasAction(playerId, petId, action));
    }

    @Test
    void acknowledgeRemovesTemporaryReceiptWithoutChangingProgression() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        FilePlayerStateRepository repository = seeded(playerId, petId);
        RepositoryProgressionService service = new RepositoryProgressionService(repository);
        CultivationItemActionTransaction action = action(token, playerId, petId, "b".repeat(64));

        RepositoryProgressionResult applied = service.addExperience(
                playerId, 1, petId, 250, context("default"), action);
        ProgressionState progression = applied.progression().state();
        assertTrue(service.hasAction(playerId, petId, action));

        service.acknowledgeAction(playerId, petId, action);

        PetInstance savedPet = repository.snapshot(playerId).pets().getFirst();
        assertFalse(service.hasAction(playerId, petId, action));
        assertFalse(savedPet.rawComponents().containsKey(CultivationActionReceiptProjection.COMPONENT_KEY));
        assertEquals(progression, PetProgressionProjection.read(savedPet, 0, 0));
        assertEquals(3, repository.snapshot(playerId).revision());
    }

    @Test
    void staleRevisionDoesNotMutateProgressionOrPersistActionReceipt() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        FilePlayerStateRepository repository = seeded(playerId, petId);
        RepositoryProgressionService service = new RepositoryProgressionService(repository);
        CultivationItemActionTransaction action = action(token, playerId, petId, "c".repeat(64));

        assertThrows(StaleRevisionException.class, () -> service.addExperience(
                playerId, 0, petId, 250, context("default"), action));

        PetInstance savedPet = repository.snapshot(playerId).pets().getFirst();
        assertEquals(1, repository.snapshot(playerId).revision());
        assertEquals(ProgressionState.initial(0, 0), PetProgressionProjection.read(savedPet, 0, 0));
        assertFalse(service.hasAction(playerId, petId, action));
    }

    private FilePlayerStateRepository seeded(UUID playerId, UUID petId) throws Exception {
        FilePlayerStateRepository repository = new FilePlayerStateRepository(temporary.resolve(playerId.toString()));
        repository.withLocked(playerId, 0, state -> state.withStorage(
                List.of(new PetInstance(petId, "ember_fox", 1, Map.of("legacy", "keep"), Map.of())),
                10, 1, List.of(), List.of()));
        return repository;
    }

    private static ProgressionMutationContext context(String formula) {
        return new ProgressionMutationContext(
                new ProgressionConfig(5, 100, 5, values -> 150,
                        Map.of(), ProgressionConfig.OverflowPolicy.CARRY),
                formula,
                Map.of("rarity", 1.0, "quality", 50.0),
                100,
                1_000);
    }

    private static CultivationItemActionTransaction action(
            UUID token,
            UUID playerId,
            UUID petId,
            String fingerprint) {
        return new CultivationItemActionTransaction(
                token,
                playerId,
                petId,
                1,
                new EggItemIdentity(
                        2,
                        EggInventoryHand.MAIN_HAND,
                        "minecraft:paper",
                        token,
                        fingerprint,
                        1,
                        Map.of()),
                CultivationItemActionKind.EXPERIENCE_CANDY,
                250,
                1,
                0,
                CultivationItemActionStage.PREPARED);
    }
}
