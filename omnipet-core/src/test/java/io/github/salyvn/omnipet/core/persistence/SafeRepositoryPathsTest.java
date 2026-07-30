package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeRepositoryPathsTest {
    @TempDir
    Path temporary;

    @Test
    void resolvesOnlyContainedWindowsSafeTargets() throws Exception {
        SafeRepositoryPaths paths = new SafeRepositoryPaths(temporary.resolve("pets"));

        assertEquals("example_pet.yml", paths.resolveId("example_pet", ".yml").getFileName().toString());
        assertThrows(IllegalArgumentException.class, () -> paths.resolveId("../escape", ".yml"));
        assertThrows(IllegalArgumentException.class, () -> paths.resolveId("NUL", ".yml"));
        assertThrows(IllegalArgumentException.class, () -> paths.requireSafe(temporary.resolve("outside.yml")));
    }

    @Test
    void rejectsCaseFoldedDuplicateIds() {
        SafeRepositoryPaths paths = new SafeRepositoryPaths(temporary.resolve("pets"));
        assertThrows(IllegalArgumentException.class, () -> paths.validateCaseUnique(List.of("Nahara", "nahara")));
    }

    @Test
    void archiveAndQuarantineTargetsStayContained() throws Exception {
        SafeRepositoryPaths paths = new SafeRepositoryPaths(temporary.resolve("pets"));
        assertTrue(paths.resolveArchive("pet", "token").startsWith(paths.root()));
        assertTrue(paths.resolveQuarantine("player.yml").startsWith(paths.root()));
        assertThrows(IllegalArgumentException.class, () -> paths.resolveArchive("pet", "../escape"));
        assertThrows(IllegalArgumentException.class, () -> paths.resolveQuarantine("../escape.yml"));
    }
}
