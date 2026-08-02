package io.github.salyvn.omnipet.core.incubation;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

public final class RepositoryHatchService {
    private final PlayerStateRepository repository;
    private final HatchService hatches;
    private final CopyOnWriteArrayList<HatchEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ThreadLocal<EventDispatch> eventDispatch = ThreadLocal.withInitial(EventDispatch::new);

    public RepositoryHatchService(PlayerStateRepository repository) {
        this(repository, new HatchService());
    }

    RepositoryHatchService(PlayerStateRepository repository, HatchService hatches) {
        this.repository = Objects.requireNonNull(repository, "player state repository");
        this.hatches = Objects.requireNonNull(hatches, "hatch service");
    }

    public boolean addListener(HatchEventListener listener) {
        return listeners.addIfAbsent(Objects.requireNonNull(listener, "hatch event listener"));
    }

    public boolean removeListener(HatchEventListener listener) {
        return listeners.remove(Objects.requireNonNull(listener, "hatch event listener"));
    }

    public PlayerState snapshot(UUID playerId) throws IOException {
        return repository.snapshot(requirePlayer(playerId));
    }

    public HatchResult start(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            EggDefinition egg,
            RegistrySnapshot registry,
            long seed,
            PetStorageLimits limits) throws IOException {
        return mutate(playerId, expectedRevision,
                state -> hatches.start(state, incubationId, egg, registry, seed, limits));
    }

    public HatchResult tick(UUID playerId, long expectedRevision, UUID incubationId, long elapsedMillis)
            throws IOException {
        return mutate(playerId, expectedRevision, state -> hatches.tick(state, incubationId, elapsedMillis));
    }

    public HatchResult reduce(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            long reductionMillis,
            UUID actionToken) throws IOException {
        return mutate(playerId, expectedRevision,
                state -> hatches.reduce(state, incubationId, reductionMillis, actionToken));
    }

    public HatchResult setRemaining(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            long remainingMillis,
            UUID actionToken) throws IOException {
        return mutate(playerId, expectedRevision,
                state -> hatches.setRemaining(state, incubationId, remainingMillis, actionToken));
    }

    public HatchResult complete(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            UUID actionToken) throws IOException {
        return mutate(playerId, expectedRevision, state -> hatches.complete(state, incubationId, actionToken));
    }

    public HatchResult cancel(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            UUID actionToken) throws IOException {
        return mutate(playerId, expectedRevision, state -> hatches.cancel(state, incubationId, actionToken));
    }

    public HatchResult claim(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            PetStorageLimits limits) throws IOException {
        return mutate(playerId, expectedRevision, state -> hatches.claim(state, incubationId, limits));
    }

    private HatchResult mutate(
            UUID playerId,
            long expectedRevision,
            Function<PlayerState, HatchResult> mutation) throws IOException {
        UUID target = requirePlayer(playerId);
        final HatchResult[] captured = new HatchResult[1];
        final PlayerState[] previous = new PlayerState[1];
        try {
            PlayerState saved = repository.withLocked(target, expectedRevision, current -> {
                HatchResult result = mutation.apply(current);
                captured[0] = result;
                if (!result.changedFrom(current)) throw new Unchanged(result);
                previous[0] = current;
                return result.state();
            });
            HatchResult result = captured[0];
            HatchResult persisted = new HatchResult(result.status(), saved, saved.incubation(), result.claimedPet());
            notifyListeners(event(target, previous[0].incubation(), saved.incubation(), persisted));
            return persisted;
        } catch (Unchanged unchanged) {
            return unchanged.result;
        }
    }

    private void notifyListeners(HatchEvent event) {
        EventDispatch dispatch = eventDispatch.get();
        dispatch.pending().add(event);
        if (dispatch.running()) return;
        dispatch.running(true);
        try {
            HatchEvent next;
            while ((next = dispatch.pending().poll()) != null) {
                for (HatchEventListener listener : listeners) {
                    try {
                        listener.onHatchEvent(next);
                    } catch (Throwable failure) {
                        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                        reportListenerFailure(failure);
                    }
                }
            }
        } finally {
            dispatch.pending().clear();
            dispatch.running(false);
            eventDispatch.remove();
        }
    }

    private static void reportListenerFailure(Throwable failure) {
        try {
            Thread thread = Thread.currentThread();
            thread.getUncaughtExceptionHandler().uncaughtException(thread, failure);
        } catch (Throwable ignored) {
            // Observer reporting must not change the result of a persisted mutation.
        }
    }

    private static HatchEvent event(
            UUID playerId,
            IncubationState previous,
            IncubationState saved,
            HatchResult result) {
        if (previous != null && saved != null && !previous.id().equals(saved.id())) previous = null;
        IncubationState identified = saved == null ? previous : saved;
        return new HatchEvent(
                playerId,
                identified.id(),
                previous == null ? null : previous.remainingActiveMillis(),
                saved == null ? null : saved.remainingActiveMillis(),
                previous == null ? null : previous.status(),
                saved == null ? null : saved.status(),
                result.status(),
                HatchEvent.DeliveryStage.STATE_PERSISTED,
                result.claimedPet());
    }

    private static UUID requirePlayer(UUID playerId) {
        return Objects.requireNonNull(playerId, "player id");
    }

    private static final class Unchanged extends RuntimeException {
        private final HatchResult result;

        private Unchanged(HatchResult result) {
            super(null, null, false, false);
            this.result = result;
        }
    }

    private static final class EventDispatch {
        private final Queue<HatchEvent> pending = new ArrayDeque<>();
        private boolean running;

        private Queue<HatchEvent> pending() { return pending; }
        private boolean running() { return running; }
        private void running(boolean value) { running = value; }
    }
}
