package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinitionEnvelope;
import io.github.salyvn.omnipet.core.domain.incubation.HatchCandidate;

/**
 * The catalog became writable so that creating a pet could also create the egg that hatches it.
 *
 * <p>Before this the repository was read-only, so a Studio-created pet had no catalog entry naming it
 * and no way to obtain one in game.
 */
class YamlEggDefinitionRepositoryWriteTest {
    @TempDir
    Path root;

    @Test
    void savedDefinitionsReadBackIdentically() throws IOException {
        YamlEggDefinitionRepository repository = new YamlEggDefinitionRepository(root);
        EggDefinition definition = definition("ember_fox_egg", "ember_fox", Duration.ofHours(1));

        repository.save(new EggDefinitionEnvelope(EggDefinitionEnvelope.CURRENT_SCHEMA_VERSION, definition));

        EggDefinition reloaded = repository.read("ember_fox_egg").orElseThrow().definition();
        assertEquals(definition, reloaded);
        assertEquals(List.of("ember_fox_egg"), repository.list());
    }

    @Test
    void savingCreatesTheCatalogDirectoryOnAFreshInstall() throws IOException {
        Path missing = root.resolve("eggs");
        assertTrue(!Files.exists(missing));

        new YamlEggDefinitionRepository(missing)
                .save(new EggDefinitionEnvelope(1, definition("a_egg", "a", Duration.ofHours(2))));

        assertTrue(Files.isRegularFile(missing.resolve("a_egg.yml")));
    }

    @Test
    void savingTwiceReplacesRatherThanDuplicating() throws IOException {
        YamlEggDefinitionRepository repository = new YamlEggDefinitionRepository(root);
        repository.save(new EggDefinitionEnvelope(1, definition("a_egg", "a", Duration.ofHours(1))));

        repository.save(new EggDefinitionEnvelope(1, definition("a_egg", "a", Duration.ofHours(5))));

        assertEquals(Duration.ofHours(5).toMillis(),
                repository.read("a_egg").orElseThrow().definition().baseActiveMillis());
        assertEquals(1, repository.list().size());
    }

    @Test
    void aTraversingIdIsRejectedBeforeAnythingIsWritten() {
        YamlEggDefinitionRepository repository = new YamlEggDefinitionRepository(root);

        // The ID becomes a filename, so a traversal attempt must not escape the catalog directory.
        assertThrows(RuntimeException.class, () -> repository.save(
                new EggDefinitionEnvelope(1, definition("../escaped", "a", Duration.ofHours(1)))));
    }

    @Test
    void aNullEnvelopeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new YamlEggDefinitionRepository(root).save(null));
    }

    private static EggDefinition definition(String eggId, String petId, Duration duration) {
        return new EggDefinition(
                eggId,
                PetTier.D,
                duration.toMillis(),
                List.of(new HatchCandidate(petId, 1.0, Map.of())),
                Map.of());
    }
}
