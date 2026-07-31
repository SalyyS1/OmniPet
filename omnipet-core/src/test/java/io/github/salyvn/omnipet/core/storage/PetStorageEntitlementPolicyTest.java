package io.github.salyvn.omnipet.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PlayerState;

class PetStorageEntitlementPolicyTest {
    @Test
    void externalAndHybridCountsRespectConfiguredPrecedence() {
        PlayerState localThree = new PlayerState(
                UUID.randomUUID(), 0, List.of(), 10, 3, List.of(), List.of(), Map.of(), Map.of());

        assertEquals(2, limits(SlotEntitlementPrecedence.LUCKPERMS_AUTHORITATIVE, 2)
                .effectiveActiveSlotCount(localThree));
        assertEquals(2, limits(SlotEntitlementPrecedence.REQUIRE_BOTH, 2)
                .effectiveActiveSlotCount(localThree));
        assertEquals(3, limits(SlotEntitlementPrecedence.UNION, 2)
                .effectiveActiveSlotCount(localThree));
    }

    private static PetStorageLimits limits(SlotEntitlementPrecedence precedence, int external) {
        return new PetStorageLimits(
                10,
                1,
                5,
                true,
                external,
                new SlotEntitlementPolicy(SlotEntitlementMode.HYBRID, precedence));
    }
}
