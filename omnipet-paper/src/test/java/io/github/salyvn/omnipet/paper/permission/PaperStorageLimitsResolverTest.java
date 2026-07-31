package io.github.salyvn.omnipet.paper.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.core.storage.SlotEntitlementMode;
import io.github.salyvn.omnipet.core.storage.SlotEntitlementPolicy;
import io.github.salyvn.omnipet.core.storage.SlotEntitlementPrecedence;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;

class PaperStorageLimitsResolverTest {
    @Test
    void combinesBaseVaultWithLiveConsecutiveLegacyCountAndCorePersistedFloor() {
        Phase4PaperConfig config = config(true, true);
        PaperStorageLimitsResolver resolver = new PaperStorageLimitsResolver(config);
        PlayerState state = state(60, 7);

        PaperStorageLimitsResolver.Resolution withPermissions = resolver.resolve(node -> slot(node) <= 40);

        assertEquals(40, withPermissions.limits().configuredVaultCapacity());
        assertEquals(40, withPermissions.legacyPermission().consecutiveGrantedSlots());
        assertEquals(60, withPermissions.limits().effectiveVaultCapacity(state));
        assertEquals(5, withPermissions.limits().effectiveActiveSlotCount(state));

        PaperStorageLimitsResolver.Resolution afterPermissionRemoval = resolver.resolve(ignored -> false);
        assertEquals(30, afterPermissionRemoval.limits().configuredVaultCapacity());
        assertEquals(60, afterPermissionRemoval.limits().effectiveVaultCapacity(state));
        assertEquals(60, state.vaultCapacity());
    }

    @Test
    void legacyDisableUsesBaseAndNeverProbesLivePermissions() {
        PaperStorageLimitsResolver resolver = new PaperStorageLimitsResolver(config(false, true));

        PaperStorageLimitsResolver.Resolution result = resolver.resolve(ignored -> {
            throw new AssertionError("disabled legacy permissions must not be checked");
        });

        assertEquals(30, result.limits().configuredVaultCapacity());
        assertEquals(LegacyVaultPermissionResolver.StopReason.DISABLED,
                result.legacyPermission().stopReason());
    }

    @Test
    void multiPetDisableClampsActiveIntentThroughCoreLimits() {
        PaperStorageLimitsResolver resolver = new PaperStorageLimitsResolver(config(true, false));
        PetStorageLimits limits = resolver.resolve(ignored -> false).limits();

        assertEquals(5, limits.configuredActiveSlotCount());
        assertEquals(1, limits.effectiveActiveSlotCount(state(30, 5)));
    }

    @Test
    void configuredBaseRaisesEffectiveSlotsForNewProfiles() {
        Phase4PaperConfig config = new Phase4PaperConfig(
                new Phase4PaperConfig.Vault(
                        30,
                        200,
                        new Phase4PaperConfig.LegacyPermission(false, "petstorage.slot.%s", 200)),
                new Phase4PaperConfig.ActiveSlots(true, 2, 5));

        PetStorageLimits limits = new PaperStorageLimitsResolver(config).resolve(ignored -> false).limits();

        assertEquals(2, limits.configuredActiveSlotFloor());
        assertEquals(2, limits.effectiveActiveSlotCount(state(0, 1)));
    }

    @Test
    void luckPermsAuthoritativeModeUsesConsecutiveObservedNodes() {
        Phase4PaperConfig config = new Phase4PaperConfig(
                new Phase4PaperConfig.Vault(
                        30,
                        200,
                        new Phase4PaperConfig.LegacyPermission(false, "petstorage.slot.%s", 200)),
                new Phase4PaperConfig.ActiveSlots(
                        true,
                        1,
                        5,
                        new Phase4PaperConfig.Entitlement(
                                new SlotEntitlementPolicy(
                                        SlotEntitlementMode.LUCKPERMS,
                                        SlotEntitlementPrecedence.LUCKPERMS_AUTHORITATIVE),
                                "omnipet.slot.unlocked.%s"),
                        Map.of()));

        PetStorageLimits limits = new PaperStorageLimitsResolver(config)
                .resolve(node -> node.endsWith(".2") || node.endsWith(".3"))
                .limits();

        assertEquals(3, limits.observedExternalActiveSlotCount());
        assertEquals(3, limits.effectiveActiveSlotCount(state(30, 5)));
    }

    private static Phase4PaperConfig config(boolean legacyEnabled, boolean multiPetEnabled) {
        return new Phase4PaperConfig(
                new Phase4PaperConfig.Vault(
                        30,
                        200,
                        new Phase4PaperConfig.LegacyPermission(
                                legacyEnabled, "petstorage.slot.%s", 200)),
                new Phase4PaperConfig.ActiveSlots(multiPetEnabled, 1, 5));
    }

    private static PlayerState state(int vaultCapacity, int activeSlotCount) {
        return new PlayerState(
                UUID.randomUUID(),
                0,
                List.of(),
                vaultCapacity,
                activeSlotCount,
                List.of(),
                List.of(),
                Map.of(),
                Map.of());
    }

    private static int slot(String node) {
        return Integer.parseInt(node.substring(node.lastIndexOf('.') + 1));
    }
}
