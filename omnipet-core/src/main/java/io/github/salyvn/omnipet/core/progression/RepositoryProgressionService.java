package io.github.salyvn.omnipet.core.progression;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;

/** Persists stable-ID cultivation mutations under the player's revision lock. */
public final class RepositoryProgressionService {
    private final PlayerStateRepository players;
    private final ProgressionService progression;

    public RepositoryProgressionService(PlayerStateRepository players) {
        this(players, new ProgressionService());
    }

    RepositoryProgressionService(PlayerStateRepository players, ProgressionService progression) {
        this.players = Objects.requireNonNull(players, "player state repository");
        this.progression = Objects.requireNonNull(progression, "progression service");
    }

    public RepositoryProgressionResult addExperience(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            double amount,
            ProgressionMutationContext context) throws IOException {
        return mutate(playerId, expectedRevision, petId, context, state ->
                progression.addExperience(state, amount, context.config(), context.petFormula(), context.formulaValues()),
                null);
    }

    public RepositoryProgressionResult addExperience(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            double amount,
            ProgressionMutationContext context,
            CultivationItemActionTransaction action) throws IOException {
        requireAction(action, CultivationItemActionKind.EXPERIENCE_CANDY, playerId, petId);
        RepositoryProgressionResult existing = existing(playerId, petId, action, context);
        return existing != null ? existing : mutate(playerId, expectedRevision, petId, context, state ->
                progression.addExperience(state, amount, context.config(), context.petFormula(), context.formulaValues()),
                action);
    }

    public RepositoryProgressionResult breakthrough(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            int requiredLevel,
            int requiredEvolution,
            ProgressionMutationContext context) throws IOException {
        return mutate(playerId, expectedRevision, petId, context, state ->
                progression.breakthrough(state, requiredLevel, requiredEvolution, context.config()), null);
    }

    public RepositoryProgressionResult breakthrough(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            int requiredLevel,
            int requiredEvolution,
            ProgressionMutationContext context,
            CultivationItemActionTransaction action) throws IOException {
        requireAction(action, CultivationItemActionKind.BREAKTHROUGH_STONE, playerId, petId);
        RepositoryProgressionResult existing = existing(playerId, petId, action, context);
        return existing != null ? existing : mutate(playerId, expectedRevision, petId, context, state ->
                progression.breakthrough(state, requiredLevel, requiredEvolution, context.config()), action);
    }

    public RepositoryProgressionResult spendStamina(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            double amount,
            ProgressionMutationContext context) throws IOException {
        return mutate(playerId, expectedRevision, petId, context, state ->
                progression.spendStamina(state, amount, context.config()), null);
    }

    public RepositoryProgressionResult regenerateStamina(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            ProgressionMutationContext context) throws IOException {
        return mutate(playerId, expectedRevision, petId, context, state -> {
            ProgressionState next = progression.regenerateStamina(state, context.nowEpochMillis(), context.config());
            return new ProgressionResult(ProgressionResult.Status.APPLIED, next, 0, "stamina regenerated");
        }, null);
    }

    public boolean hasAction(UUID playerId, UUID petId, CultivationItemActionTransaction action) throws IOException {
        PlayerState state = players.snapshot(playerId);
        PetInstance pet = state.pets().stream()
                .filter(candidate -> candidate.id().equals(petId)).findFirst().orElse(null);
        return pet != null && CultivationActionReceiptProjection.matches(pet, action);
    }

    public void acknowledgeAction(
            UUID playerId, UUID petId, CultivationItemActionTransaction action) throws IOException {
        for (int attempt = 0; attempt < 2; attempt++) {
            PlayerState observed = players.snapshot(playerId);
            PetInstance pet = observed.pets().stream()
                    .filter(candidate -> candidate.id().equals(petId)).findFirst().orElse(null);
            if (pet == null || !CultivationActionReceiptProjection.matches(pet, action)) return;
            try {
                players.withLocked(playerId, observed.revision(), current -> {
                    int index = petIndex(current.pets(), petId);
                    if (index < 0) return current;
                    PetInstance currentPet = current.pets().get(index);
                    if (!CultivationActionReceiptProjection.matches(currentPet, action)) return current;
                    List<PetInstance> pets = new ArrayList<>(current.pets());
                    pets.set(index, CultivationActionReceiptProjection.remove(currentPet, action));
                    return current.withStorage(
                            pets, current.vaultCapacity(), current.activeSlotCount(),
                            current.desiredActivePetIds(), current.slotEntitlements());
                });
                return;
            } catch (StaleRevisionException stale) {
                if (attempt == 1) throw stale;
            }
        }
    }

