package io.github.salyvn.omnipet.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.persistence.YamlDocuments;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;

class Phase4PaperConfigLoaderTest {
    private final Phase4PaperConfigLoader loader = new Phase4PaperConfigLoader();

    @Test
    void migratesLegacySlotConfigAndEmitsCurrentSchema() {
        Phase4PaperConfigLoader.LoadResult result = loader.parseWithReport("""
                globalMaxSlots: 1000
                slotPermission: petstorage.slot.%s
                """);

        assertTrue(result.migratedLegacy());
        assertEquals(30, result.config().vault().baseCapacity());
        assertEquals(1000, result.config().vault().maxCapacity());
        assertFalse(loader.encode(result.config()).contains("globalMaxSlots"));
        assertEquals(result.config(), loader.parse(loader.encode(result.config())));
    }

    @Test
    void migratesLegacyDefaultsWhenOptionalKeysAreMissing() {
        Phase4PaperConfigLoader.LoadResult empty = loader.parseWithReport("{}\n");
        Phase4PaperConfigLoader.LoadResult permissionOnly = loader.parseWithReport("""
                slotPermission: custom.pet.slot.%s
                """);

        assertTrue(empty.migratedLegacy());
        assertEquals(1000, empty.config().vault().maxCapacity());
        assertEquals("petstorage.slot.3", empty.config().vault().legacyPermission().permissionNode(3));
        assertEquals(1000, permissionOnly.config().vault().maxCapacity());
        assertEquals("custom.pet.slot.3", permissionOnly.config().vault().legacyPermission().permissionNode(3));
    }

    @Test
    void migratesLegacyZeroCapacityWithoutEnablingPermissionScanning() {
        Phase4PaperConfigLoader.LoadResult result = loader.parseWithReport("globalMaxSlots: 0\n");

        assertTrue(result.migratedLegacy());
        assertEquals(0, result.config().vault().baseCapacity());
        assertEquals(0, result.config().vault().maxCapacity());
        assertFalse(result.config().vault().legacyPermission().enabled());
        assertEquals(0, result.config().vault().legacyPermission().maxScan());
        assertEquals(result.config(), loader.parse(loader.encode(result.config())));
    }

    @Test
    void loadsAuthoritativeDefaultsIntoDetachedValidatedSnapshot() throws IOException {
        Phase4PaperConfig config = loader.parse(shippedStorageSection());

        assertEquals(30, config.vault().baseCapacity());
        assertEquals(200, config.vault().maxCapacity());
        assertTrue(config.vault().legacyPermission().enabled());
        assertEquals("petstorage.slot.7", config.vault().legacyPermission().permissionNode(7));
        assertEquals(200, config.vault().legacyPermission().maxScan());
        assertTrue(config.activeSlots().multiPetEnabled());
        assertEquals(1, config.activeSlots().base());
        assertEquals(5, config.activeSlots().max());
        assertEquals(4, config.activeSlots().unlocks().size());
        assertEquals(50, config.activeSlots().unlock(2).orElseThrow()
                .costs().get(EconomyProvider.PLAYER_POINTS).playerPointsValue());
        assertEquals("omnipet.slot.unlocked.3", config.activeSlots().entitlement().permissionNode(3));
    }

