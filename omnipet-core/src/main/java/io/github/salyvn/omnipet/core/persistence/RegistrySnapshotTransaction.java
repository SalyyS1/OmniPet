package io.github.salyvn.omnipet.core.persistence;

import java.util.Objects;
import java.util.function.Consumer;

public final class RegistrySnapshotTransaction {
    private final RegistrySnapshotRepository repository;

    public RegistrySnapshotTransaction(RegistrySnapshotRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public RegistrySnapshot commit(
            RegistrySnapshot staged,
            Runnable commitFiles,
            Runnable rollbackFiles,
            Consumer<RegistrySnapshot> activation) {
        Objects.requireNonNull(commitFiles, "commitFiles");
        Objects.requireNonNull(rollbackFiles, "rollbackFiles");
        Objects.requireNonNull(activation, "activation");
        try {
            commitFiles.run();
            return repository.replace(staged, activation);
        } catch (RuntimeException | Error failure) {
            try {
                rollbackFiles.run();
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }
}
