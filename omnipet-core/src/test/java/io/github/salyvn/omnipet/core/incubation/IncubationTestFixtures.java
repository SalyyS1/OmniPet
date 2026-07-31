package io.github.salyvn.omnipet.core.incubation;

import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.HatchCandidate;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;

final class IncubationTestFixtures {
    private IncubationTestFixtures() {}

    static EggDefinition egg(long durationMillis) {
        return new EggDefinition(
                "tier_d_egg",
                PetTier.D,
                durationMillis,
                List.of(new HatchCandidate("ember_fox", 1, Map.of())),
                Map.of());
    }

    static RegistrySnapshot registry() {
        return new RegistrySnapshot(12, Map.of("ember_fox", pet()));
    }

    static PetDefinition pet() {
        Map<String, Object> raw = Map.of(
                "stats", List.of(
                        Map.of("id", "attack_damage", "modifierType", "FLAT", "min", 5, "max", 20),
                        Map.of("id", "movement_speed", "modifierType", "RELATIVE", "min", 0.05, "max", 0.25)),
                "rarity", Map.of("bands", List.of(
                        Map.of("id", "COMMON", "qualityMin", 0, "qualityMax", 60,
                                "weight", 8, "hatchMultiplier", 1.0),
                        Map.of("id", "LEGENDARY", "qualityMin", 80, "qualityMax", 100,
                                "weight", 2, "hatchMultiplier", 1.5))));
        return new PetDefinition(
                "ember_fox",
                4,
                PetTier.D,
                new HeadIcon("BASE64", "texture"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null),
                raw);
    }
}
