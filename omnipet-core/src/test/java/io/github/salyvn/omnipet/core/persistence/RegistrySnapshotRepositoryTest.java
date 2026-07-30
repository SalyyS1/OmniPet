package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RegistrySnapshotRepositoryTest {
    @Test
    void swapsOneGenerationAndRollsBackActivationFailure() {
        RegistrySnapshotRepository repository = new InMemoryRegistrySnapshotRepository();
        RegistrySnapshot staged = repository.stage(1, Map.of());

        repository.replace(staged, ignored -> {});
        assertEquals(1, repository.current().generation());

        RegistrySnapshot failing = repository.stage(2, Map.of());
        assertThrows(IllegalStateException.class,
                () -> repository.replace(failing, ignored -> { throw new IllegalStateException("activation failed"); }));
        assertEquals(1, repository.current().generation());
    }

    @Test
    void rollsBackDiskTransactionWhenActivationFails() {
        RegistrySnapshotRepository repository = new InMemoryRegistrySnapshotRepository();
        RegistrySnapshot staged = repository.stage(1, Map.of());
        StringBuilder files = new StringBuilder("old");

        assertThrows(IllegalStateException.class, () -> new RegistrySnapshotTransaction(repository).commit(
                staged,
                () -> files.replace(0, files.length(), "new"),
                () -> files.replace(0, files.length(), "old"),
                ignored -> { throw new IllegalStateException("activation failed"); }));

        assertEquals("old", files.toString());
        assertEquals(0, repository.current().generation());
    }
}
