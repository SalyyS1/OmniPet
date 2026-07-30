package io.github.salyvn.omnipet.core.persistence;

import java.util.Map;
import java.util.function.Consumer;

import io.github.salyvn.omnipet.core.domain.PetDefinition;

public interface RegistrySnapshotRepository {
    RegistrySnapshot current();

    RegistrySnapshot stage(long generation, Map<String, PetDefinition> definitions);

    RegistrySnapshot replace(RegistrySnapshot staged, Consumer<RegistrySnapshot> activation);
}
