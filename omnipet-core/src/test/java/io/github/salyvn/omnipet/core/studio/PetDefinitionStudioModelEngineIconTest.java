package io.github.salyvn.omnipet.core.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.persistence.FoundationRegistryLoader;
import io.github.salyvn.omnipet.core.persistence.InMemoryRegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.YamlPetDefinitionRepository;

/**
 * Saving a pet that renders through ModelEngine.
 *
 * <p>Every new Studio draft is seeded with a {@code CHANGE_ME} head icon, and save refused it. For a HEAD
 * pet that is right — the head icon <em>is</em> the pet's appearance. For a MODELENGINE pet it meant an
 * operator had to go and find a skin texture for a pet that would never wear one, and the refusal said
 * {@code icon.head.value must be configured} without explaining why a model pet needed a head, so it read
 * as the provider choice being broken.
 *
 * <p>The icon stays non-null — nine places dereference it, three of them mid-transaction — and a working
 * default is substituted instead. {@code HEAD_CATALOG} because that already means "a head with no texture"
 * everywhere downstream: it passes the source whitelist, and the head renderer resolves it to a plain
 * player head rather than throwing, so the HEAD fallback still has something to draw if ModelEngine is ever
 * unavailable.
 */
class PetDefinitionStudioModelEngineIconTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void aModelEngineDraftSavesWithoutAnyHeadIconBeingChosen() throws IOException {
        Fixture fixture = fixture();
        StudioPetDraft draft = modelEngineDraft(fixture, "wolf_model");

        var saved = fixture.service.save(draft, UUID.randomUUID());

        assertEquals(DisplayDefinition.Provider.MODELENGINE,
                saved.definition().definition().display().provider());
        assertEquals("wolf_model", saved.definition().definition().display().model());
    }

    @Test
    void theSubstitutedIconIsAnUntexturedHeadRatherThanTheSentinel() throws IOException {
        Fixture fixture = fixture();

        fixture.service.save(modelEngineDraft(fixture, "wolf_model"), UUID.randomUUID());

        HeadIcon icon = fixture.registry.current().definitions().get("modelled").icon();
        assertEquals("HEAD_CATALOG", icon.source(),
                "HEAD_CATALOG already means 'a head with no texture' to every reader downstream");
        assertEquals("modelled", icon.value());
        assertNotEquals(PetDefinitionStudioService.UNSET_ICON_VALUE, icon.value());
    }

    /** Live registry and disk have to agree, or the next save fails its consistency check. */
    @Test
    void theSubstitutedIconReachesDiskAndTheRegistryIdentically() throws IOException {
        Fixture fixture = fixture();

        fixture.service.save(modelEngineDraft(fixture, "wolf_model"), UUID.randomUUID());

        HeadIcon live = fixture.registry.current().definitions().get("modelled").icon();
        HeadIcon stored = fixture.definitions.read("modelled").orElseThrow().definition().icon();
        assertEquals(live, stored);
    }

    /** Proof the substitution did not just paper over a broken definition: it can be saved again. */
    @Test
    void aSubstitutedDefinitionCanBeEditedAndSavedAgain() throws IOException {
        Fixture fixture = fixture();
        fixture.service.save(modelEngineDraft(fixture, "wolf_model"), UUID.randomUUID());

        PetDefinition current = fixture.registry.current().definitions().get("modelled");
        StudioPetDraft edit = StudioPetDraft.edit(
                        current,
                        StudioPetDraft.semanticHash(current.rawNode()),
                        fixture.registry.current().generation())
                .withTier(PetTier.S);

        var saved = fixture.service.save(edit, UUID.randomUUID());

        assertEquals(PetTier.S, saved.definition().definition().tier());
    }

    /** A HEAD pet is unaffected: its icon is what a player sees, so it still has to be chosen. */
    @Test
    void aHeadPetStillHasToChooseAnIcon() throws IOException {
        Fixture fixture = fixture();
        StudioPetDraft draft = StudioPetDraft.create(
                "plain", fixture.registry.current().generation(), PetTier.D,
                new HeadIcon("BASE64", PetDefinitionStudioService.UNSET_ICON_VALUE),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null), Map.of());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.save(draft, UUID.randomUUID()));

        assertTrue(failure.getMessage().contains("icon.head.value"), failure.getMessage());
    }

    /** An operator who did choose a texture for a model pet keeps it. */
    @Test
    void aChosenIconIsNeverReplacedBySubstitution() throws IOException {
        Fixture fixture = fixture();
        StudioPetDraft draft = StudioPetDraft.create(
                "modelled", fixture.registry.current().generation(), PetTier.D,
                new HeadIcon("TEXTURE_URL", "https://example.invalid/model.png"),
                new DisplayDefinition(DisplayDefinition.Provider.MODELENGINE, "wolf_model"), Map.of());

        fixture.service.save(draft, UUID.randomUUID());

        HeadIcon icon = fixture.registry.current().definitions().get("modelled").icon();
        assertEquals("TEXTURE_URL", icon.source());
        assertEquals("https://example.invalid/model.png", icon.value());
    }

    /** The model is still required: dropping the icon check must not drop this one too. */
    @Test
    void aModelEngineDraftWithNoModelIsStillRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new DisplayDefinition(DisplayDefinition.Provider.MODELENGINE, "  "));
    }

    private StudioPetDraft modelEngineDraft(Fixture fixture, String model) {
        return StudioPetDraft.create(
                "modelled", fixture.registry.current().generation(), PetTier.D,
                new HeadIcon("BASE64", PetDefinitionStudioService.UNSET_ICON_VALUE),
                new DisplayDefinition(DisplayDefinition.Provider.MODELENGINE, model), Map.of());
    }

    private Fixture fixture() throws IOException {
        YamlPetDefinitionRepository definitions =
                new YamlPetDefinitionRepository(temporaryDirectory.resolve("pets"));
        InMemoryRegistrySnapshotRepository registry = new InMemoryRegistrySnapshotRepository();
        new FoundationRegistryLoader().load(definitions, registry);
        return new Fixture(definitions, registry,
                new PetDefinitionStudioService(definitions, registry, ignored -> { }));
    }

    private record Fixture(
            YamlPetDefinitionRepository definitions,
            InMemoryRegistrySnapshotRepository registry,
            PetDefinitionStudioService service) {}
}
