package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetDefinition;

public final class FoundationRegistryLoader {
    public RegistrySnapshot load(PetDefinitionRepository definitions, RegistrySnapshotRepository snapshots)
            throws IOException {
        Map<String, PetDefinition> loaded = new LinkedHashMap<>();
        for (String id : definitions.list()) {
            PetDefinition definition = definitions.read(id)
                    .orElseThrow(() -> new IOException("definition disappeared during load: " + id))
                    .definition();
            PetDefinition duplicate = loaded.putIfAbsent(id, definition);
            if (duplicate != null) throw new IOException("duplicate definition ID: " + id);
        }
        RegistrySnapshot staged = snapshots.stage(snapshots.current().generation() + 1, loaded);
        return snapshots.replace(staged, ignored -> {});
    }
}
