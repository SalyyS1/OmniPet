package io.github.salyvn.omnipet.core.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import io.github.salyvn.omnipet.core.domain.PetDefinition;

public final class InMemoryRegistrySnapshotRepository implements RegistrySnapshotRepository {
    private RegistrySnapshot current = new RegistrySnapshot(0, Map.of());

    @Override
    public synchronized RegistrySnapshot current() {
        return current;
    }

    @Override
    public synchronized RegistrySnapshot stage(long generation, Map<String, PetDefinition> definitions) {
        if (generation <= current.generation()) throw new IllegalArgumentException("staged generation must advance");
        return new RegistrySnapshot(generation, definitions);
    }

    @Override
    public synchronized RegistrySnapshot replace(RegistrySnapshot staged, Consumer<RegistrySnapshot> activation) {
        Objects.requireNonNull(staged, "staged snapshot");
        Objects.requireNonNull(activation, "activation");
        if (staged.generation() <= current.generation()) throw new IllegalArgumentException("staged generation must advance");
        RegistrySnapshot previous = current;
        current = staged;
        try {
            activation.accept(staged);
            return staged;
        } catch (RuntimeException | Error failure) {
            current = previous;
            throw failure;
        }
    }
}
