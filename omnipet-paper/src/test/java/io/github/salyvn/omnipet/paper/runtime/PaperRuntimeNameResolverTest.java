package io.github.salyvn.omnipet.paper.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;

/**
 * What a rendered pet is called.
 *
 * <p>Before this there was no nameplate at all and no path for a name to reach a renderer, so an activated
 * pet was an anonymous floating head. Two sources feed it: a default an operator writes on the definition,
 * and whatever the pet's owner renamed it to.
 *
 * <p>The owner wins. Renaming is the entire point of the stored custom name, and a definition-level default
 * that overrode it would make the rename look broken.
 */
class PaperRuntimeNameResolverTest {
    @Test
    void anOwnersOwnNameBeatsTheDefinitionDefault() {
        Map<String, Object> definition = display("Wolf Cub");
        PetInstance renamed = pet(Map.of("management", Map.of("customName", "Shadow")));

        assertEquals("Shadow  Lv.1", PaperRuntimeNameResolver.resolve(definition, renamed, 1));
    }

    @Test
    void theDefinitionDefaultIsUsedWhenNobodyHasRenamedIt() {
        assertEquals("Wolf Cub  Lv.4",
                PaperRuntimeNameResolver.resolve(display("Wolf Cub"), pet(Map.of()), 4));
    }

    @Test
    void aPetWithNoNameAnywhereHasNoNameplate() {
        // Empty rather than falling back to the definition ID. "tier_d_wolf" floating over a pet is worse
        // than nothing, and an operator who wants a name can write one.
        assertTrue(PaperRuntimeNameResolver.resolve(Map.of(), pet(Map.of()), 3).isEmpty());
        assertTrue(PaperRuntimeNameResolver.resolve(null, pet(Map.of()), 3).isEmpty());
        assertTrue(PaperRuntimeNameResolver.resolve(display("   "), pet(Map.of()), 3).isEmpty());
    }

    @Test
    void theLevelIsAppendedOnlyWhenItIsKnown() {
        assertEquals("Wolf Cub", PaperRuntimeNameResolver.resolve(display("Wolf Cub"), pet(Map.of()), null));
        assertEquals("Wolf Cub  Lv.12",
                PaperRuntimeNameResolver.resolve(display("Wolf Cub"), pet(Map.of()), 12));
    }

    @Test
    void anUncultivatedPetReadsAsLevelOne() {
        // Matches ProgressionState.initial and the vault's reader. Hatching writes no progression node, so
        // treating absent as unknown would leave every new pet's plate without a level.
        assertEquals(1, PaperRuntimeNameResolver.level(pet(Map.of())));
    }

    @Test
    void aMalformedProgressionNodeCostsTheLevelAndNotTheRender() {
        // The whole reason this does not call PetProgressionProjection.read, which throws on this input.
        assertEquals(null, PaperRuntimeNameResolver.level(
                petWithComponents(Map.of("progression", Map.of("level", "seven")))));
        assertEquals(null, PaperRuntimeNameResolver.level(
                petWithComponents(Map.of("progression", Map.of("level", 2.5)))));
        // Not a map is not a progression node at all, which reads the same as absent.
        assertEquals(1, PaperRuntimeNameResolver.level(
                petWithComponents(Map.of("progression", "not-a-map"))));
    }

    @Test
    void aLevelReadsFromTheComponentWhenItIsThere() {
        assertEquals(9, PaperRuntimeNameResolver.level(
                petWithComponents(Map.of("progression", Map.of("level", 9)))));
    }

    @Test
    void aNameTooLongForAPlateIsTruncatedRatherThanRefused() {
        // A cosmetic label must never be able to cost a pet its render, so this trims instead of throwing.
        String long_ = "x".repeat(RendererAppearance.MAX_DISPLAY_NAME + 40);

        String resolved = PaperRuntimeNameResolver.resolve(display(long_), pet(Map.of()), 5);

        assertEquals(RendererAppearance.MAX_DISPLAY_NAME, resolved.length());
        // And the result is still a legal appearance, which is the property that actually matters.
        assertEquals(resolved, new RendererAppearance(
                "HEAD", "", "TEXTURE_URL", "https://example.invalid/a.png", resolved).displayName());
    }

    @Test
    void aMalformedManagementNodeCostsTheCustomNameAndNothingElse() {
        PetInstance broken = pet(Map.of("management", "not-a-map"));

        assertEquals("Wolf Cub  Lv.1", PaperRuntimeNameResolver.resolve(display("Wolf Cub"), broken, 1));
    }

    private static Map<String, Object> display(String name) {
        return Map.of("display", Map.of("name", name));
    }

    private static PetInstance pet(Map<String, Object> extensions) {
        return new PetInstance(UUID.randomUUID(), "wolf", 1, Map.of(), new LinkedHashMap<>(extensions));
    }

    private static PetInstance petWithComponents(Map<String, Object> components) {
        return new PetInstance(UUID.randomUUID(), "wolf", 1, new LinkedHashMap<>(components), Map.of());
    }
}
