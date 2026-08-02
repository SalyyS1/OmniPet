package io.github.salyvn.omnipet.paper.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

final class RuntimeTestFixtures {
    private RuntimeTestFixtures() {}

    static PetInstance pet(UUID id, String definitionId) {
        return new PetInstance(id, definitionId, 0, Map.of(), Map.of());
    }

    static PetDefinition definition(String id) {
        return definition(id, DisplayDefinition.Provider.HEAD, Map.of());
    }

    static PetDefinition definition(String id, DisplayDefinition.Provider provider, Map<String, Object> raw) {
        String model = provider == DisplayDefinition.Provider.MODELENGINE ? id + "_model" : null;
        return new PetDefinition(
                id,
                0,
                PetTier.D,
                new HeadIcon("TEXTURE_URL", "https://textures.minecraft.net/texture/" + id),
                new DisplayDefinition(provider, model),
                raw);
    }

    static PetStorageSnapshot storage(UUID owner, long revision, List<PetInstance> pets, List<UUID> desired) {
        return new PetStorageSnapshot(
                owner, revision, pets, desired, 100, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT);
    }

    static RegistrySnapshot registry(long generation, PetDefinition... definitions) {
        java.util.LinkedHashMap<String, PetDefinition> mapped = new java.util.LinkedHashMap<>();
        for (PetDefinition definition : definitions) mapped.put(definition.id(), definition);
        return new RegistrySnapshot(generation, mapped);
    }
}
