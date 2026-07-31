package io.github.salyvn.omnipet.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;

class PetStorageLimitsTest {
    private final PetStorageService storage = new PetStorageService();

    @Test
    void rejectsInvalidAndUntrustedHugeConfiguredLimits() {
        assertThrows(IllegalArgumentException.class, () -> new PetStorageLimits(-1, 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> new PetStorageLimits(PetStorageLimits.MAX_VAULT_CAPACITY + 1, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new PetStorageLimits(1, 0, true));
        assertThrows(IllegalArgumentException.class,
                () -> new PetStorageLimits(1, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT + 1, true));
        assertThrows(IllegalArgumentException.class, () -> new PetStorageLimits(1, 3, 2, true));
    }

    @Test
    void combinesPersistedVaultFloorWithConfiguredCapacityAndCapsActiveSlots() {
        PlayerState state = state(List.of(), 5, 4, List.of());

        PetStorageSnapshot configuredHigher = storage.snapshot(state, new PetStorageLimits(8, 2, true));
        assertEquals(8, configuredHigher.effectiveVaultCapacity());
        assertEquals(2, configuredHigher.effectiveActiveSlotCount());

        PetStorageSnapshot persistedHigher = storage.snapshot(state, new PetStorageLimits(3, 6, true));
        assertEquals(5, persistedHigher.effectiveVaultCapacity());
        assertEquals(4, persistedHigher.effectiveActiveSlotCount());
    }

    @Test
    void configuredActiveFloorRaisesNewProfilesWithoutGrantingBeyondTheCap() {
        PlayerState state = state(List.of(), 0, 1, List.of());

        PetStorageResult reconciled = storage.reconcileLimits(state, new PetStorageLimits(30, 2, 5, true));

        assertEquals(2, reconciled.state().activeSlotCount());
        assertEquals(2, reconciled.snapshot().effectiveActiveSlotCount());
    }

    @Test
    void disablingMultiPetRetainsFirstIntentAndSafelyRecallsOverflow() {
        PetInstance first = pet("first_pet");
        PetInstance second = pet("second_pet");
        PetInstance third = pet("third_pet");
        PlayerState state = state(
                List.of(first, second, third),
                3,
                3,
                List.of(second.id(), first.id(), third.id()));

        PetStorageResult result = storage.reconcileLimits(state, new PetStorageLimits(3, 3, false));

        assertEquals(PetStorageResult.Status.LIMITS_RECONCILED, result.status());
        assertEquals(List.of(second.id()), result.state().desiredActivePetIds());
        assertEquals(List.of(first.id(), third.id()), result.recalledPetIds());
        assertEquals(List.of(first, second, third), result.state().pets());
        assertEquals(3, result.state().activeSlotCount());
        assertEquals(1, result.snapshot().effectiveActiveSlotCount());
    }

    @Test
    void reducedVaultLimitMarksOverflowAndRejectsAdmissionWithoutPetLoss() {
        PetInstance first = pet("first_pet");
        PetInstance second = pet("second_pet");
        PlayerState overflow = state(List.of(first, second), 0, 1, List.of());
        PetStorageLimits limits = new PetStorageLimits(1, 1, true);

        PetStorageResult reconciled = storage.reconcileLimits(overflow, limits);
        assertSame(overflow, reconciled.state());
        assertEquals(1, reconciled.snapshot().vaultOverflow());

        PetStorageResult admission = storage.admit(overflow, pet("third_pet"), limits);
        assertEquals(PetStorageResult.Status.VAULT_CAPACITY_REACHED, admission.status());
        assertSame(overflow, admission.state());
        assertEquals(List.of(first, second), admission.state().pets());
    }

    @Test
    void activeLimitReductionRecallsTailInStableOrder() {
        PetInstance first = pet("first_pet");
        PetInstance second = pet("second_pet");
        PetInstance third = pet("third_pet");
        PlayerState state = state(
                List.of(first, second, third),
                3,
                3,
                List.of(third.id(), first.id(), second.id()));

        PetStorageResult result = storage.reconcileLimits(state, new PetStorageLimits(3, 2, true));

        assertEquals(List.of(third.id(), first.id()), result.state().desiredActivePetIds());
        assertEquals(List.of(second.id()), result.recalledPetIds());
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
