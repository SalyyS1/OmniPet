package io.github.salyvn.omnipet.paper.runtime;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

/** Bridges completed repository work into the coordinator's immutable snapshot boundary. */
public final class PaperRuntimeSnapshotPublisher implements Consumer<PetStorageSnapshot> {
    private final BiConsumer<PetStorageSnapshot, RegistrySnapshot> target;
    private final RegistrySnapshotRepository registry;

    public PaperRuntimeSnapshotPublisher(
            PaperPetRuntimeCoordinator coordinator,
            RegistrySnapshotRepository registry) {
        this(Objects.requireNonNull(coordinator, "runtime coordinator")::acceptSnapshot, registry);
    }

    PaperRuntimeSnapshotPublisher(
            BiConsumer<PetStorageSnapshot, RegistrySnapshot> target,
            RegistrySnapshotRepository registry) {
        this.target = Objects.requireNonNull(target, "runtime snapshot target");
        this.registry = Objects.requireNonNull(registry, "runtime registry repository");
    }

    @Override
    public void accept(PetStorageSnapshot snapshot) {
        target.accept(Objects.requireNonNull(snapshot, "runtime storage snapshot"), registry.current());
    }
}
