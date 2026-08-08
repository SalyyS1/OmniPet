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
    /**
     * A definition file as an operator would actually write one, read the way the server reads it.
     *
     * <p>Every other test here hands the projection a `Map` built in Java, which skips the two steps that
     * stand between a file on disk and a cast: the YAML parser, and the codec that decides what survives into
     * `rawNode`. A codec that dropped unknown keys, or a parser that turned `8s` into something other than a
     * string, would leave a pet with no bindings at all — and a pet with no bindings casts nothing, reports
     * nothing, and looks exactly like a broken skill system. So the whole path is exercised from text.
     */
    @Test
    void aHandWrittenDefinitionFileYieldsACastableBinding() {
        String yaml = """
                schemaVersion: 1
                definitionId: ember_fox
                revision: 1
                classification:
                  tier: S
                icon:
                  head:
                    source: BASE64
                    value: texture
                display:
                  provider: HEAD
                skills:
                  - provider: MYTHICMOBS
                    id: Fireball
                    trigger: SHIFT_RIGHT_CLICK
                    cooldown: 8s
                    targetPolicy: LOOK_THEN_NEAREST
                    power: 1.5
                """;

        PetDefinition definition = new io.github.salyvn.omnipet.core.persistence.PetDefinitionYamlCodec()
                .decode("ember_fox", yaml)
                .definition();
        List<SkillBinding> bindings = SkillBindingProjection.read(definition);

        assertEquals(1, bindings.size(), "a hand-written skills block must survive the codec");
        SkillBinding binding = bindings.getFirst();
        assertEquals("MYTHICMOBS", binding.provider());
        assertEquals("Fireball", binding.skillId());
        assertEquals(SkillTrigger.SHIFT_RIGHT_CLICK, binding.trigger());
        // The operator-friendly duration, not ISO-8601: `8s` used to disable the whole binding silently.
        assertEquals(java.time.Duration.ofSeconds(8), binding.cooldown());
        assertEquals(SkillTargetPolicy.LOOK_THEN_NEAREST, binding.targetPolicy());
        assertEquals(1.5, binding.power());
    }

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