    @Test
    void rejectsDuplicateMalformedMissingAndUnknownYaml() {
        assertThrows(IllegalArgumentException.class, () -> loader.parse(validYaml().replace(
                "baseCapacity: 30", "baseCapacity: 30\n    baseCapacity: 31")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse("- storage\n- vault\n"));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(validYaml().replace(
                "    maxCapacity: 200\n", "")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(validYaml().replace(
                "    maxCapacity: 200", "    maxCapacity: 200\n    globalMaxSlots: 1000")));
    }

    @Test
    void rejectsMissingExtraUnsupportedAndUnsafePermissionPlaceholders() {
        assertInvalidTemplate("petstorage.slot");
        assertInvalidTemplate("petstorage.%s.slot.%s");
        assertInvalidTemplate("petstorage.slot.%d");
        assertInvalidTemplate("petstorage.*.%s");
        assertInvalidTemplate("PetStorage.slot.%s");
    }

    @Test
    void rejectsInvertedFractionalAndHugeLimitsAtSafeOperatorAndCoreCaps() {
        assertThrows(IllegalArgumentException.class, () -> loader.parse(validYaml().replace(
                "baseCapacity: 30", "baseCapacity: 201")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(validYaml().replace(
                "maxCapacity: 200", "maxCapacity: 10001")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(validYaml().replace(
                "maxScan: 200", "maxScan: 201")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(validYaml().replace(
                "base: 1", "base: 2.5")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(validYaml().replace(
                "base: 1", "base: 6")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(validYaml().replace(
                "max: 5", "max: " + (PetStorageLimits.MAX_ACTIVE_SLOT_COUNT + 1))));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(validYaml().replace(
                "maxCapacity: 200", "maxCapacity: 999999999999999999999999999")));
        assertTrue(Phase4PaperConfig.MAX_OPERATOR_VAULT_CAPACITY < PetStorageLimits.MAX_VAULT_CAPACITY);
    }

    @Test
    void supportsExplicitLegacyDisableWithoutSkippingTemplateValidation() {
        Phase4PaperConfig disabled = loader.parse(validYaml().replace("enabled: true", "enabled: false"));
        assertFalse(disabled.vault().legacyPermission().enabled());
        assertInvalidYaml(validYaml()
                .replace("enabled: true", "enabled: false")
                .replace("petstorage.slot.%s", "petstorage.slot.*"));
    }

    @Test
    void rejectsFractionalPointsAndContradictoryEntitlementPolicy() throws IOException {
        String defaults = shippedStorageSection();
        assertInvalidYaml(defaults.replace("PLAYER_POINTS: 50", "PLAYER_POINTS: 50.5"));
        assertInvalidYaml(defaults.replace(
                "precedence: OMNIPET_AUTHORITATIVE",
                "precedence: LUCKPERMS_AUTHORITATIVE"));
    }

    @Test
    void rejectsEntitlementTemplateThatExceedsNodeLimitAtConfiguredMaximum() throws IOException {
        String defaults = shippedStorageSection();
        String boundaryTemplate = "a".repeat(127) + "%s";
        Phase4PaperConfig singleDigitConfig = loader.parse(
                defaults.replace("omnipet.slot.unlocked.%s", boundaryTemplate));

        assertEquals(128, singleDigitConfig.activeSlots().entitlement().permissionNode(5).length());
        assertInvalidYaml(defaults
                .replace("max: 5", "max: 64")
                .replace("omnipet.slot.unlocked.%s", boundaryTemplate));
    }

    /**
     * Returns only the storage subtree of the shipped config. The strict Phase 4 loader owns storage
     * alone; the aggregate loader owns runtime, progression, item, and integration sections.
     */
    private static String shippedStorageSection() throws IOException {
        String shipped;
        try (InputStream input = Phase4PaperConfigLoaderTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            if (input == null) throw new IOException("config.yml test resource is missing");
            shipped = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, Object> root = YamlDocuments.readMap(shipped);
        Object storage = root.get("storage");
        if (!(storage instanceof Map<?, ?>)) throw new IOException("shipped config.yml has no storage section");
        return YamlDocuments.writeMap(Map.of("storage", storage));
    }

    private void assertInvalidTemplate(String template) {
        assertInvalidYaml(validYaml().replace("petstorage.slot.%s", template));
    }

    private void assertInvalidYaml(String yaml) {
        assertThrows(IllegalArgumentException.class, () -> loader.parse(yaml));
    }

    private static String validYaml() {
        return """
                storage:
                  vault:
                    baseCapacity: 30
                    maxCapacity: 200
                    legacyPermission:
                      enabled: true
                      template: "petstorage.slot.%s"
                      maxScan: 200
                  activeSlots:
                    multiPetEnabled: true
                    base: 1
                    max: 5
                """;
    }
}
