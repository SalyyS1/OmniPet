package io.github.salyvn.omnipet.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class OmniPetConfigLoaderTest {
    @Test
    void loadsShippedAggregateConfigAndCompilesFormula() throws Exception {
        String yaml;
        try (var input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        OmniPetConfig config = new OmniPetConfigLoader().parse(yaml).config();

        assertEquals(64, config.runtime().maximumOwnersPerTick());
        assertEquals(100, config.progression().maxLevel());
        assertEquals(125.0, config.progression().defaultFormula().required(
                java.util.Map.of("level", 1.0, "rarity", 1.0, "quality", 0.5, "evolution", 0.0)));
        assertEquals("EXPERIENCE_BOTTLE", config.cultivationItems().experienceMaterial());
    }

    @Test
    void storageOnlyConfigKeepsBackwardCompatibleDefaults() {
        String yaml = """
                storage:
                  vault:
                    baseCapacity: 30
                    maxCapacity: 200
                    legacyPermission: { enabled: true, template: "petstorage.slot.%s", maxScan: 200 }
                  activeSlots:
                    multiPetEnabled: true
                    base: 1
                    max: 5
                    entitlement:
                      mode: OMNIPET
                      precedence: OMNIPET_AUTHORITATIVE
                      luckPermsPermissionTemplate: "omnipet.slot.unlocked.%s"
                    unlocks:
                      "2": { permission: "", costs: { VAULT: 100 } }
                """;

        OmniPetConfig config = new OmniPetConfigLoader().parse(yaml).config();

        assertEquals(1, config.runtime().periodTicks());
        assertEquals(100.0, config.cultivationItems().experienceAmount());
    }

    @Test
    void rejectsUnknownKeysAndNonPositiveFormulaSamples() {
        String shipped;
        try (var input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            shipped = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new OmniPetConfigLoader().parse(shipped + "\nunknownRoot: true\n"));
        assertThrows(IllegalArgumentException.class,
                () -> new OmniPetConfigLoader().parse(shipped.replace(
                        "100 + level * 25 + evolution * 100", "0")));
    }
}
