package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

        PlayerState saved = repository.withLocked(playerId, 0, current -> current.withStorage(
                current.pets(), 8, current.activeSlotCount(), current.desiredActivePetIds(), current.slotEntitlements()));

        assertEquals(1, saved.revision());
        assertEquals(8, repository.snapshot(playerId).vaultCapacity());
        assertThrows(StaleRevisionException.class, () -> repository.withLocked(playerId, 0, current -> current));
    }

    @Test
    void sharesPlayerLocksAcrossRepositoryInstancesForTheSameRoot() throws Exception {
        UUID playerId = UUID.randomUUID();
        Path root = temporary.resolve("shared-players");
        PlayerStateRepository first = new FilePlayerStateRepository(root);
        PlayerStateRepository restarted = new FilePlayerStateRepository(root);
        CountDownLatch mutationEntered = new CountDownLatch(1);
        CountDownLatch allowMutation = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var mutation = executor.submit(() -> first.withLocked(playerId, 0, current -> {
                mutationEntered.countDown();
                try {
                    if (!allowMutation.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test mutation release timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test mutation interrupted", interrupted);
                }
                return current.withStorage(
                        current.pets(), 9, current.activeSlotCount(),
                        current.desiredActivePetIds(), current.slotEntitlements());
            }));
            assertTrue(mutationEntered.await(5, TimeUnit.SECONDS));
            Path lockFile = root.resolve(".locks").resolve(playerId + ".lck");
            try (FileChannel externalChannel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
                assertThrows(OverlappingFileLockException.class, externalChannel::tryLock);
            }

            var snapshot = executor.submit(() -> restarted.snapshot(playerId));
            assertFalse(snapshot.isDone());
            allowMutation.countDown();

            assertEquals(1, mutation.get(5, TimeUnit.SECONDS).revision());
            assertEquals(9, snapshot.get(5, TimeUnit.SECONDS).vaultCapacity());
        }
    }

    @Test
    void mutationCannotChangeUuidOrRevision() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = new FilePlayerStateRepository(temporary.resolve("players"));

        assertThrows(IllegalArgumentException.class, () -> repository.withLocked(playerId, 0,
                current -> current.withRevision(2)));
        assertThrows(IllegalArgumentException.class, () -> repository.withLocked(playerId, 0,
                current -> new PlayerState(
                        UUID.randomUUID(),
                        current.revision(),
                        current.pets(),
                        current.vaultCapacity(),
                        current.activeSlotCount(),
                        current.desiredActivePetIds(),
                        current.slotEntitlements(),
                        current.legacyCurrentEgg(),
                        current.extensions())));
    }

    @Test
    void migratesLegacyStateOnFirstSnapshotAndKeepsBackup() throws Exception {
        UUID playerId = UUID.randomUUID();
        Path root = temporary.resolve("players");
        Files.createDirectories(root);
        Path file = root.resolve(playerId + ".yml");
        Files.writeString(file, "uuid: " + playerId + "\npets: []\ncapacity: 4\n");

        FilePlayerStateRepository repository = new FilePlayerStateRepository(root);
        assertEquals(4, repository.snapshot(playerId).vaultCapacity());
        assertTrue(Files.readString(file).contains("schemaVersion: 3"));
        assertTrue(Files.exists(AtomicFileStore.backupPath(file)));

        byte[] rewritten = Files.readAllBytes(file);
        assertEquals(4, repository.snapshot(playerId).vaultCapacity());
        assertTrue(java.util.Arrays.equals(rewritten, Files.readAllBytes(file)));
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

    @Test
    void newProfilesUseSafeSchemaThreeDefaults() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = new FilePlayerStateRepository(temporary.resolve("players"));

        PlayerState state = repository.snapshot(playerId);

        assertEquals(0, state.vaultCapacity());
        assertEquals(1, state.activeSlotCount());
        assertEquals(List.of(), state.desiredActivePetIds());
        assertEquals(List.of(), state.slotEntitlements());
    }
}
