package io.github.salyvn.omnipet.core.incubation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.core.storage.PetStorageResult;
import io.github.salyvn.omnipet.core.storage.PetStorageService;

public final class HatchService {
    private final DeterministicHatchRollService rolls;
    private final PetStorageService storage;

    public HatchService() {
        this(new DeterministicHatchRollService(), new PetStorageService());
    }

    HatchService(DeterministicHatchRollService rolls, PetStorageService storage) {
        this.rolls = Objects.requireNonNull(rolls, "roll service");
        this.storage = Objects.requireNonNull(storage, "pet storage service");
    }

    public HatchResult start(
            PlayerState state,
            UUID incubationId,
            EggDefinition egg,
            RegistrySnapshot registry,
            long seed,
            PetStorageLimits limits) {
        requireState(state);
        Objects.requireNonNull(incubationId, "incubation id");
        Objects.requireNonNull(limits, "storage limits");
        IncubationState current = state.incubation();
        if (current != null && !current.terminal()) return result(HatchResult.Status.ALREADY_INCUBATING, state);
        UUID petInstanceId = DeterministicHatchRollService.petInstanceId(incubationId);
        if ((current != null && current.id().equals(incubationId))
                || state.pets().stream().anyMatch(pet -> pet.id().equals(petInstanceId))) {
            return result(HatchResult.Status.INCUBATION_ID_REUSED, state);
        }
        if (!state.legacyCurrentEgg().isEmpty()) return result(HatchResult.Status.LEGACY_MIGRATION_REQUIRED, state);
        if (storage.snapshot(state, limits).vaultAtCapacity()) {
            return result(HatchResult.Status.VAULT_CAPACITY_REACHED, state);
        }
        var outcome = rolls.roll(incubationId, egg, registry, seed).outcome();
        IncubationState started = new IncubationState(
                incubationId,
                egg.id(),
                outcome,
                outcome.totalActiveMillis(),
                IncubationStatus.INCUBATING,
                List.of(),
                Map.of());
        return changed(HatchResult.Status.STARTED, state, started, null);
    }

    public HatchResult tick(PlayerState state, UUID incubationId, long elapsedMillis) {
        if (elapsedMillis < 0) throw new IllegalArgumentException("elapsed active time cannot be negative");
        HatchResult guard = requireIncubating(state, incubationId);
        if (guard != null) return guard;
        if (elapsedMillis == 0) return result(HatchResult.Status.TICKED, state);
        IncubationState current = state.incubation();
        return remaining(HatchResult.Status.TICKED, state, current,
                IncubationStateChanges.saturatingSubtract(current.remainingActiveMillis(), elapsedMillis), null);
    }

    public HatchResult reduce(PlayerState state, UUID incubationId, long reductionMillis, UUID actionToken) {
        if (reductionMillis < 0) throw new IllegalArgumentException("incubation reduction cannot be negative");
        HatchResult guard = requireIncubating(state, incubationId);
        if (guard != null) return guard;
        IncubationState current = state.incubation();
        HatchResult duplicate = duplicate(state, current, actionToken);
        if (duplicate != null) return duplicate;
        return remaining(HatchResult.Status.REDUCED, state, current,
                IncubationStateChanges.saturatingSubtract(current.remainingActiveMillis(), reductionMillis), actionToken);
    }

    public HatchResult setRemaining(PlayerState state, UUID incubationId, long remainingMillis, UUID actionToken) {
        HatchResult guard = requireIncubating(state, incubationId);
        if (guard != null) return guard;
        IncubationState current = state.incubation();
        if (remainingMillis < 0 || remainingMillis > current.outcome().totalActiveMillis()) {
            throw new IllegalArgumentException("remaining active time is outside the resolved duration");
        }
        HatchResult duplicate = duplicate(state, current, actionToken);
        if (duplicate != null) return duplicate;
        return remaining(HatchResult.Status.REMAINING_SET, state, current, remainingMillis, actionToken);
    }

    public HatchResult complete(PlayerState state, UUID incubationId, UUID actionToken) {
        HatchResult guard = requireIncubating(state, incubationId);
        if (guard != null) return guard;
        IncubationState current = state.incubation();
        HatchResult duplicate = duplicate(state, current, actionToken);
        if (duplicate != null) return duplicate;
        return remaining(HatchResult.Status.COMPLETED, state, current, 0, actionToken);
    }

