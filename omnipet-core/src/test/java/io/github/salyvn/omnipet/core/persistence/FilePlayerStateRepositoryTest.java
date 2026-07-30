package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PlayerState;

class FilePlayerStateRepositoryTest {
    @TempDir
    Path temporary;

    @Test
    void serializesMutationByUuidAndRejectsStaleRevision() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = new FilePlayerStateRepository(temporary.resolve("players"));

        PlayerState saved = repository.withLocked(playerId, 0, current -> new PlayerState(
                current.playerId(), current.revision(), current.pets(), current.legacyCurrentPetIndex(),
                current.legacyCurrentEgg(), 8, current.extensions()));

        assertEquals(1, saved.revision());
        assertEquals(8, repository.snapshot(playerId).legacyCapacity());
        assertThrows(StaleRevisionException.class, () -> repository.withLocked(playerId, 0, current -> current));
    }

    @Test
    void mutationCannotChangeUuidOrRevision() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = new FilePlayerStateRepository(temporary.resolve("players"));

        assertThrows(IllegalArgumentException.class, () -> repository.withLocked(playerId, 0,
                current -> current.withRevision(2)));
        assertThrows(IllegalArgumentException.class, () -> repository.withLocked(playerId, 0,
                current -> new PlayerState(UUID.randomUUID(), current.revision(), current.pets(), null,
                        current.legacyCurrentEgg(), null, current.extensions())));
    }

    @Test
    void migratesLegacyStateOnFirstSnapshotAndKeepsBackup() throws Exception {
        UUID playerId = UUID.randomUUID();
        Path root = temporary.resolve("players");
        Files.createDirectories(root);
        Path file = root.resolve(playerId + ".yml");
        Files.writeString(file, "uuid: " + playerId + "\npets: []\ncapacity: 4\n");

        FilePlayerStateRepository repository = new FilePlayerStateRepository(root);
        assertEquals(4, repository.snapshot(playerId).legacyCapacity());
        assertTrue(Files.readString(file).contains("schemaVersion: 2"));
        assertTrue(Files.exists(AtomicFileStore.backupPath(file)));
    }

    @Test
    void quarantinesInvalidStateInsteadOfBlankSaving() throws Exception {
        UUID playerId = UUID.randomUUID();
        Path root = temporary.resolve("players");
        Files.createDirectories(root);
        Path file = root.resolve(playerId + ".yml");
        Files.writeString(file, "uuid: not-a-uuid\npets: []\n");

        FilePlayerStateRepository repository = new FilePlayerStateRepository(root);
        assertThrows(java.io.IOException.class, () -> repository.snapshot(playerId));
        assertFalse(Files.exists(file));
        try (var files = Files.list(root.resolve("quarantine"))) {
            assertTrue(files.findAny().isPresent());
        }

        assertThrows(java.io.IOException.class, () -> repository.snapshot(playerId));
        AtomicBoolean mutationCalled = new AtomicBoolean();
        assertThrows(java.io.IOException.class, () -> repository.withLocked(playerId, 0, current -> {
            mutationCalled.set(true);
            return current;
        }));
        assertFalse(mutationCalled.get());

        FilePlayerStateRepository restartedRepository = new FilePlayerStateRepository(root);
        assertThrows(java.io.IOException.class, () -> restartedRepository.snapshot(playerId));

        Files.writeString(file, "uuid: " + playerId + "\npets: []\n");
        assertEquals(playerId, restartedRepository.snapshot(playerId).playerId());
    }
}
