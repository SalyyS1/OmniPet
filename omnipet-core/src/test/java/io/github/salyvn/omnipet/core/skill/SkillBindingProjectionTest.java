package io.github.salyvn.omnipet.core.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;

class SkillBindingProjectionTest {
    @Test
    void readsValidBindingsAndIsolatesInvalidNodes() {
        PetDefinition definition = new PetDefinition(
                "ember_fox", 1, PetTier.S,
                new HeadIcon("BASE64", "texture"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, ""),
                Map.of("skills", List.of(
                        Map.of("provider", "mythicmobs", "id", "ember_burst", "trigger", "ACTIVE",
                                "cooldown", "PT5S", "chance", 1.0, "staminaCost", 8.0,
                                "targetPolicy", "OWNER"),
                        Map.of("provider", "mythicmobs"))));

        List<SkillBinding> bindings = SkillBindingProjection.read(definition);

        assertEquals(1, bindings.size());
        assertEquals("MYTHICMOBS", bindings.getFirst().provider());
        assertEquals("skill_0", bindings.getFirst().bindingId());
    }
}
