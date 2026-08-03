package io.github.salyvn.omnipet.paper.management;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;
import io.github.salyvn.omnipet.core.release.InternalOutboxResult;
import io.github.salyvn.omnipet.core.release.ReleasePreview;

final class PetManagementControllerContext {
    final PetManagementRepositoryPort repository;
    final Supplier<UUID> ids;
    final ConcurrentHashMap<UUID, PetManagementSession> sessions = new ConcurrentHashMap<>();
    final ConcurrentHashMap<UUID, UUID> openRequests = new ConcurrentHashMap<>();
    final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> queuedRepositoryTasks = ConcurrentHashMap.newKeySet();
    private final PetManagementAuthorizationPort authorization;
    private final Executor repositoryExecutor;
    private volatile boolean closed;

    PetManagementControllerContext(
            PetManagementRepositoryPort repository,
            PetManagementAuthorizationPort authorization,
            Executor repositoryExecutor,
            Supplier<UUID> ids) {
        this.repository = Objects.requireNonNull(repository, "management repository");
        this.authorization = Objects.requireNonNull(authorization, "management authorization");
        this.repositoryExecutor = Objects.requireNonNull(repositoryExecutor, "management repository executor");
        this.ids = Objects.requireNonNull(ids, "management UUID supplier");
    }

    PetManagementOutcome begin(PetManagementSession session, PetManagementOperation operation) {
        if (session == null || !session.equals(sessions.get(session.viewerId()))) {
            return outcome(PetManagementOutcome.Status.STALE_SESSION, null, null, null, null, null,
                    "management session is stale");
        }
        PetManagementAuthorizationPort.Decision decision;
        try {
            decision = authorize(session.viewerId(), session.ownerId(), session.petId(), operation);
        } catch (RuntimeException failure) {
            return error(failure);
        }
        if (!decision.allowed()) {
            return outcome(PetManagementOutcome.Status.UNAUTHORIZED, session, null, null, null, null, decision.detail());
        }
        return inFlight.add(session.viewerId()) ? null : outcome(
                PetManagementOutcome.Status.BUSY, session, null, null, null, null,
                "another management action is already processing");
    }

    PetManagementAuthorizationPort.Decision authorize(
            UUID viewerId, UUID ownerId, UUID petId, PetManagementOperation operation) {
        Objects.requireNonNull(viewerId, "management viewer ID");
        Objects.requireNonNull(ownerId, "management owner ID");
        Objects.requireNonNull(petId, "management pet ID");
        return Objects.requireNonNull(authorization.authorize(viewerId, ownerId, petId, operation),
                "management authorization decision");
    }

    PetManagementSession advance(PetManagementSession current, PlayerState state) {
        PetManagementSession next = current.withRevision(state.revision());
        if (!sessions.replace(current.viewerId(), current, next)) {
            throw new StaleRevisionException(current.expectedRevision(), state.revision());
        }
        return next;
    }

    void invalidate(PetManagementSession session) {
        sessions.remove(session.viewerId(), session);
    }

    CompletionStage<PetManagementOutcome> submit(IoSupplier<PetManagementOutcome> task) {
        return submitValue(task).handle((outcome, failure) -> {
            if (failure == null) return outcome;
            Throwable cause = unwrap(failure);
            if (cause instanceof StaleRevisionException stale) {
                return outcome(PetManagementOutcome.Status.STALE_SESSION,
                        null, null, null, null, null, stale.getMessage());
            }
            return error(cause);
        });
    }

    <T> CompletionStage<T> submitValue(IoSupplier<T> task) {
        Objects.requireNonNull(task, "management repository task");
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closed) {
            future.completeExceptionally(new IllegalStateException("pet management is shutting down"));
            return future;
        }
        queuedRepositoryTasks.add(future);
        try {
            repositoryExecutor.execute(() -> {
                if (!queuedRepositoryTasks.remove(future)) return;
                try {
                    future.complete(task.get());
                } catch (IOException | RuntimeException failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            queuedRepositoryTasks.remove(future);
            future.completeExceptionally(failure);
        }
        return future;
    }

    void shutdown() {
        closed = true;
        IllegalStateException failure = new IllegalStateException("pet management is shutting down");
        for (CompletableFuture<?> task : queuedRepositoryTasks) task.completeExceptionally(failure);
        queuedRepositoryTasks.clear();
        openRequests.clear();
        sessions.clear();
        inFlight.clear();
    }

    PetManagementOutcome viewOutcome(
            PetManagementOutcome.Status status,
            PetManagementSession session,
            PlayerState state,
            ReleasePreview preview,
            String detail) {
        return outcome(status, session, PetManagementViewModel.create(session, state, preview),
                preview, preview == null ? null : preview.transactionId(), null, detail);
    }

    PetManagementViewModel safeView(PetManagementSession session, PlayerState state, ReleasePreview preview) {
        try {
            return state == null ? null : PetManagementViewModel.create(session, state, preview);
        } catch (RuntimeException stale) {
            // Deliberate: a view model that cannot be built from this state is stale, and null is the
            // caller's contract for "no view". The caller reports it as a stale session to the player.
            return null;
        }
    }

    static CompletionStage<PetManagementOutcome> completed(PetManagementOutcome.Status status, String detail) {
        return CompletableFuture.completedFuture(outcome(status, null, null, null, null, null, detail));
    }

    static PetManagementOutcome error(Throwable failure) {
        return outcome(PetManagementOutcome.Status.ERROR, null, null, null, null, null, shortDetail(failure));
    }

    static PetManagementOutcome outcome(
            PetManagementOutcome.Status status,
            PetManagementSession session,
            PetManagementViewModel view,
            ReleasePreview preview,
            UUID transactionId,
            InternalOutboxResult outbox,
            String detail) {
        return new PetManagementOutcome(status, session, view, preview, transactionId, outbox, detail);
    }

    static String shortDetail(Throwable failure) {
        Throwable cause = unwrap(failure);
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }

    static UUID requireId(UUID id) {
        return Objects.requireNonNull(id, "generated management UUID");
    }

    @FunctionalInterface
    interface IoSupplier<T> { T get() throws IOException; }
}
