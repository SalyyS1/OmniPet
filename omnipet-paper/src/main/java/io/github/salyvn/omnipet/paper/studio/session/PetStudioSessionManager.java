package io.github.salyvn.omnipet.paper.studio.session;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Main-thread-only owner of detached admin studio sessions. */
public final class PetStudioSessionManager {
    private final StudioClock clock;
    private final StudioScheduler scheduler;
    private final StudioThreadGuard threadGuard;
    private final Duration timeout;
    private final Supplier<UUID> sessionIds;
    private final Consumer<SessionClosed> cleanup;
    private final Map<UUID, MutableSession> sessions = new HashMap<>();

    public PetStudioSessionManager(
            StudioClock clock,
            StudioScheduler scheduler,
            StudioThreadGuard threadGuard,
            Duration timeout,
            Consumer<SessionClosed> cleanup) {
        this(clock, scheduler, threadGuard, timeout, UUID::randomUUID, cleanup);
    }

    public PetStudioSessionManager(
            StudioClock clock,
            StudioScheduler scheduler,
            StudioThreadGuard threadGuard,
            Duration timeout,
            Supplier<UUID> sessionIds,
            Consumer<SessionClosed> cleanup) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.threadGuard = Objects.requireNonNull(threadGuard, "threadGuard");
        this.timeout = requirePositive(timeout);
        this.sessionIds = Objects.requireNonNull(sessionIds, "sessionIds");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    public PetStudioSession open(
            UUID viewerId,
            String definitionId,
            long baseRevision,
            String baseHash,
            long registryGeneration) {
        assertMainThread();
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(definitionId, "definitionId");
        if (baseRevision < 0) throw new IllegalArgumentException("base revision cannot be negative");
        if (registryGeneration < 0) throw new IllegalArgumentException("registry generation cannot be negative");

        MutableSession previous = sessions.remove(viewerId);
        if (previous != null) closeRemoved(previous, SessionCloseReason.SUPERSEDED);

        UUID sessionId = Objects.requireNonNull(sessionIds.get(), "session ID supplier returned null");
        Instant expiresAt = clock.now().plus(timeout);
        MutableSession session = new MutableSession(
                sessionId,
                viewerId,
                definitionId,
                baseRevision,
                baseHash,
                registryGeneration,
                expiresAt);
        sessions.put(viewerId, session);
        scheduleExpiry(session);
        return session.snapshot();
    }

    public Optional<PetStudioSession> get(UUID viewerId) {
        assertMainThread();
        MutableSession session = sessions.get(viewerId);
        if (expireIfDue(session)) return Optional.empty();
        return session == null ? Optional.empty() : Optional.of(session.snapshot());
    }

    public Optional<PetStudioSession> get(StudioViewToken token) {
        assertMainThread();
        MutableSession session = findCurrent(token);
        return session == null ? Optional.empty() : Optional.of(session.snapshot());
    }

    public StudioViewToken nextView(StudioViewToken token) {
        assertMainThread();
        MutableSession session = requireCurrent(token);
        session.viewNonce++;
        return session.token();
    }

    public boolean isCurrent(StudioViewToken token) {
        assertMainThread();
        return findCurrent(token) != null;
    }

    public boolean markDirty(StudioViewToken token) {
        assertMainThread();
        MutableSession session = findCurrent(token);
        if (session == null) return false;
        session.dirty = true;
        return true;
    }

    public boolean setPendingInput(StudioViewToken token, boolean pending) {
        assertMainThread();
        MutableSession session = findCurrent(token);
        if (session == null) return false;
        session.pendingInput = pending;
        return true;
    }

    public boolean touch(StudioViewToken token) {
        assertMainThread();
        MutableSession session = findCurrent(token);
        if (session == null) return false;
        session.expiryHandle.cancel();
        session.expiresAt = clock.now().plus(timeout);
        scheduleExpiry(session);
        return true;
    }

    public boolean close(UUID viewerId, SessionCloseReason reason) {
        assertMainThread();
        MutableSession session = sessions.remove(viewerId);
        if (session == null) return false;
        closeRemoved(session, reason);
        return true;
    }

    public boolean close(StudioViewToken token, SessionCloseReason reason) {
        assertMainThread();
        MutableSession session = findCurrent(token);
        if (session == null) return false;
        sessions.remove(session.viewerId);
        closeRemoved(session, reason);
        return true;
    }

    public void invalidateAll(SessionCloseReason reason) {
        assertMainThread();
        for (MutableSession session : sessions.values().toArray(MutableSession[]::new)) {
            sessions.remove(session.viewerId);
            closeRemoved(session, reason);
        }
    }

    public void onQuit(UUID viewerId) {
        close(viewerId, SessionCloseReason.QUIT);
    }

    public void onReload() {
        invalidateAll(SessionCloseReason.RELOAD);
    }

    public void onDisable() {
        invalidateAll(SessionCloseReason.DISABLE);
    }

    private void scheduleExpiry(MutableSession session) {
        Duration delay = Duration.between(clock.now(), session.expiresAt);
        long expiryNonce = ++session.expiryNonce;
        session.expiryHandle = scheduler.schedule(delay.isNegative() ? Duration.ZERO : delay,
                () -> expire(session.viewerId, session.sessionId, expiryNonce));
    }

    private void expire(UUID viewerId, UUID sessionId, long expiryNonce) {
        assertMainThread();
        MutableSession session = sessions.get(viewerId);
        if (session == null || !session.sessionId.equals(sessionId) || session.expiryNonce != expiryNonce) return;
        if (clock.now().isBefore(session.expiresAt)) {
            scheduleExpiry(session);
            return;
        }
        sessions.remove(viewerId);
        closeRemoved(session, SessionCloseReason.TIMEOUT);
    }

    private MutableSession requireCurrent(StudioViewToken token) {
        MutableSession session = findCurrent(token);
        if (session == null) throw new IllegalStateException("studio view token is stale");
        return session;
    }

    private MutableSession findCurrent(StudioViewToken token) {
        if (token == null) return null;
        MutableSession session = sessions.get(token.viewerId());
        if (expireIfDue(session)) return null;
        return session != null && session.matches(token) ? session : null;
    }

    private boolean expireIfDue(MutableSession session) {
        if (session == null || clock.now().isBefore(session.expiresAt)) return false;
        sessions.remove(session.viewerId);
        closeRemoved(session, SessionCloseReason.TIMEOUT);
        return true;
    }

    private void closeRemoved(MutableSession session, SessionCloseReason reason) {
        session.expiryHandle.cancel();
        cleanup.accept(new SessionClosed(session.sessionId, session.viewerId, Objects.requireNonNull(reason, "reason")));
    }

    private void assertMainThread() {
        threadGuard.assertMainThread();
    }

    private static Duration requirePositive(Duration value) {
        Objects.requireNonNull(value, "timeout");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        return value;
    }

}
