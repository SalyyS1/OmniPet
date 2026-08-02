package io.github.salyvn.omnipet.core.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Stable renderer target lookup. Actor authorization is intentionally separate. */
public final class InteractionIndex {
    private final Map<UUID, InteractionIdentity> targets = new HashMap<>();

    public synchronized void register(RendererHandle handle) {
        if (handle == null) throw new IllegalArgumentException("renderer handle is required");
        InteractionIdentity identity = new InteractionIdentity(
                handle.ownerId(), handle.petInstanceId(), handle.rendererGeneration());
        Set<UUID> entityIds = Set.copyOf(handle.interactionEntityIds());
        for (UUID entityId : entityIds) {
            InteractionIdentity current = targets.get(entityId);
            if (current != null && !current.equals(identity)) {
                throw new IllegalStateException("interaction entity is already owned by another renderer handle");
            }
        }
        entityIds.forEach(entityId -> targets.put(entityId, identity));
    }

    public synchronized Optional<InteractionIdentity> resolve(UUID entityId) {
        return Optional.ofNullable(targets.get(entityId));
    }

    public synchronized void unregister(RendererHandle handle) {
        if (handle == null) return;
        InteractionIdentity identity = new InteractionIdentity(
                handle.ownerId(), handle.petInstanceId(), handle.rendererGeneration());
        targets.entrySet().removeIf(entry -> entry.getValue().equals(identity));
    }

    public synchronized int removeOwner(UUID ownerId) {
        int before = targets.size();
        targets.entrySet().removeIf(entry -> entry.getValue().ownerId().equals(ownerId));
        return before - targets.size();
    }

    public synchronized int size() {
        return targets.size();
    }
}
