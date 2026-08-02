package io.github.salyvn.omnipet.core.release;

import java.io.IOException;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;

public final class ReleaseOutboxDeliveryService {
    private final PlayerStateRepository players;
    private final ReleaseOutboxStore outbox = new ReleaseOutboxStore();
    private final ReleaseOutboxStateUpdater updater;

    public ReleaseOutboxDeliveryService(PlayerStateRepository players) {
        if (players == null) throw new IllegalArgumentException("player repository is required");
        this.players = players;
        this.updater = new ReleaseOutboxStateUpdater(players, outbox);
    }

    public InternalOutboxResult recoverInternal(
            UUID playerId,
            UUID transactionId,
            InternalRewardDeliveryPort delivery) throws IOException {
        if (delivery == null) throw new IllegalArgumentException("internal reward delivery port is required");
        Lookup lookup = lookup(playerId, transactionId);
        if (lookup.status() != null) return internal(lookup.status(), lookup.entry(), lookup.detail());
        ReleaseOutboxEntry entry = lookup.entry();
        if (entry.internalState() == ReleaseOutboxEntry.InternalState.ACKNOWLEDGED) {
            return internal(InternalOutboxResult.Status.ACKNOWLEDGED, entry, "internal rewards already acknowledged");
        }

        InternalRewardDeliveryPort.Outcome outcome;
        try {
            outcome = delivery.deliver(playerId, transactionId, entry.rewards().internalRewards());
            if (outcome == null) outcome = InternalRewardDeliveryPort.Outcome.failed("delivery port returned no outcome");
        } catch (RuntimeException failure) {
            outcome = InternalRewardDeliveryPort.Outcome.failed(shortDetail(failure));
        }
        return switch (outcome.status()) {
            case CAPACITY_FULL -> internal(InternalOutboxResult.Status.PENDING_CAPACITY, entry, outcome.detail());
            case FAILED -> internal(InternalOutboxResult.Status.PENDING_FAILURE, entry, outcome.detail());
            case DELIVERED -> updater.acknowledgeInternal(playerId, transactionId, entry, outcome.detail());
        };
    }

    public ExternalReleaseResult attemptExternal(
            UUID playerId,
            UUID transactionId,
            ExternalRewardDeliveryPort delivery) throws IOException {
        if (delivery == null) throw new IllegalArgumentException("external reward delivery port is required");
        Lookup lookup = lookup(playerId, transactionId);
        if (lookup.status() != null) return external(lookup, null);
        ReleaseOutboxEntry entry = lookup.entry();
        ExternalReleaseResult terminal = terminalExternal(entry);
        if (terminal != null) return terminal;
        if (entry.externalState() == ReleaseOutboxEntry.ExternalState.ATTEMPTING) {
            return external(ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, entry,
                    "external attempt is already in flight or was interrupted; provider was not called again");
        }

        ReleaseOutboxEntry attempting = updater.transitionExternal(
                playerId, entry, ReleaseOutboxEntry.ExternalState.PENDING,
                ReleaseOutboxEntry.ExternalState.ATTEMPTING, "external delivery intent persisted");
        if (attempting == null) {
            return external(ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, entry,
                    "external state changed before delivery; provider was not called");
        }
        ExternalRewardDeliveryPort.Outcome outcome;
        try {
            outcome = delivery.deliver(playerId, transactionId, attempting.rewards().externalRewards());
            if (outcome == null) outcome = ExternalRewardDeliveryPort.Outcome.unknown("provider returned no outcome");
        } catch (RuntimeException failure) {
            outcome = ExternalRewardDeliveryPort.Outcome.unknown(shortDetail(failure));
        }
        ReleaseOutboxEntry.ExternalState finalState = switch (outcome.status()) {
            case PROVEN_SUCCESS -> ReleaseOutboxEntry.ExternalState.DELIVERED;
            case PROVEN_FAILURE -> ReleaseOutboxEntry.ExternalState.FAILED;
            case UNKNOWN_COMMIT -> ReleaseOutboxEntry.ExternalState.UNKNOWN_REQUIRES_RECONCILIATION;
        };
        try {
            ReleaseOutboxEntry saved = updater.transitionExternal(
                    playerId, attempting, ReleaseOutboxEntry.ExternalState.ATTEMPTING, finalState, outcome.evidence());
            if (saved == null) {
                return external(ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, attempting,
                        "external result raced with another state change; reconciliation required");
            }
            return terminalExternal(saved);
        } catch (IOException persistenceFailure) {
            return external(
                    ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION,
                    attempting,
                    "external result could not be persisted; reconciliation required");
        }
    }

