package io.github.salyvn.omnipet.core.release;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;

final class ReleaseOutboxStateUpdater {
    private static final int MAX_WRITE_ATTEMPTS = 4;

    private final PlayerStateRepository players;
    private final ReleaseOutboxStore outbox;

    ReleaseOutboxStateUpdater(PlayerStateRepository players, ReleaseOutboxStore outbox) {
        this.players = players;
        this.outbox = outbox;
    }

    InternalOutboxResult acknowledgeInternal(
            UUID playerId,
            UUID transactionId,
            ReleaseOutboxEntry delivered,
            String detail) {
        try {
            for (int attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
                PlayerState state = players.snapshot(playerId);
                Optional<ReleaseOutboxEntry> currentOptional = outbox.find(state, transactionId);
                if (currentOptional.isEmpty()) {
                    return internal(InternalOutboxResult.Status.NOT_FOUND, null, "release outbox entry disappeared");
                }
                ReleaseOutboxEntry current = currentOptional.get();
                if (!sameIdentity(current, delivered)) {
                    return internal(InternalOutboxResult.Status.IDENTITY_MISMATCH, current,
                            "release outbox identity changed before acknowledgement");
                }
                if (current.internalState() == ReleaseOutboxEntry.InternalState.ACKNOWLEDGED) {
                    return internal(InternalOutboxResult.Status.ACKNOWLEDGED, current, detail);
                }
                try {
                    players.withLocked(playerId, state.revision(), locked -> outbox.update(
                            locked, current.withInternalState(ReleaseOutboxEntry.InternalState.ACKNOWLEDGED)));
                    return internal(InternalOutboxResult.Status.ACKNOWLEDGED,
                            current.withInternalState(ReleaseOutboxEntry.InternalState.ACKNOWLEDGED), detail);
                } catch (StaleRevisionException retry) {
                    // Retry only the local acknowledgement, never the reward delivery.
                }
            }
        } catch (IOException persistenceFailure) {
            try {
                Optional<ReleaseOutboxEntry> verified = outbox.find(players.snapshot(playerId), transactionId);
                if (verified.isPresent()
                        && verified.get().internalState() == ReleaseOutboxEntry.InternalState.ACKNOWLEDGED) {
                    return internal(InternalOutboxResult.Status.ACKNOWLEDGED, verified.get(), detail);
                }
            } catch (IOException ignored) {
                // A later recovery uses the same transaction idempotency key.
            }
            return internal(InternalOutboxResult.Status.ACK_PERSIST_FAILED, delivered,
                    "internal delivery succeeded but acknowledgement persistence failed");
        }
        return internal(InternalOutboxResult.Status.ACK_PERSIST_FAILED, delivered,
                "internal acknowledgement remained contended");
    }

    ReleaseOutboxEntry transitionExternal(
            UUID playerId,
            ReleaseOutboxEntry expected,
            ReleaseOutboxEntry.ExternalState requiredState,
            ReleaseOutboxEntry.ExternalState nextState,
            String evidence) throws IOException {
        for (int attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
            PlayerState current = players.snapshot(playerId);
            ReleaseOutboxEntry entry = outbox.find(current, expected.transactionId())
                    .orElseThrow(() -> new IOException("release outbox entry disappeared"));
            if (!sameIdentity(entry, expected)) throw new IOException("release outbox identity changed");
            if (entry.externalState() != requiredState) return null;
            ReleaseOutboxEntry updated = entry.withExternalState(nextState, evidence);
            try {
                players.withLocked(playerId, current.revision(), locked -> outbox.update(locked, updated));
                return updated;
            } catch (StaleRevisionException retry) {
                // Retry the local state transition only.
            }
        }
        throw new IOException("external release state remained contended");
    }

    ReleaseOutboxEntry forceExternal(
            UUID playerId,
            ReleaseOutboxEntry expected,
            ReleaseOutboxEntry.ExternalState state,
            String evidence) throws IOException {
        for (int attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
            PlayerState current = players.snapshot(playerId);
            ReleaseOutboxEntry entry = outbox.find(current, expected.transactionId())
                    .orElseThrow(() -> new IOException("release outbox entry disappeared"));
            if (!sameIdentity(entry, expected)) throw new IOException("release outbox identity changed");
            ReleaseOutboxEntry updated = entry.withExternalState(state, evidence);
            try {
                players.withLocked(playerId, current.revision(), locked -> outbox.update(locked, updated));
                return updated;
            } catch (StaleRevisionException retry) {
                // Explicit reconciliation may retry only the local state write.
            }
        }
        throw new IOException("external reconciliation state remained contended");
    }

    static boolean sameIdentity(ReleaseOutboxEntry left, ReleaseOutboxEntry right) {
        return left.transactionId().equals(right.transactionId())
                && left.playerId().equals(right.playerId())
                && left.petId().equals(right.petId())
                && left.petFingerprint().equals(right.petFingerprint())
                && left.previewToken().equals(right.previewToken());
    }

    private static InternalOutboxResult internal(
            InternalOutboxResult.Status status, ReleaseOutboxEntry entry, String detail) {
        return new InternalOutboxResult(status, entry, detail);
    }
}
