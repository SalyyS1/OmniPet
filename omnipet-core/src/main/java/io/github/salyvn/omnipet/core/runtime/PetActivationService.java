package io.github.salyvn.omnipet.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Reconciles persisted desired IDs to disposable renderer handles with compensation. */
public final class PetActivationService {
    private final InteractionIndex interactions;
    private final Map<UUID, LinkedHashMap<UUID, ActiveHandle>> owners = new LinkedHashMap<>();

    public PetActivationService(InteractionIndex interactions) {
        this.interactions = Objects.requireNonNull(interactions, "interaction index");
    }

    public synchronized PetActivationResult reconcile(
            UUID ownerId,
            List<RendererSpawnRequest> desired,
            ActivationRendererResolver renderers) {
        Objects.requireNonNull(ownerId, "activation owner ID");
        Objects.requireNonNull(renderers, "activation renderer resolver");
        LinkedHashMap<UUID, RendererSpawnRequest> desiredById = validateDesired(ownerId, desired);
        LinkedHashMap<UUID, ActiveHandle> active = owners.computeIfAbsent(ownerId, ignored -> new LinkedHashMap<>());
        List<UUID> spawned = new ArrayList<>();
        List<UUID> removed = new ArrayList<>();
        Map<UUID, String> failures = new LinkedHashMap<>();

        for (UUID petId : List.copyOf(active.keySet())) {
            RendererSpawnRequest request = desiredById.get(petId);
            ActiveHandle current = active.get(petId);
            if (request == null || request.rendererGeneration() != current.handle().rendererGeneration()) {
                if (remove(current, failures)) {
                    active.remove(petId);
                    removed.add(petId);
                }
            }
        }

        for (RendererSpawnRequest request : desiredById.values()) {
            ActiveHandle current = active.get(request.petInstanceId());
            if (current != null) {
                if (current.handle().rendererGeneration() != request.rendererGeneration()) continue;
                if (update(current, request, failures)) continue;
                active.remove(request.petInstanceId());
            }
            spawn(request, renderers, active, spawned, failures);
        }
        if (active.isEmpty()) owners.remove(ownerId);
        return result(active, spawned, removed, failures);
    }

    public synchronized PetActivationResult removeOwner(UUID ownerId) {
        Objects.requireNonNull(ownerId, "activation owner ID");
        LinkedHashMap<UUID, ActiveHandle> active = owners.get(ownerId);
        if (active == null) return new PetActivationResult(List.of(), List.of(), List.of(), Map.of());
        List<UUID> removed = new ArrayList<>();
        Map<UUID, String> failures = new LinkedHashMap<>();
        for (UUID petId : List.copyOf(active.keySet())) {
            if (remove(active.get(petId), failures)) {
                active.remove(petId);
                removed.add(petId);
            }
        }
        if (active.isEmpty()) owners.remove(ownerId);
        return result(active, List.of(), removed, failures);
    }

    public synchronized int activeCount() {
        return owners.values().stream().mapToInt(Map::size).sum();
    }

    public synchronized List<ActiveRendererSnapshot> activeRenderers(UUID ownerId) {
        Objects.requireNonNull(ownerId, "activation owner ID");
        LinkedHashMap<UUID, ActiveHandle> active = owners.get(ownerId);
        if (active == null) return List.of();
        return active.values().stream().map(entry -> snapshot(entry.handle())).toList();
    }

    public synchronized List<ActiveRendererSnapshot> activeRenderers() {
        return owners.values().stream()
                .flatMap(active -> active.values().stream())
                .map(entry -> snapshot(entry.handle()))
                .toList();
    }