    private RepositoryProgressionResult mutate(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            ProgressionMutationContext context,
            Function<ProgressionState, ProgressionResult> mutation,
            CultivationItemActionTransaction action) throws IOException {
        Objects.requireNonNull(playerId, "progression player ID");
        Objects.requireNonNull(petId, "progression pet ID");
        Objects.requireNonNull(context, "progression mutation context");
        try {
            final RepositoryProgressionResult[] captured = new RepositoryProgressionResult[1];
            PlayerState saved = players.withLocked(playerId, expectedRevision, current -> {
                int index = petIndex(current.pets(), petId);
                if (index < 0) {
                    throw new Rejected(new RepositoryProgressionResult(
                            RepositoryProgressionResult.Status.PET_NOT_FOUND, current, null, null));
                }
                PetInstance pet = current.pets().get(index);
                if (action != null && CultivationActionReceiptProjection.matches(pet, action)) {
                    ProgressionState existing = PetProgressionProjection.read(
                            pet, context.initialStamina(), context.nowEpochMillis());
                    throw new Rejected(new RepositoryProgressionResult(
                            RepositoryProgressionResult.Status.PERSISTED, current, pet,
                            new ProgressionResult(ProgressionResult.Status.APPLIED, existing, 0,
                                    "cultivation action already persisted")));
                }
                ProgressionState before = PetProgressionProjection.read(
                        pet, context.initialStamina(), context.nowEpochMillis());
                ProgressionResult result = mutation.apply(before);
                if (!result.succeeded() || result.state().equals(before)) {
                    throw new Rejected(new RepositoryProgressionResult(
                            RepositoryProgressionResult.Status.REJECTED, current, pet, result));
                }
                PetInstance updated = PetProgressionProjection.write(pet, result.state());
                if (action != null) updated = CultivationActionReceiptProjection.add(updated, action);
                List<PetInstance> pets = new ArrayList<>(current.pets());
                pets.set(index, updated);
                captured[0] = new RepositoryProgressionResult(
                        RepositoryProgressionResult.Status.PERSISTED, current, updated, result);
                return current.withStorage(
                        pets,
                        current.vaultCapacity(),
                        current.activeSlotCount(),
                        current.desiredActivePetIds(),
                        current.slotEntitlements());
            });
            RepositoryProgressionResult result = captured[0];
            PetInstance savedPet = saved.pets().stream().filter(pet -> pet.id().equals(petId)).findFirst().orElseThrow();
            return new RepositoryProgressionResult(result.status(), saved, savedPet, result.progression());
        } catch (Rejected rejected) {
            return rejected.result;
        }
    }

    private RepositoryProgressionResult existing(
            UUID playerId,
            UUID petId,
            CultivationItemActionTransaction action,
            ProgressionMutationContext context) throws IOException {
        PlayerState state = players.snapshot(playerId);
        PetInstance pet = state.pets().stream()
                .filter(candidate -> candidate.id().equals(petId)).findFirst().orElse(null);
        if (pet == null || !CultivationActionReceiptProjection.matches(pet, action)) return null;
        ProgressionState progressionState = PetProgressionProjection.read(
                pet, context.initialStamina(), context.nowEpochMillis());
        return new RepositoryProgressionResult(
                RepositoryProgressionResult.Status.PERSISTED, state, pet,
                new ProgressionResult(ProgressionResult.Status.APPLIED, progressionState, 0,
                        "cultivation action already persisted"));
    }

    private static void requireAction(
            CultivationItemActionTransaction action,
            CultivationItemActionKind kind,
            UUID playerId,
            UUID petId) {
        Objects.requireNonNull(action, "cultivation action");
        if (action.kind() != kind || !action.playerId().equals(playerId) || !action.petId().equals(petId)) {
            throw new IllegalArgumentException("cultivation action identity or kind differs");
        }
    }

    private static int petIndex(List<PetInstance> pets, UUID petId) {
        for (int index = 0; index < pets.size(); index++) {
            if (pets.get(index).id().equals(petId)) return index;
        }
        return -1;
    }

    private static final class Rejected extends RuntimeException {
        private final RepositoryProgressionResult result;

        private Rejected(RepositoryProgressionResult result) {
            super(null, null, false, false);
            this.result = result;
        }
    }
}
