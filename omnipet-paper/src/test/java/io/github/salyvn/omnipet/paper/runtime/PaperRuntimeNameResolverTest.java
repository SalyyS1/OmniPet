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
 *
 * <p>This resolves the name alone. The level is carried beside it and placed by the nameplate template, so
 * that an operator can move it, restyle it, or leave it out — see {@code NameplateTest}.
 */
class PaperRuntimeNameResolverTest {
    @Test
    void anOwnersOwnNameBeatsTheDefinitionDefault() {
        Map<String, Object> definition = display("Wolf Cub");
        PetInstance renamed = pet(Map.of("management", Map.of("customName", "Shadow")));

        assertEquals("Shadow", PaperRuntimeNameResolver.resolve(definition, renamed));
    }

    @Test
    void theDefinitionDefaultIsUsedWhenNobodyHasRenamedIt() {
        assertEquals("Wolf Cub", PaperRuntimeNameResolver.resolve(display("Wolf Cub"), pet(Map.of())));
    }

    @Test
    void aPetWithNoNameAnywhereFallsBackToItsReadableDefinitionId() {
        // This used to return empty, on the argument that "tier_d_wolf" floating over a pet is worse than
        // nothing. In practice it meant nameplates never appeared at all: the only way to get one was
        // display.name, an undocumented raw-node key, so every pet on every server was unnamed and the
        // feature looked broken. A readable ID is a worse name than an operator would write and a far
        // better outcome than no plate, and it makes the rename button visibly do something.
        assertEquals("Wolf", PaperRuntimeNameResolver.resolve(Map.of(), pet(Map.of())));
        assertEquals("Wolf", PaperRuntimeNameResolver.resolve(null, pet(Map.of())));
        assertEquals("Wolf", PaperRuntimeNameResolver.resolve(display("   "), pet(Map.of())));
    }

    /** An underscored ID is made readable rather than shown raw. */
    @Test
    void theFallbackReadsAsWordsRatherThanAsAnIdentifier() {
        PetInstance underscored = new PetInstance(UUID.randomUUID(), "tier_d_wolf", 1, Map.of(), Map.of());

        assertEquals("Tier d wolf", PaperRuntimeNameResolver.resolve(Map.of(), underscored));
    }

    /** The operator's name and the player's rename both still outrank the ID. */
    @Test
    void theFallbackNeverOverridesANameSomebodyChose() {
        assertEquals("Wolf Cub", PaperRuntimeNameResolver.resolve(display("Wolf Cub"), pet(Map.of())));
    }

    /**
     * The level is not part of the name.
     *
     * <p>It used to be concatenated here, which meant the plate's layout was decided before anything
     * operator-configurable saw it — an operator could not move the level, restyle it, or drop it, because
     * by then it was already inside the name string.
     */
    @Test
    void theLevelIsNotBakedIntoTheName() {
        assertEquals("Wolf Cub", PaperRuntimeNameResolver.resolve(display("Wolf Cub"), pet(Map.of())));
        assertEquals(1, PaperRuntimeNameResolver.level(pet(Map.of())));
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

        String resolved = PaperRuntimeNameResolver.resolve(display(long_), pet(Map.of()));

        assertEquals(RendererAppearance.MAX_DISPLAY_NAME, resolved.length());
        // And the result is still a legal appearance, which is the property that actually matters.
        assertEquals(resolved, new RendererAppearance(
                "HEAD", "", "TEXTURE_URL", "https://example.invalid/a.png", resolved).displayName());
    }

    @Test
    void aMalformedManagementNodeCostsTheCustomNameAndNothingElse() {
        PetInstance broken = pet(Map.of("management", "not-a-map"));

        assertEquals("Wolf Cub", PaperRuntimeNameResolver.resolve(display("Wolf Cub"), broken));
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