    public HatchResult cancel(PlayerState state, UUID incubationId, UUID actionToken) {
        requireState(state);
        IncubationState current = state.incubation();
        HatchResult mismatch = requireMatch(state, current, incubationId);
        if (mismatch != null) return mismatch;
        HatchResult duplicate = duplicate(state, current, actionToken);
        if (duplicate != null) return duplicate;
        if (current.status() != IncubationStatus.INCUBATING && current.status() != IncubationStatus.READY) {
            return result(HatchResult.Status.INVALID_STATE, state);
        }
        IncubationState cancelled = IncubationStateChanges.cancelled(current, actionToken);
        return changed(HatchResult.Status.CANCELLED, state, cancelled, null);
    }

    public HatchResult claim(PlayerState state, UUID incubationId, PetStorageLimits limits) {
        requireState(state);
        Objects.requireNonNull(limits, "storage limits");
        IncubationState current = state.incubation();
        HatchResult mismatch = requireMatch(state, current, incubationId);
        if (mismatch != null) return mismatch;
        PetInstance pet = IncubationPetFactory.create(current);
        if (current.status() == IncubationStatus.CLAIMED) {
            return new HatchResult(HatchResult.Status.ALREADY_CLAIMED, state, current, pet);
        }
        if (current.status() != IncubationStatus.READY) return result(HatchResult.Status.INVALID_STATE, state);
        PetInstance existing = state.pets().stream()
                .filter(owned -> owned.id().equals(pet.id()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            if (!existing.equals(pet)) return result(HatchResult.Status.INVALID_STATE, state);
            IncubationState claimed = IncubationStateChanges.claimed(current);
            return changed(HatchResult.Status.CLAIMED, state, claimed, pet);
        }
        PetStorageResult admission = storage.admit(state, pet, limits);
        if (admission.status() == PetStorageResult.Status.VAULT_CAPACITY_REACHED) {
            return result(HatchResult.Status.VAULT_CAPACITY_REACHED, state);
        }
        if (admission.status() != PetStorageResult.Status.ADMITTED) {
            return result(HatchResult.Status.INVALID_STATE, state);
        }
        IncubationState claimed = IncubationStateChanges.claimed(current);
        PlayerState next = admission.state().withIncubation(claimed);
        return new HatchResult(HatchResult.Status.CLAIMED, next, claimed, pet);
    }

    private HatchResult requireIncubating(PlayerState state, UUID incubationId) {
        requireState(state);
        IncubationState current = state.incubation();
        HatchResult mismatch = requireMatch(state, current, incubationId);
        if (mismatch != null) return mismatch;
        if (current.status() == IncubationStatus.READY) return result(HatchResult.Status.ALREADY_READY, state);
        if (current.status() != IncubationStatus.INCUBATING) return result(HatchResult.Status.INVALID_STATE, state);
        return null;
    }

    private static HatchResult requireMatch(PlayerState state, IncubationState current, UUID incubationId) {
        Objects.requireNonNull(incubationId, "incubation id");
        if (current == null) return result(HatchResult.Status.NO_INCUBATION, state);
        return current.id().equals(incubationId) ? null : result(HatchResult.Status.INCUBATION_ID_MISMATCH, state);
    }

    private static HatchResult duplicate(PlayerState state, IncubationState current, UUID actionToken) {
        Objects.requireNonNull(actionToken, "action token");
        if (current.hasApplied(actionToken)) return result(HatchResult.Status.ALREADY_APPLIED, state);
        return current.appliedActionTokens().size() == IncubationState.MAX_ACTION_TOKENS
                ? result(HatchResult.Status.ACTION_TOKEN_CAPACITY_REACHED, state)
                : null;
    }

    private static HatchResult remaining(
            HatchResult.Status status,
            PlayerState state,
            IncubationState current,
            long remaining,
            UUID actionToken) {
        IncubationState next = IncubationStateChanges.remaining(current, remaining, actionToken);
        return next.equals(current) ? result(status, state) : changed(status, state, next, null);
    }

    private static HatchResult changed(
            HatchResult.Status status,
            PlayerState state,
            IncubationState incubation,
            PetInstance pet) {
        PlayerState next = state.withIncubation(incubation);
        return new HatchResult(status, next, incubation, pet);
    }

    private static HatchResult result(HatchResult.Status status, PlayerState state) {
        return new HatchResult(status, state, state.incubation(), null);
    }

    private static PlayerState requireState(PlayerState state) { return Objects.requireNonNull(state, "player state"); }
}
