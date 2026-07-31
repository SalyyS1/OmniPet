package io.github.salyvn.omnipet.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;

class PetStorageServiceTest {
    private final PetStorageService storage = new PetStorageService();

    @Test
    void admitsWithinEffectiveVaultAndRejectsDuplicateOrFullVaultWithoutDeletingPets() {
        PetInstance first = pet("first_pet");
        PetInstance second = pet("second_pet");
        PlayerState state = state(List.of(first), 1, 1, List.of());
        PetStorageLimits limits = new PetStorageLimits(2, 1, true);

        PetStorageResult admitted = storage.admit(state, second, limits);

        assertEquals(PetStorageResult.Status.ADMITTED, admitted.status());
        assertEquals(List.of(first, second), admitted.state().pets());
        assertEquals(2, admitted.snapshot().effectiveVaultCapacity());

        PetStorageResult duplicate = storage.admit(admitted.state(), second, limits);
        assertEquals(PetStorageResult.Status.DUPLICATE_PET_ID, duplicate.status());
        assertSame(admitted.state(), duplicate.state());

        PetStorageResult full = storage.admit(admitted.state(), pet("third_pet"), limits);
        assertEquals(PetStorageResult.Status.VAULT_CAPACITY_REACHED, full.status());
        assertSame(admitted.state(), full.state());
        assertEquals(List.of(first, second), full.state().pets());
    }

    @Test
    void activationPreservesOrderAndRejectsDuplicatesUnknownPetsAndSlotOverflow() {
        PetInstance first = pet("first_pet");
        PetInstance second = pet("second_pet");
        PetInstance third = pet("third_pet");
        PlayerState state = state(List.of(first, second, third), 3, 3, List.of());
        PetStorageLimits limits = new PetStorageLimits(3, 2, true);

        PetStorageResult firstActivation = storage.activate(state, second.id(), limits);
        PetStorageResult secondActivation = storage.activate(firstActivation.state(), first.id(), limits);

        assertEquals(List.of(second.id(), first.id()), secondActivation.state().desiredActivePetIds());
        assertEquals(PetStorageResult.Status.PET_ALREADY_ACTIVE,
                storage.activate(secondActivation.state(), first.id(), limits).status());
        assertEquals(PetStorageResult.Status.ACTIVE_SLOT_CAPACITY_REACHED,
                storage.activate(secondActivation.state(), third.id(), limits).status());
        assertEquals(PetStorageResult.Status.PET_NOT_OWNED,
                storage.activate(secondActivation.state(), UUID.randomUUID(), limits).status());
    }

    @Test
    void activationNormalizesConfiguredBaseWithoutRequiringPriorReconciliation() {
        PetInstance first = pet("first_pet");
        PetInstance second = pet("second_pet");
        PlayerState state = state(List.of(first, second), 2, 1, List.of(first.id()));

        PetStorageResult activated = storage.activate(
                state, second.id(), new PetStorageLimits(2, 2, 5, true));

        assertEquals(PetStorageResult.Status.ACTIVATED, activated.status());
        assertEquals(2, activated.state().activeSlotCount());
        assertEquals(List.of(first.id(), second.id()), activated.state().desiredActivePetIds());
    }

    @Test
    void deactivationAndRemovalReturnStructuredResultsAndRecallActivePet() {
        PetInstance first = pet("first_pet");
        PetInstance second = pet("second_pet");
        PlayerState state = state(List.of(first, second), 2, 2, List.of(first.id(), second.id()));
        PetStorageLimits limits = new PetStorageLimits(2, 2, true);

        PetStorageResult deactivated = storage.deactivate(state, first.id(), limits);
        assertEquals(PetStorageResult.Status.DEACTIVATED, deactivated.status());
        assertEquals(List.of(second.id()), deactivated.state().desiredActivePetIds());
        assertEquals(List.of(first.id()), deactivated.recalledPetIds());
        assertEquals(PetStorageResult.Status.PET_NOT_ACTIVE,
                storage.deactivate(deactivated.state(), first.id(), limits).status());

        PetStorageResult removed = storage.remove(deactivated.state(), second.id(), limits);
        assertEquals(PetStorageResult.Status.REMOVED, removed.status());
        assertTrue(removed.succeeded());
        assertEquals(second, removed.removedPet());
        assertEquals(List.of(second.id()), removed.recalledPetIds());
        assertEquals(List.of(first), removed.state().pets());
        assertTrue(removed.state().desiredActivePetIds().isEmpty());

        PetStorageResult missing = storage.remove(removed.state(), UUID.randomUUID(), limits);
        assertEquals(PetStorageResult.Status.PET_NOT_OWNED, missing.status());
        assertFalse(missing.succeeded());
        assertSame(removed.state(), missing.state());
    }

    private static PlayerState state(
            List<PetInstance> pets,
            int vaultCapacity,
            int activeSlotCount,
            List<UUID> desiredActivePetIds) {
        return new PlayerState(
                UUID.randomUUID(),
                0,
                pets,
                vaultCapacity,
                activeSlotCount,
                desiredActivePetIds,
                List.of(),
                Map.of(),
                Map.of());
    }

    private static PetInstance pet(String definitionId) {
        return new PetInstance(UUID.randomUUID(), definitionId, 1, Map.of(), Map.of());
    }
}
