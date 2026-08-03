package io.github.salyvn.omnipet.paper.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.runtime.ActivationRendererResolver;
import io.github.salyvn.omnipet.core.runtime.InteractionIdentity;
import io.github.salyvn.omnipet.core.runtime.InteractionIndex;
import io.github.salyvn.omnipet.core.runtime.MovementController;
import io.github.salyvn.omnipet.core.runtime.PetActivationService;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

/** One-task Paper runtime that consumes only immutable snapshots during ticks. */
public final class PaperPetRuntimeCoordinator implements AutoCloseable {
    private final PaperRuntimeScheduler scheduler;
    private final PaperRuntimeSettings settings;
    private final PaperRuntimeFailureSink failures;
    private final PaperRuntimeOwnerEngine engine;
    private final InteractionIndex interactions;
    private final Map<UUID, PaperRuntimeOwnerSnapshot> snapshots = new LinkedHashMap<>();
    private final Set<UUID> cleanupOwners = new LinkedHashSet<>();
    private PaperRuntimeScheduler.ScheduledTask task;
    private int ownerCursor;
    private boolean closed;
    public PaperPetRuntimeCoordinator(
            PaperRuntimeScheduler scheduler,
            PaperRuntimeSettings settings,
            PetActivationService activation,
            MovementController movement,
            ActivationRendererResolver renderers,
            PaperRuntimeOwnerPoseSource poses,
            LongSupplier nanoTime,
            PaperRuntimeFailureSink failures) {
        this(scheduler, settings, activation, movement, renderers, poses, nanoTime, failures, null);
    }

    /**
     * @param interactions the same index handed to {@code activation}, so {@link #petFor(UUID)} can
     *     answer entity lookups. Null when the caller does not need reverse lookup, in which case
     *     {@code petFor} is always empty rather than throwing.
     */
    public PaperPetRuntimeCoordinator(
            PaperRuntimeScheduler scheduler,
            PaperRuntimeSettings settings,
            PetActivationService activation,
            MovementController movement,
            ActivationRendererResolver renderers,
            PaperRuntimeOwnerPoseSource poses,
            LongSupplier nanoTime,
            PaperRuntimeFailureSink failures,
            InteractionIndex interactions) {
        this.scheduler = Objects.requireNonNull(scheduler, "runtime scheduler");
        this.settings = Objects.requireNonNull(settings, "runtime settings");
        this.failures = Objects.requireNonNull(failures, "runtime failure sink");
        this.interactions = interactions;
        this.engine = new PaperRuntimeOwnerEngine(
                Objects.requireNonNull(activation, "pet activation service"),
                Objects.requireNonNull(movement, "movement controller"),
                Objects.requireNonNull(renderers, "renderer resolver"),
                Objects.requireNonNull(poses, "owner pose source"),
                Objects.requireNonNull(nanoTime, "runtime monotonic clock"),
                failures,
                settings.maximumPetsPerOwner());
    }

    public synchronized void start() {
        requireMainThread();
        if (closed) throw new IllegalStateException("pet runtime coordinator is closed");
        if (task != null && !task.cancelled()) return;
        task = Objects.requireNonNull(
                scheduler.scheduleRepeating(this::scheduledTick,
                        settings.initialDelayTicks(), settings.periodTicks()),
                "runtime scheduler returned null task");
    }

    /** Accepts repository results outside the tick; the compiled value is detached and immutable. */
    public synchronized void acceptSnapshot(PetStorageSnapshot storage, RegistrySnapshot registry) {
        if (closed) throw new IllegalStateException("pet runtime coordinator is closed");
        PaperRuntimeOwnerSnapshot compiled = PaperRuntimeOwnerSnapshot.compile(storage, registry);
        snapshots.put(compiled.ownerId(), compiled);
    }

    public void tickNow() {
        requireMainThread();
        if (closed) return;
        List<UUID> owners = nextOwners();
        for (UUID ownerId : owners) {
            if (isCleanupPending(ownerId)) {
                if (engine.cleanup(ownerId, PaperRuntimeFailure.Stage.CLEANUP)) clearCleanup(ownerId);
                continue;
            }
            PaperRuntimeOwnerSnapshot snapshot = snapshot(ownerId);
            if (snapshot != null && !engine.process(snapshot)) markCleanup(ownerId);
        }
    }

    public void ownerQuit(UUID ownerId) {
        requireMainThread();
        Objects.requireNonNull(ownerId, "runtime owner ID");
        synchronized (this) {
            snapshots.remove(ownerId);
            cleanupOwners.add(ownerId);
        }
        if (engine.cleanup(ownerId, PaperRuntimeFailure.Stage.CLEANUP)) clearCleanup(ownerId);
    }

