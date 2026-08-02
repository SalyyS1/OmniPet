package io.github.salyvn.omnipet.core.release;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;

public final class ReleaseService {
    private final PlayerStateRepository players;
    private final ReleaseRewardPolicy rewards;
    private final ReleaseLockPolicy locks;
    private final ReleaseOutboxStore outbox = new ReleaseOutboxStore();

    public ReleaseService(PlayerStateRepository players, ReleaseRewardPolicy rewards) {
        this(players, rewards, ReleaseLockPolicy.standard());
    }

    public ReleaseService(
            PlayerStateRepository players,
            ReleaseRewardPolicy rewards,
            ReleaseLockPolicy locks) {
        if (players == null || rewards == null || locks == null) {
            throw new IllegalArgumentException("release service dependencies are required");
        }
        this.players = players;
        this.rewards = rewards;
        this.locks = locks;
    }

    public ReleasePreviewResult preview(UUID transactionId, UUID playerId, UUID petId) throws IOException {
        if (transactionId == null || playerId == null || petId == null) {
            throw new IllegalArgumentException("release preview identities are required");
        }
        PlayerState state = players.snapshot(playerId);
        if (!outbox.isUsable(state)) {
            return previewResult(ReleasePreviewResult.Status.TRANSACTION_CONFLICT, null, "release outbox is invalid");
        }
        if (outbox.find(state, transactionId).isPresent()) {
            return previewResult(
                    ReleasePreviewResult.Status.TRANSACTION_CONFLICT, null, "release transaction UUID already exists");
        }
        Optional<PetInstance> target = findPet(state, petId);
        if (target.isEmpty()) {
            return previewResult(ReleasePreviewResult.Status.PET_NOT_FOUND, null, "pet UUID is not owned");
        }
        if (locks.isLocked(target.get())) {
            return previewResult(ReleasePreviewResult.Status.LOCKED, null, "locked pets cannot be released");
        }
        ReleaseRewardBundle frozen;
        try {
            frozen = rewards.calculate(target.get());
            if (frozen == null) throw new IllegalArgumentException("release reward policy returned null");
            frozen = new ReleaseRewardBundle(frozen.internalRewards(), frozen.externalRewards());
        } catch (RuntimeException invalid) {
            return previewResult(ReleasePreviewResult.Status.INVALID_REWARDS, null, shortDetail(invalid));
        }
        String fingerprint = ReleaseIdentity.petFingerprint(target.get());
        String token = ReleaseIdentity.confirmationToken(
                transactionId, playerId, petId, state.revision(), fingerprint, frozen);
        ReleasePreview preview = new ReleasePreview(
                transactionId, playerId, petId, state.revision(), fingerprint, frozen, token);
        return previewResult(ReleasePreviewResult.Status.READY, preview, "release preview frozen");
    }

    public ReleaseResult release(ReleasePreview preview) throws IOException {
        if (preview == null || !ReleaseIdentity.valid(preview)) {
            return result(ReleaseResult.Status.INVALID_CONFIRMATION, null, null, "release preview token is invalid");
        }
        PlayerState observed = players.snapshot(preview.playerId());
        ReleaseResult existing = existingResult(observed, preview);
        if (existing != null) return existing;
        if (!outbox.isUsable(observed)) {
            return result(ReleaseResult.Status.OUTBOX_INVALID, observed, null, "release outbox is invalid");
        }
        if (observed.revision() != preview.expectedRevision()) {
            return result(ReleaseResult.Status.STALE_REVISION, observed, null, "player state changed after preview");
        }
        Optional<PetInstance> target = findPet(observed, preview.petId());
        if (target.isEmpty()) return result(ReleaseResult.Status.PET_NOT_FOUND, observed, null, "pet UUID is not owned");
        if (locks.isLocked(target.get())) {
            return result(ReleaseResult.Status.LOCKED, observed, null, "locked pets cannot be released");
        }
        if (!ReleaseIdentity.petFingerprint(target.get()).equals(preview.petFingerprint())) {
            return result(ReleaseResult.Status.INVALID_CONFIRMATION, observed, null, "pet identity changed after preview");
        }
        if (outbox.size(observed) >= ReleaseOutboxStore.MAX_ENTRIES) {
            return result(ReleaseResult.Status.OUTBOX_FULL, observed, null, "release outbox requires delivery or archival");
        }
        ReleaseOutboxEntry entry = new ReleaseOutboxEntry(
                preview.transactionId(), preview.playerId(), preview.petId(), preview.petFingerprint(),
                preview.confirmationToken(), preview.rewards(), ReleaseOutboxEntry.InternalState.PENDING,
                preview.rewards().externalRewards().isEmpty()
                        ? ReleaseOutboxEntry.ExternalState.NOT_REQUIRED
                        : ReleaseOutboxEntry.ExternalState.PENDING,
                "", Map.of());
        try {
            PlayerState saved = players.withLocked(preview.playerId(), preview.expectedRevision(),
                    current -> outbox.release(current, preview.petId(), entry));
            return result(ReleaseResult.Status.COMMITTED, saved, entry, "pet removed and reward outbox committed");
        } catch (StaleRevisionException stale) {
            PlayerState current = players.snapshot(preview.playerId());
            ReleaseResult retried = existingResult(current, preview);
            return retried != null
                    ? retried
                    : result(ReleaseResult.Status.STALE_REVISION, current, null, "player state changed during release");
        }
    }

    private ReleaseResult existingResult(PlayerState state, ReleasePreview preview) {
        Optional<ReleaseOutboxEntry> existing = outbox.find(state, preview.transactionId());
        if (existing.isEmpty()) return null;
        ReleaseOutboxEntry entry = existing.get();
        boolean matches = entry.playerId().equals(preview.playerId())
                && entry.petId().equals(preview.petId())
                && entry.petFingerprint().equals(preview.petFingerprint())
                && entry.previewToken().equals(preview.confirmationToken());
        return matches
                ? result(ReleaseResult.Status.ALREADY_COMMITTED, state, entry, "release transaction already committed")
                : result(ReleaseResult.Status.TRANSACTION_CONFLICT, state, entry,
                        "release transaction UUID belongs to a different identity");
    }

    private static Optional<PetInstance> findPet(PlayerState state, UUID petId) {
        return state.pets().stream().filter(pet -> pet.id().equals(petId)).findFirst();
    }

    private static ReleasePreviewResult previewResult(
            ReleasePreviewResult.Status status, ReleasePreview preview, String detail) {
        return new ReleasePreviewResult(status, preview, detail);
    }

    private static ReleaseResult result(
            ReleaseResult.Status status, PlayerState state, ReleaseOutboxEntry entry, String detail) {
        return new ReleaseResult(status, state, entry, detail);
    }

    private static String shortDetail(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message.length() <= 256 ? message : message.substring(0, 256);
    }
}
