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
    private final LongSupplier nanoTime;
    /**
     * Every owner the loop may visit, rebuilt only when the membership changes.
     *
     * <p>This used to be rebuilt from scratch on every tick — a set, a copy, a list, and another copy,
     * all sized to the total owner count even though the budget only ever selects a few dozen. The
     * membership changes when a player joins, quits, or is queued for cleanup, which is far rarer than
     * twenty times a second.
     */
    private List<UUID> knownOwners = List.of();
    private boolean knownOwnersStale = true;
    /** Reused across ticks so selecting owners costs no allocation once the loop is warm. */
    private final List<UUID> selectedOwners = new ArrayList<>();
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
        // Retained as well as handed to the engine: the engine uses it as a movement clock, the tick
        // loop uses it to stop before it has spent more of the tick than the operator allowed.
        this.nanoTime = Objects.requireNonNull(nanoTime, "runtime monotonic clock");
        this.engine = new PaperRuntimeOwnerEngine(
                Objects.requireNonNull(activation, "pet activation service"),
                Objects.requireNonNull(movement, "movement controller"),
                Objects.requireNonNull(renderers, "renderer resolver"),
                Objects.requireNonNull(poses, "owner pose source"),
                nanoTime,
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
        if (snapshots.put(compiled.ownerId(), compiled) == null) invalidateKnownOwners();
    }

    public void tickNow() {
        requireMainThread();
        if (closed) return;
        long deadline = settings.timeBudgeted()
                ? nanoTime.getAsLong() + settings.maximumNanosPerTick()
                : 0;
        List<UUID> owners = nextOwners();
        for (int index = 0; index < owners.size(); index++) {
            UUID ownerId = owners.get(index);
            if (isCleanupPending(ownerId)) {
                if (engine.cleanup(ownerId, PaperRuntimeFailure.Stage.CLEANUP)) clearCleanup(ownerId);
            } else {
                PaperRuntimeOwnerSnapshot snapshot = snapshot(ownerId);
                if (snapshot != null && !engine.process(snapshot)) markCleanup(ownerId);
            }
            // Checked after the owner rather than before, so every tick makes progress on at least one
            // and a budget smaller than a single owner cannot stall the loop forever. Owners not reached
            // are not lost: the cursor already advanced past them, so the next tick starts there.
            if (deadline != 0 && index + 1 < owners.size() && nanoTime.getAsLong() >= deadline) {
                rewindCursor(owners.size() - index - 1);
                return;
            }
        }
    }

    public void ownerQuit(UUID ownerId) {
        requireMainThread();
        Objects.requireNonNull(ownerId, "runtime owner ID");
        synchronized (this) {
            snapshots.remove(ownerId);
            cleanupOwners.add(ownerId);
            invalidateKnownOwners();
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
            // Read before clearing: the cached list is what close() still has to walk.
            owners = allKnownOwnersLocked();
            snapshots.clear();
            cleanupOwners.clear();
            invalidateKnownOwners();
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
        selectedOwners.clear();
        if (candidates.isEmpty()) {
            ownerCursor = 0;
            return selectedOwners;
        }
        int count = Math.min(settings.maximumOwnersPerTick(), candidates.size());
        int start = Math.floorMod(ownerCursor, candidates.size());
        for (int index = 0; index < count; index++) {
            selectedOwners.add(candidates.get((start + index) % candidates.size()));
        }
        ownerCursor = (start + count) % candidates.size();
        // The live list, not a copy: it is cleared and refilled on the next call, and the only reader is
        // the tick loop that asked for it.
        return selectedOwners;
    }

    /**
     * Puts back owners the time budget stopped the loop reaching, so the next tick starts on them.
     *
     * <p>Without this a budget that regularly trips would skip the tail of every selection: the cursor
     * had already advanced past owners nobody visited, and the same ones would be skipped again.
     */
    private synchronized void rewindCursor(int unvisited) {
        int total = knownOwners.isEmpty() ? 0 : knownOwners.size();
        if (total == 0 || unvisited <= 0) return;
        ownerCursor = Math.floorMod(ownerCursor - unvisited, total);
    }

    private synchronized List<UUID> allKnownOwners() {
        return allKnownOwnersLocked();
    }

    /** The cached membership, rebuilt only after a join, quit, or cleanup queue change. */
    private List<UUID> allKnownOwnersLocked() {
        if (knownOwnersStale) {
            LinkedHashSet<UUID> owners = new LinkedHashSet<>(cleanupOwners);
            owners.addAll(snapshots.keySet());
            knownOwners = List.copyOf(owners);
            knownOwnersStale = false;
        }
        return knownOwners;
    }

    /** Called wherever {@code snapshots} or {@code cleanupOwners} gains or loses an entry. */
    private void invalidateKnownOwners() {
        knownOwnersStale = true;
    }

    private synchronized PaperRuntimeOwnerSnapshot snapshot(UUID ownerId) {
        return snapshots.get(ownerId);
    }

    private synchronized boolean isCleanupPending(UUID ownerId) {
        return cleanupOwners.contains(ownerId);
    }

    private synchronized void markCleanup(UUID ownerId) {
        // Only a first add changes membership; an owner already queued is still one entry.
        if (cleanupOwners.add(ownerId)) invalidateKnownOwners();
    }

    private synchronized void clearCleanup(UUID ownerId) {
        // Still known while a snapshot exists, so only drop the cache when this was the last reason.
        if (cleanupOwners.remove(ownerId) && !snapshots.containsKey(ownerId)) invalidateKnownOwners();
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