    public void ownerWorldChanged(UUID ownerId) {
        requireMainThread();
        Objects.requireNonNull(ownerId, "runtime owner ID");
        markCleanup(ownerId);
        if (engine.cleanup(ownerId, PaperRuntimeFailure.Stage.CLEANUP)) clearCleanup(ownerId);
    }

    public void reload() {
        requireMainThread();
        for (UUID ownerId : allKnownOwners()) {
            markCleanup(ownerId);
            if (engine.cleanup(ownerId, PaperRuntimeFailure.Stage.CLEANUP)) clearCleanup(ownerId);
        }
    }

    public void reload(RegistrySnapshot registry) {
        requireMainThread();
        Objects.requireNonNull(registry, "runtime registry snapshot");
        synchronized (this) {
            snapshots.replaceAll((ownerId, snapshot) ->
                    PaperRuntimeOwnerSnapshot.compile(snapshot.storage(), registry));
        }
        reload();
    }

    public int activePetCount() {
        return engine.activeCount();
    }

    /**
     * Which pet, if any, a clicked entity belongs to.
     *
     * <p>Pure delegation to {@link InteractionIndex#resolve} plus a staleness check — deliberately no
     * state of its own, so there is no second copy that could disagree with the index the activation
     * service maintains. {@code resolve} is already {@code synchronized}, so this is not synchronized
     * again.
     *
     * <p>An identity whose renderer generation no longer matches a live renderer is a leftover from a
     * re-render and resolves to empty, so a click on it is treated as a click on someone else's entity.
     */
    public Optional<InteractionIdentity> petFor(UUID entityId) {
        if (interactions == null || entityId == null) return Optional.empty();
        return interactions.resolve(entityId).filter(this::isLive);
    }

    private boolean isLive(InteractionIdentity identity) {
        return engine.activeRenderers(identity.ownerId()).stream()
                .anyMatch(active -> active.petInstanceId().equals(identity.petInstanceId())
                        && active.rendererGeneration() == identity.rendererGeneration());
    }

    public synchronized boolean started() {
        return task != null && !task.cancelled();
    }

    @Override
    public void close() {
        requireMainThread();
        List<UUID> owners;
        synchronized (this) {
            if (closed) return;
            closed = true;
            if (task != null && !task.cancelled()) task.cancel();
            owners = allKnownOwnersLocked();
            snapshots.clear();
            cleanupOwners.clear();
        }
        for (UUID ownerId : owners) engine.cleanup(ownerId, PaperRuntimeFailure.Stage.CLEANUP);
    }

    public void disable() {
        close();
    }

    private void scheduledTick() {
        try {
            tickNow();
        } catch (RuntimeException | LinkageError failure) {
            String detail = failure.getMessage();
            report(new PaperRuntimeFailure(null, null, PaperRuntimeFailure.Stage.COORDINATOR,
                    detail == null || detail.isBlank() ? failure.getClass().getSimpleName() : detail));
        }
    }

    private synchronized List<UUID> nextOwners() {
        List<UUID> candidates = allKnownOwnersLocked();
        if (candidates.isEmpty()) {
            ownerCursor = 0;
            return List.of();
        }
        int count = Math.min(settings.maximumOwnersPerTick(), candidates.size());
        int start = Math.floorMod(ownerCursor, candidates.size());
        List<UUID> selected = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            selected.add(candidates.get((start + index) % candidates.size()));
        }
        ownerCursor = (start + count) % candidates.size();
        return List.copyOf(selected);
    }

    private synchronized List<UUID> allKnownOwners() {
        return allKnownOwnersLocked();
    }

    private List<UUID> allKnownOwnersLocked() {
        LinkedHashSet<UUID> owners = new LinkedHashSet<>(cleanupOwners);
        owners.addAll(snapshots.keySet());
        return List.copyOf(owners);
    }

    private synchronized PaperRuntimeOwnerSnapshot snapshot(UUID ownerId) {
        return snapshots.get(ownerId);
    }

    private synchronized boolean isCleanupPending(UUID ownerId) {
        return cleanupOwners.contains(ownerId);
    }

    private synchronized void markCleanup(UUID ownerId) {
        cleanupOwners.add(ownerId);
    }

    private synchronized void clearCleanup(UUID ownerId) {
        cleanupOwners.remove(ownerId);
    }

    private void requireMainThread() {
        if (!scheduler.isMainThread()) {
            throw new IllegalStateException("pet runtime Bukkit work must run on the Paper main thread");
        }
    }

    private void report(PaperRuntimeFailure failure) {
        try {
            failures.accept(failure);
        } catch (RuntimeException | LinkageError ignored) {
            // Failure observers are isolated from the coordinator task.
        }
    }
}
