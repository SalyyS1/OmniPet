package io.github.salyvn.omnipet.core.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
import io.github.salyvn.omnipet.core.persistence.PetDefinitionDraft;
import io.github.salyvn.omnipet.core.persistence.YamlPetDefinitionRepository;

class PetDefinitionStudioCloneTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void cloneCannotOverwriteAnExistingDefinition() throws IOException {
        Fixture fixture = fixture();
        PetDefinition source = fixture.registry.current().definitions().get("wolf");
        StudioPetDraft clone = StudioPetDraft.edit(source, StudioPetDraft.semanticHash(source.rawNode()),
                fixture.registry.current().generation()).cloneTo("wolf");

        assertThrows(StudioConflictException.class, () -> fixture.service.save(clone, UUID.randomUUID()));
        assertEquals(1, fixture.definitions.read("wolf").orElseThrow().definition().revision());
    }

    @Test
    void saveRejectsLegacyAndNamespacedAliasesForTheSameStat() throws IOException {
        Fixture fixture = fixture();
        StudioPetDraft draft = StudioPetDraft.create("fox", fixture.registry.current().generation(), PetTier.C,
                new HeadIcon("TEXTURE_URL", "https://example.invalid/fox.png"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null), Map.of())
                .withStats(List.of(
                        new StudioStat("ATTACK_DAMAGE", StatModifierType.FLAT, new StatRange(1, 2), Map.of()),
                        new StudioStat("mythiclib:attack_damage", StatModifierType.FLAT,
                                new StatRange(3, 4), Map.of("vendorStatId", "ATTACK_DAMAGE"))));

        assertThrows(IllegalArgumentException.class, () -> fixture.service.save(draft, UUID.randomUUID()));
        assertEquals(false, fixture.registry.current().definitions().containsKey("fox"));
    }

    private Fixture fixture() throws IOException {
        YamlPetDefinitionRepository definitions = new YamlPetDefinitionRepository(temporaryDirectory.resolve("pets"));
        PetDefinition initial = new PetDefinition("wolf", 0, PetTier.D,
                new HeadIcon("TEXTURE_URL", "https://example.invalid/wolf.png"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null),
                Map.of("classification", Map.of("tier", "D")));
        definitions.saveDraft(new PetDefinitionDraft(initial, 0));
        InMemoryRegistrySnapshotRepository registry = new InMemoryRegistrySnapshotRepository();
        new FoundationRegistryLoader().load(definitions, registry);
        return new Fixture(definitions, registry, new PetDefinitionStudioService(definitions, registry, ignored -> {}));
    }

    private record Fixture(YamlPetDefinitionRepository definitions, InMemoryRegistrySnapshotRepository registry,
                           PetDefinitionStudioService service) {}
}
