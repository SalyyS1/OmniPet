package io.github.salyvn.omnipet.core.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;

class ReleaseServiceTest {
    @TempDir
    Path temporary;

    @Test
    void previewFreezesExactBoundedRewardsAndStableIdentities() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        FilePlayerStateRepository repository = seeded(playerId, List.of(pet(petId, Map.of())), Map.of());
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(Map.of("material", "DIAMOND"));
        ReleaseService service = new ReleaseService(repository, ignored -> new ReleaseRewardBundle(
                List.of(new ReleaseRewardBundle.InternalReward("material", 3, payload)),
                List.of(new ReleaseRewardBundle.ExternalReward(
                        "vault", "coins", new BigDecimal("12.50"), Map.of("reason", "release")))));
        UUID transactionId = UUID.randomUUID();

        ReleasePreview preview = service.preview(transactionId, playerId, petId).preview();
        payload.put("material", "DIRT");

        assertEquals(transactionId, preview.transactionId());
        assertEquals(petId, preview.petId());
        assertEquals("DIAMOND", preview.rewards().internalRewards().getFirst().payload().get("material"));
        assertEquals(new BigDecimal("12.5"), preview.rewards().externalRewards().getFirst().amount());
        assertNotNull(preview.confirmationToken());
        assertThrows(IllegalArgumentException.class, () -> new ReleaseRewardBundle.InternalReward(
                "material", ReleaseRewardBundle.MAX_INTERNAL_AMOUNT + 1, Map.of()));
    }

    @Test
    void lockedPetIsRejectedBeforeRewardCalculation() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        FilePlayerStateRepository repository = seeded(
                playerId, List.of(pet(petId, Map.of("management", Map.of("locked", true)))), Map.of());
        int[] calls = {0};
        ReleaseService service = new ReleaseService(repository, ignored -> {
            calls[0]++;
            return rewards();
        });

        ReleasePreviewResult result = service.preview(UUID.randomUUID(), playerId, petId);

        assertEquals(ReleasePreviewResult.Status.LOCKED, result.status());
        assertEquals(0, calls[0]);
    }

    @Test
    void staleConfirmationCannotReleaseDifferentListEntry() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PetInstance first = pet(firstId, Map.of());
        PetInstance second = pet(secondId, Map.of());
        FilePlayerStateRepository repository = seeded(playerId, List.of(first, second), Map.of());
        ReleaseService service = new ReleaseService(repository, ignored -> rewards());
        ReleasePreview preview = service.preview(UUID.randomUUID(), playerId, firstId).preview();
        repository.withLocked(playerId, 1, state -> state.withStorage(
                List.of(second, first), state.vaultCapacity(), state.activeSlotCount(),
                state.desiredActivePetIds(), state.slotEntitlements()));

        ReleaseResult result = service.release(preview);

        assertEquals(ReleaseResult.Status.STALE_REVISION, result.status());
        assertEquals(List.of(secondId, firstId), repository.snapshot(playerId).pets().stream().map(PetInstance::id).toList());
    }

    @Test
    void releaseAtomicallyRemovesPetAndPersistsOutboxWhilePreservingExtensions() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        FilePlayerStateRepository repository = seeded(
                playerId, List.of(pet(petId, Map.of())), Map.of("foreignNode", Map.of("keep", 7)), true);
        ReleaseService service = new ReleaseService(repository, ignored -> rewards());
        ReleasePreview preview = service.preview(UUID.randomUUID(), playerId, petId).preview();

        ReleaseResult result = service.release(preview);
        PlayerState restarted = new FilePlayerStateRepository(temporary.resolve("players-" + playerId)).snapshot(playerId);

        assertEquals(ReleaseResult.Status.COMMITTED, result.status());
        assertFalse(restarted.pets().stream().anyMatch(pet -> pet.id().equals(petId)));
        assertFalse(restarted.desiredActivePetIds().contains(petId));
        assertEquals(Map.of("keep", 7), restarted.extensions().get("foreignNode"));
        assertEquals(ReleaseOutboxEntry.InternalState.PENDING, result.outboxEntry().internalState());
    }

    @Test
    void copiedConfirmationTokenCannotTargetAnotherPetIdentity() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        FilePlayerStateRepository repository = seeded(playerId, List.of(pet(petId, Map.of())), Map.of());
        ReleaseService service = new ReleaseService(repository, ignored -> rewards());
        ReleasePreview original = service.preview(UUID.randomUUID(), playerId, petId).preview();
        ReleasePreview copied = new ReleasePreview(
                original.transactionId(), original.playerId(), UUID.randomUUID(), original.expectedRevision(),
                original.petFingerprint(), original.rewards(), original.confirmationToken());

        ReleaseResult result = service.release(copied);

        assertEquals(ReleaseResult.Status.INVALID_CONFIRMATION, result.status());
        assertEquals(1, repository.snapshot(playerId).pets().size());
    }

    @Test
    void aPreviewSurvivesStaminaRegeneratingUnderneathIt() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        Map<String, Object> before = Map.of("progression", new java.util.LinkedHashMap<>(Map.of(
                "level", 3,
                "experience", 40.0,
                "evolution", 1,
                "stamina", 12.0,
                "lastStaminaEpochMillis", 1_000L)));
        FilePlayerStateRepository repository = seeded(playerId, List.of(pet(petId, before)), Map.of());
        ReleaseService service = new ReleaseService(repository, ignored -> rewards());
        ReleasePreview preview = service.preview(UUID.randomUUID(), playerId, petId).preview();

        // Stamina refills on its own while the player is deciding. That is not a change to which pet
        // this is, so the preview they were shown must still be good when they confirm it.
        Map<String, Object> after = Map.of("progression", new java.util.LinkedHashMap<>(Map.of(
                "level", 3,
                "experience", 40.0,
                "evolution", 1,
                "stamina", 20.0,
                "lastStaminaEpochMillis", 9_000L)));
        repository.withLocked(playerId, repository.snapshot(playerId).revision(), state -> new PlayerState(
                playerId, state.revision(), List.of(pet(petId, after)), state.vaultCapacity(),
                state.activeSlotCount(), state.desiredActivePetIds(), state.slotEntitlements(),
                state.legacyCurrentEgg(), state.extensions()));

        ReleasePreview reissued = service.preview(
                preview.transactionId(), playerId, petId).preview();

        assertEquals(preview.petFingerprint(), reissued.petFingerprint(),
                "stamina regeneration must not change what the pet is");
    }

    @Test
    void aRealChangeToThePetStillChangesItsFingerprint() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        Map<String, Object> before = Map.of("progression", new java.util.LinkedHashMap<>(Map.of(
                "level", 3,
                "experience", 40.0,
                "evolution", 1,
                "stamina", 12.0,
                "lastStaminaEpochMillis", 1_000L)));
        FilePlayerStateRepository repository = seeded(playerId, List.of(pet(petId, before)), Map.of());
        ReleaseService service = new ReleaseService(repository, ignored -> rewards());
        ReleasePreview preview = service.preview(UUID.randomUUID(), playerId, petId).preview();

        Map<String, Object> levelled = Map.of("progression", new java.util.LinkedHashMap<>(Map.of(
                "level", 4,
                "experience", 0.0,
                "evolution", 1,
                "stamina", 12.0,
                "lastStaminaEpochMillis", 1_000L)));
        repository.withLocked(playerId, repository.snapshot(playerId).revision(), state -> new PlayerState(
                playerId, state.revision(), List.of(pet(petId, levelled)), state.vaultCapacity(),
                state.activeSlotCount(), state.desiredActivePetIds(), state.slotEntitlements(),
                state.legacyCurrentEgg(), state.extensions()));

        ReleasePreview reissued = service.preview(
                preview.transactionId(), playerId, petId).preview();

        assertNotEquals(preview.petFingerprint(), reissued.petFingerprint(),
                "levelling up is a real change and must still be caught");
    }

    @Test
    void samePreviewRetryIsIdempotent() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        FilePlayerStateRepository repository = seeded(playerId, List.of(pet(petId, Map.of())), Map.of());
        ReleaseService service = new ReleaseService(repository, ignored -> rewards());
        ReleasePreview preview = service.preview(UUID.randomUUID(), playerId, petId).preview();

        ReleaseResult first = service.release(preview);
        ReleaseResult second = service.release(preview);

        assertEquals(ReleaseResult.Status.COMMITTED, first.status());
        assertEquals(ReleaseResult.Status.ALREADY_COMMITTED, second.status());
        assertEquals(first.outboxEntry().transactionId(), second.outboxEntry().transactionId());
    }

    private FilePlayerStateRepository seeded(
            UUID playerId,
            List<PetInstance> pets,
            Map<String, Object> extensions) throws Exception {
        return seeded(playerId, pets, extensions, false);
    }

    private FilePlayerStateRepository seeded(
            UUID playerId,
            List<PetInstance> pets,
            Map<String, Object> extensions,
            boolean active) throws Exception {
        FilePlayerStateRepository repository = new FilePlayerStateRepository(temporary.resolve("players-" + playerId));
        repository.withLocked(playerId, 0, state -> new PlayerState(
                playerId, state.revision(), pets, 10, 1,
                active ? List.of(pets.getFirst().id()) : List.of(), List.of(), Map.of(), null, extensions));
        return repository;
    }

    private static PetInstance pet(UUID petId, Map<String, Object> components) {
        return new PetInstance(petId, "ember_fox", 4, components, Map.of("foreignPetNode", "keep"));
    }

    private static ReleaseRewardBundle rewards() {
        return new ReleaseRewardBundle(
                List.of(new ReleaseRewardBundle.InternalReward("material", 2, Map.of("type", "DIAMOND"))),
                List.of());
    }
}