    public ExternalReleaseResult recoverExternal(UUID playerId, UUID transactionId) throws IOException {
        Lookup lookup = lookup(playerId, transactionId);
        if (lookup.status() != null) return external(lookup, null);
        ReleaseOutboxEntry entry = lookup.entry();
        ExternalReleaseResult terminal = terminalExternal(entry);
        if (terminal != null) return terminal;
        if (entry.externalState() == ReleaseOutboxEntry.ExternalState.ATTEMPTING) {
            entry = updater.transitionExternal(
                    playerId, entry, ReleaseOutboxEntry.ExternalState.ATTEMPTING,
                    ReleaseOutboxEntry.ExternalState.UNKNOWN_REQUIRES_RECONCILIATION,
                    "external attempt was interrupted; provider commit is unknown");
            if (entry == null) return recoverExternal(playerId, transactionId);
            return terminalExternal(entry);
        }
        return external(ExternalReleaseResult.Status.FAILED, entry,
                "external reward is pending and requires an explicit delivery command");
    }

    public ExternalReleaseResult reconcileExternal(
            UUID playerId,
            UUID transactionId,
            ExternalReconciliation decision,
            String evidence) throws IOException {
        if (decision == null) throw new IllegalArgumentException("external reconciliation decision is required");
        Lookup lookup = lookup(playerId, transactionId);
        if (lookup.status() != null) return external(lookup, null);
        ReleaseOutboxEntry.ExternalState state = decision == ExternalReconciliation.CONFIRM_DELIVERED
                ? ReleaseOutboxEntry.ExternalState.DELIVERED
                : ReleaseOutboxEntry.ExternalState.FAILED;
        ReleaseOutboxEntry saved = updater.forceExternal(playerId, lookup.entry(), state, evidence);
        return terminalExternal(saved);
    }

    public List<ReleaseOutboxEntry> pending(UUID playerId, int limit) throws IOException {
        if (playerId == null) throw new IllegalArgumentException("release player ID is required");
        if (limit < 1 || limit > ReleaseOutboxStore.MAX_ENTRIES) {
            throw new IllegalArgumentException("release pending limit must be 1.." + ReleaseOutboxStore.MAX_ENTRIES);
        }
        return outbox.list(players.snapshot(playerId), ReleaseOutboxStore.MAX_ENTRIES).stream()
                .filter(entry -> entry.internalState() != ReleaseOutboxEntry.InternalState.ACKNOWLEDGED
                        || entry.externalState() == ReleaseOutboxEntry.ExternalState.PENDING
                        || entry.externalState() == ReleaseOutboxEntry.ExternalState.ATTEMPTING
                        || entry.externalState() == ReleaseOutboxEntry.ExternalState.UNKNOWN_REQUIRES_RECONCILIATION)
                .limit(limit)
                .toList();
    }

    private Lookup lookup(UUID playerId, UUID transactionId) throws IOException {
        if (playerId == null || transactionId == null) {
            throw new IllegalArgumentException("release outbox identities are required");
        }
        Optional<ReleaseOutboxEntry> found = outbox.find(players.snapshot(playerId), transactionId);
        if (found.isEmpty()) return new Lookup(InternalOutboxResult.Status.NOT_FOUND, null, "release outbox not found");
        if (!found.get().playerId().equals(playerId)) {
            return new Lookup(InternalOutboxResult.Status.IDENTITY_MISMATCH, found.get(),
                    "release outbox player identity does not match");
        }
        return new Lookup(null, found.get(), "");
    }

    private static ExternalReleaseResult terminalExternal(ReleaseOutboxEntry entry) {
        return switch (entry.externalState()) {
            case NOT_REQUIRED -> external(ExternalReleaseResult.Status.NOT_REQUIRED, entry, "no external rewards");
            case DELIVERED -> external(ExternalReleaseResult.Status.DELIVERED, entry, entry.externalEvidence());
            case FAILED -> external(ExternalReleaseResult.Status.FAILED, entry, entry.externalEvidence());
            case UNKNOWN_REQUIRES_RECONCILIATION -> external(
                    ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, entry, entry.externalEvidence());
            case PENDING, ATTEMPTING -> null;
        };
    }

    private static InternalOutboxResult internal(
            InternalOutboxResult.Status status, ReleaseOutboxEntry entry, String detail) {
        return new InternalOutboxResult(status, entry, detail);
    }

    private static ExternalReleaseResult external(Lookup lookup, ReleaseOutboxEntry ignored) {
        ExternalReleaseResult.Status status = lookup.status() == InternalOutboxResult.Status.IDENTITY_MISMATCH
                ? ExternalReleaseResult.Status.IDENTITY_MISMATCH
                : ExternalReleaseResult.Status.NOT_FOUND;
        return external(status, lookup.entry(), lookup.detail());
    }

    private static ExternalReleaseResult external(
            ExternalReleaseResult.Status status, ReleaseOutboxEntry entry, String detail) {
        return new ExternalReleaseResult(status, entry, detail);
    }

    private static String shortDetail(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message.length() <= 256 ? message : message.substring(0, 256);
    }

    private record Lookup(InternalOutboxResult.Status status, ReleaseOutboxEntry entry, String detail) {}
}
