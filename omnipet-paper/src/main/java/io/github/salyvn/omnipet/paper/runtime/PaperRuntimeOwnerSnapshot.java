package io.github.salyvn.omnipet.paper.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.runtime.MovementProfile;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

/** Compiled immutable runtime input; repository access happens before this boundary. */
record PaperRuntimeOwnerSnapshot(
        UUID ownerId,
        long storageRevision,
        long registryGeneration,
        PetStorageSnapshot storage,
        List<DesiredPet> desiredPets,
        List<InvalidPet> invalidPets) {

    static PaperRuntimeOwnerSnapshot compile(PetStorageSnapshot storage, RegistrySnapshot registry) {
        if (storage == null) throw new IllegalArgumentException("runtime storage snapshot is required");
        if (registry == null) throw new IllegalArgumentException("runtime registry snapshot is required");
        Map<UUID, PetInstance> owned = new LinkedHashMap<>();
        storage.pets().forEach(pet -> owned.put(pet.id(), pet));
        List<DesiredPet> desired = new ArrayList<>();
        List<InvalidPet> invalid = new ArrayList<>();
        for (UUID petId : storage.desiredActivePetIds()) {
            PetInstance instance = owned.get(petId);
            if (instance == null) {
                invalid.add(new InvalidPet(petId, "desired pet is not present in the immutable storage snapshot"));
                continue;
            }
            PetDefinition definition = registry.definitions().get(instance.definitionId());
            if (definition == null) {
                try {
                    desired.add(persistedFallback(instance));
                } catch (RuntimeException failure) {
                    invalid.add(new InvalidPet(petId,
                            "pet definition is missing and persisted appearance is invalid: " + detail(failure)));
                }
                continue;
            }
            try {
                PaperRuntimeBehaviorResolver.ResolvedBehavior behavior =
                        PaperRuntimeBehaviorResolver.resolve(definition.rawNode());
                RendererAppearance appearance = new RendererAppearance(
                        definition.display().provider().name(),
                        definition.display().model(),
                        definition.icon().source(),
                        definition.icon().value());
                desired.add(new DesiredPet(
                        instance, definition.id(), appearance, behavior.movement(), behavior.scale()));
            } catch (RuntimeException failure) {
                invalid.add(new InvalidPet(petId, detail(failure)));
            }
        }
        return new PaperRuntimeOwnerSnapshot(
                storage.playerId(), storage.revision(), registry.generation(), storage,
                List.copyOf(desired), List.copyOf(invalid));
    }

    record DesiredPet(
            PetInstance instance,
            String definitionId,
            RendererAppearance appearance,
            MovementProfile movement,
            double scale) {}

    record InvalidPet(UUID petInstanceId, String detail) {}

    private static DesiredPet persistedFallback(PetInstance instance) {
        Object rawAppearance = instance.rawComponents().get("appearance");
        if (!(rawAppearance instanceof Map<?, ?> appearance)) {
            throw new IllegalArgumentException("components.appearance is missing");
        }
        RendererAppearance resolved = new RendererAppearance(
                requiredText(appearance, "provider"),
                optionalText(appearance, "assetId"),
                requiredText(appearance, "fallbackHeadSource"),
                requiredText(appearance, "fallbackHeadValue"));
        PaperRuntimeBehaviorResolver.ResolvedBehavior behavior = PaperRuntimeBehaviorResolver.resolve(Map.of());
        return new DesiredPet(
                instance, instance.definitionId(), resolved, behavior.movement(), behavior.scale());
    }

    private static String requiredText(Map<?, ?> source, String key) {
        String value = optionalText(source, key);
        if (value.isBlank()) throw new IllegalArgumentException("components.appearance." + key + " is required");
        return value;
    }

    private static String optionalText(Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value == null) return "";
        if (value instanceof String text) return text.trim();
        throw new IllegalArgumentException("components.appearance." + key + " must be text");
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
