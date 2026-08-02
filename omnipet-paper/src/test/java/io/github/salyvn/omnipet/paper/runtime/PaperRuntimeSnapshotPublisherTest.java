package io.github.salyvn.omnipet.paper.runtime;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.persistence.InMemoryRegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

class PaperRuntimeSnapshotPublisherTest {
    @Test
    void publishesCompletedStorageWithTheCurrentlyActivatedRegistry() {
        InMemoryRegistrySnapshotRepository registry = new InMemoryRegistrySnapshotRepository();
        RegistrySnapshot staged = registry.stage(1, java.util.Map.of());
        registry.replace(staged, ignored -> {});
        PetStorageSnapshot storage = RuntimeTestFixtures.storage(
                java.util.UUID.randomUUID(), 3, List.of(), List.of());
        AtomicReference<PetStorageSnapshot> receivedStorage = new AtomicReference<>();
        AtomicReference<RegistrySnapshot> receivedRegistry = new AtomicReference<>();
        PaperRuntimeSnapshotPublisher publisher = new PaperRuntimeSnapshotPublisher(
                (snapshot, definitions) -> {
                    receivedStorage.set(snapshot);
                    receivedRegistry.set(definitions);
                },
                registry);

        publisher.accept(storage);

        assertSame(storage, receivedStorage.get());
        assertSame(staged, receivedRegistry.get());
    }
}