    private void spawn(
            RendererSpawnRequest request,
            ActivationRendererResolver renderers,
            LinkedHashMap<UUID, ActiveHandle> active,
            List<UUID> spawned,
            Map<UUID, String> failures) {
        PetRendererPort renderer;
        try {
            renderer = Objects.requireNonNull(renderers.resolve(request), "renderer resolver returned null");
            RendererHealth health = Objects.requireNonNull(renderer.health(), "renderer health");
            if (!health.available()) {
                failures.put(request.petInstanceId(), "renderer unavailable: " + health.detail());
                return;
            }
            RendererHandle handle = Objects.requireNonNull(renderer.spawn(request), "renderer returned null handle");
            validateHandle(request, handle);
            try {
                interactions.register(handle);
            } catch (RuntimeException failure) {
                compensateSpawn(renderer, handle, failure);
                throw failure;
            }
            active.put(request.petInstanceId(), new ActiveHandle(request, renderer, handle));
            spawned.add(request.petInstanceId());
        } catch (RuntimeException | LinkageError failure) {
            failures.put(request.petInstanceId(), detail(failure));
        }
    }

    private boolean update(ActiveHandle current, RendererSpawnRequest request, Map<UUID, String> failures) {
        try {
            current.renderer().update(current.handle(), request.transform());
            if (!current.request().appearance().equals(request.appearance())) {
                current.renderer().updateAppearance(current.handle(), request.appearance());
            }
            current.request = request;
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (current.handle().removed()) {
                interactions.unregister(current.handle());
                return false;
            }
            failures.put(request.petInstanceId(), detail(failure));
            return true;
        }
    }

    private boolean remove(ActiveHandle current, Map<UUID, String> failures) {
        UUID petId = current.handle().petInstanceId();
        try {
            current.renderer().remove(current.handle());
            interactions.unregister(current.handle());
            return true;
        } catch (RuntimeException | LinkageError failure) {
            failures.put(petId, detail(failure));
            return false;
        }
    }

    private static LinkedHashMap<UUID, RendererSpawnRequest> validateDesired(
            UUID ownerId,
            List<RendererSpawnRequest> desired) {
        LinkedHashMap<UUID, RendererSpawnRequest> result = new LinkedHashMap<>();
        // Iterated, not copied: the map below is the detached value this method returns, so copying the
        // input first bought nothing and allocated a list per owner per tick.
        if (desired == null) return result;
        for (RendererSpawnRequest request : desired) {
            if (!ownerId.equals(request.ownerId())) throw new IllegalArgumentException("desired renderer owner differs");
            if (result.putIfAbsent(request.petInstanceId(), request) != null) {
                throw new IllegalArgumentException("duplicate desired pet instance ID");
            }
        }
        return result;
    }

    private static void validateHandle(RendererSpawnRequest request, RendererHandle handle) {
        if (!request.ownerId().equals(handle.ownerId())
                || !request.petInstanceId().equals(handle.petInstanceId())
                || request.rendererGeneration() != handle.rendererGeneration()) {
            throw new IllegalStateException("renderer handle identity differs from spawn request");
        }
        if (handle.removed()) throw new IllegalStateException("renderer returned an already removed handle");
    }

    private static void compensateSpawn(PetRendererPort renderer, RendererHandle handle, RuntimeException failure) {
        try {
            renderer.remove(handle);
        } catch (RuntimeException | LinkageError cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static PetActivationResult result(
            LinkedHashMap<UUID, ActiveHandle> active,
            List<UUID> spawned,
            List<UUID> removed,
            Map<UUID, String> failures) {
        return new PetActivationResult(
                List.copyOf(new LinkedHashSet<>(active.keySet())), spawned, removed, failures);
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static ActiveRendererSnapshot snapshot(RendererHandle handle) {
        return new ActiveRendererSnapshot(
                handle.ownerId(),
                handle.petInstanceId(),
                handle.rendererGeneration(),
                handle.capabilities(),
                handle.interactionEntityIds());
    }

    private static final class ActiveHandle {
        private RendererSpawnRequest request;
        private final PetRendererPort renderer;
        private final RendererHandle handle;

        private ActiveHandle(RendererSpawnRequest request, PetRendererPort renderer, RendererHandle handle) {
            this.request = request;
            this.renderer = renderer;
            this.handle = handle;
        }

        private RendererSpawnRequest request() { return request; }
        private PetRendererPort renderer() { return renderer; }
        private RendererHandle handle() { return handle; }
    }
}
