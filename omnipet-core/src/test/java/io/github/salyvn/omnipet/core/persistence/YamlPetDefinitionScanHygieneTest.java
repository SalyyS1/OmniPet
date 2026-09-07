package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A definitions directory is a place operators put files by hand, so it collects things that are not
 * definitions. None of those may cost the server its pet registry.
 */
class YamlPetDefinitionScanHygieneTest {
    @TempDir
    Path root;

    @Test
    void aStrayFilenameIsSkippedAndReportedRatherThanBrickingTheRegistry() throws Exception {
        writeDefinition("ember");
        writeDefinition("frost");
        // A name no ID can be made from: spaces are not valid in a stable ID.
        Files.writeString(root.resolve("my pet.yml"), "schemaVersion: 2\nid: whatever\n");

        List<String> problems = new ArrayList<>();
        YamlPetDefinitionRepository repository = new YamlPetDefinitionRepository(root, problems::add);

        assertEquals(List.of("ember", "frost"), repository.list(),
                "one unusable filename must not hide the definitions that are fine");
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("my pet.yml")),
                "the skipped file must be named so an operator can fix it, got: " + problems);
    }

    @Test
    void aStrayFileDoesNotBlockReadingAnUnrelatedDefinition() throws Exception {
        writeDefinition("ember");
        Files.writeString(root.resolve("notes.yml"), "just some notes\n");
        Files.writeString(root.resolve("BAD NAME.yaml"), "schemaVersion: 2\n");

        YamlPetDefinitionRepository repository = new YamlPetDefinitionRepository(root);

        assertTrue(repository.read("ember").isPresent());
    }

    @Test
    void anOversizeDefinitionFailsCleanlyInsteadOfExhaustingMemory() throws Exception {
        Path file = root.resolve("huge.yml");
        Files.writeString(file, "#" + "y".repeat(1024 * 1024));

        YamlPetDefinitionRepository repository = new YamlPetDefinitionRepository(root);

        IOException failure = assertThrows(IOException.class, () -> repository.read("huge"));
        assertTrue(failure.getMessage().contains("too large"), failure.getMessage());
    }

    private void writeDefinition(String id) throws IOException {
        Files.writeString(root.resolve(id + ".yml"), """
                schemaVersion: 2
                definitionId: %s
                revision: 1
                classification:
                  tier: D
                icon:
                  head:
                    source: MATERIAL
                    value: BONE
                display:
                  provider: HEAD
                """.formatted(id));
    }
}
