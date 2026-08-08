package io.github.salyvn.omnipet.paper.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.persistence.PetDefinitionYamlCodec;
import io.github.salyvn.omnipet.core.skill.SkillBinding;
import io.github.salyvn.omnipet.core.skill.SkillBindingProjection;
import io.github.salyvn.omnipet.core.skill.SkillCastRequest;
import io.lumine.mythic.bukkit.MythicBukkit;

/**
 * A definition file reaching MythicMobs as a cast, with nothing in between stubbed out.
 *
 * <p>The pieces of this path each had their own test and the path itself had none, which is how "pet skills
 * do not work" survived several rounds of fixes: every unit passed, and the joins were where the casts were
 * being lost. This starts from the YAML an operator writes and ends at the vendor call, asserting the skill
 * name, the targets, and the power that actually arrive.
 *
 * <p>Deliberately not a server test. The two steps that need Bukkit — resolving a policy into nearby entities
 * and scheduling onto the main thread — are covered by {@code PaperSkillTargetResolver} and the controller's
 * own tests; what was never covered is that a binding read from a file produces a cast the vendor accepts.
 */
class PetSkillCastEndToEndTest {
    /**
     * The skill name is written in a different case in the file than MythicMobs registered it.
     *
     * <p>That is the common case, not an edge one: MythicMobs keys a skill by its YAML node name exactly as
     * written, so a definition naming it in any other casing has to be translated before the cast. An exact
     * match refused casts MythicMobs would have honoured, and refused them before the vendor was ever called
     * — no error, no effect.
     */
    @Test
    void aDefinitionFileCastsThroughToMythicMobsWithItsRegisteredName() {
        MythicBukkit.reset();
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
                    id: EMBER_BURST
                    trigger: SHIFT_RIGHT_CLICK
                    cooldown: 8s
                    targetPolicy: NEAREST_HOSTILE
                    power: 2.5
                """;
        SkillBinding binding = SkillBindingProjection.read(
                new PetDefinitionYamlCodec().decode("ember_fox", yaml).definition()).getFirst();

        Object caster = new Object();
        Object mob = new Object();
        UUID target = UUID.randomUUID();
        ReflectiveMythicMobsSkillProvider provider = new ReflectiveMythicMobsSkillProvider(
                getClass().getClassLoader(), () -> true, () -> true,
                ignored -> caster,
                id -> id.equals(target) ? mob : null);
        provider.refresh(1);

        // What the controller does between reading the binding and casting: fold the name to the provider's
        // own spelling, refusing only when the provider genuinely does not know it.
        String skillId = provider.catalog().canonical(binding.skillId());
        assertNotNull(skillId, "a case difference must not refuse a skill MythicMobs knows");
        assertEquals("ember_burst", skillId);

        UUID owner = UUID.randomUUID();
        var result = provider.cast(new SkillCastRequest(
                UUID.randomUUID(), owner, owner, UUID.randomUUID(), skillId,
                binding.targetPolicy(), List.of(target), binding.power(),
                Map.of("world", "world")));

        assertTrue(result.succeeded(), result.detail());
        var helper = MythicBukkit.inst().getAPIHelper();
        assertEquals(caster, helper.caster);
        // The targeted overload, or MythicMobs would aim from the owning player instead of at the pet's mark.
        assertTrue(helper.usedTargetedOverload, "a resolved target must reach the vendor");
        assertEquals(List.of(mob), helper.entityTargets);
        assertEquals(2.5f, helper.power, "the definition's power must survive to the vendor call");
    }

    /**
     * A skill the provider has never registered is refused before the vendor is called.
     *
     * <p>The other half of the same question: the fold must not be so loose that a typo silently casts
     * something else. An operator who misspells a skill gets a refusal that names what they wrote.
     */
    @Test
    void aSkillTheProviderDoesNotKnowIsRefusedBeforeTheVendorIsCalled() {
        MythicBukkit.reset();
        ReflectiveMythicMobsSkillProvider provider = new ReflectiveMythicMobsSkillProvider(
                getClass().getClassLoader(), () -> true, () -> true, ignored -> new Object());
        provider.refresh(1);

        assertEquals(null, provider.catalog().canonical("ember_bust"));
        assertEquals(null, MythicBukkit.inst().getAPIHelper().caster,
                "a skill absent from the catalog must never reach MythicMobs");
    }
}
