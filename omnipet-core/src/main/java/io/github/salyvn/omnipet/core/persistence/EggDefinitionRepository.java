package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinitionEnvelope;

public interface EggDefinitionRepository {
    Optional<EggDefinitionEnvelope> read(String id) throws IOException;

    List<String> list() throws IOException;

    default Map<String, EggDefinition> loadAll() throws IOException {
        LinkedHashMap<String, EggDefinition> result = new LinkedHashMap<>();
        for (String id : list()) result.put(id, read(id).orElseThrow().definition());
        return Map.copyOf(result);
    }
}
