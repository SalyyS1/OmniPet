package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlEggDefinitionRepositoryTest {
    @TempDir
    Path temporary;

    @Test
    void listsDeterministicallyLoadsDefinitionsAndReturnsMissing() throws Exception {
        Path root = temporary.resolve("eggs");
        Files.createDirectories(root);
        Files.writeString(root.resolve("zeta.yml"), definition("zeta"));
        Files.writeString(root.resolve("Alpha.yml"), definition("Alpha"));
        YamlEggDefinitionRepository repository = new YamlEggDefinitionRepository(root);

        assertEquals(java.util.List.of("Alpha", "zeta"), repository.list());
        assertEquals(2, repository.loadAll().size());
        assertEquals(Optional.empty(), repository.read("missing"));
    }

    @Test
    void rejectsOversizedFiles() throws Exception {
        Path oversized = temporary.resolve("oversized");
        Files.createDirectories(oversized);
        Files.writeString(oversized.resolve("large.yml"), "#".repeat((int) YamlEggDefinitionRepository.MAX_FILE_BYTES + 1));
        assertThrows(java.io.IOException.class, () -> new YamlEggDefinitionRepository(oversized).read("large"));
    }

    @Test
    void rejectsCatalogsBeyondTheBoundedFileCount() throws Exception {
        Path root = temporary.resolve("too-many-eggs");
        Files.createDirectories(root);
        for (int index = 0; index <= 10_000; index++) {
            Files.createFile(root.resolve("egg_%05d.yml".formatted(index)));
        }

        assertThrows(java.io.IOException.class, () -> new YamlEggDefinitionRepository(root).list());
    }

    private static String definition(String id) {
        return """
                schemaVersion: 1
                eggId: %s
                tier: D
                baseDuration: 1h
                candidates:
                  - { definitionId: ember_fox, weight: 1 }
                """.formatted(id);
    }
}
