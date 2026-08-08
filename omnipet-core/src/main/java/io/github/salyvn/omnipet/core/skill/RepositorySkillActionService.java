package io.github.salyvn.omnipet.core.skill;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.progression.PetProgressionProjection;
import io.github.salyvn.omnipet.core.progression.ProgressionState;

/** Durable prepare/cast/complete boundary; restart recovery never blindly recasts pending actions. */
public final class RepositorySkillActionService {
    /** Bounds the runtime-only cooldown table so a long uptime cannot grow it without limit. */
    static final int MAX_TRANSIENT_COOLDOWNS = 4096;

    private final PlayerStateRepository players;
    private final Map<String, Long> transientCooldowns = new ConcurrentHashMap<>();

    public RepositorySkillActionService(PlayerStateRepository players) {
        this.players = Objects.requireNonNull(players, "player state repository");
    }

    /** Prepares with no stamina regeneration, for callers with no progression configuration to hand. */
    public RepositorySkillActionResult prepare(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            SkillBinding binding,
            UUID actionId,
            long nowEpochMillis,
            double maxStamina) throws IOException {
        return prepare(playerId, expectedRevision, petId, binding, actionId, nowEpochMillis, maxStamina, 0);
    }

    /**
     * @param maxStamina the ceiling regeneration restores towards, and the value a pet starts at
     * @param staminaRegenPerSecond how fast spent stamina comes back; zero freezes it, which is what the
     *     skill path effectively did before this parameter existed
     */
    public RepositorySkillActionResult prepare(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            SkillBinding binding,
            UUID actionId,
            long nowEpochMillis,
            double maxStamina,
            double staminaRegenPerSecond) throws IOException {
        Objects.requireNonNull(binding, "skill binding");
        Objects.requireNonNull(actionId, "skill action ID");
        return mutate(playerId, expectedRevision, petId, (current, pet) -> {
            PetSkillState skill = PetSkillStateProjection.read(pet);
            SkillActionReservation existing = skill.pendingActions().get(actionId);
            if (existing != null) return rejected(RepositorySkillActionResult.Status.ALREADY_PREPARED, current, pet,
                    existing, "skill action is already prepared");
            if (skill.pendingActions().size() >= PetSkillState.MAX_PENDING_ACTIONS) {
                return rejected(RepositorySkillActionResult.Status.ACTION_PENDING, current, pet, null,
                        "pending skill action capacity reached");
            }
            if (skill.pendingActions().values().stream().anyMatch(pending -> pending.bindingId().equals(binding.bindingId()))) {
                return rejected(RepositorySkillActionResult.Status.ACTION_PENDING, current, pet, null,
                        "this skill already has a pending cast outcome");
            }
            long persisted = skill.cooldownDeadlines().getOrDefault(binding.bindingId(), 0L);
            long ephemeral = binding.persistCooldown()
                    ? 0L
                    : transientDeadline(playerId, petId, binding.bindingId());
            long deadlineNow = Math.max(persisted, ephemeral);
            if (deadlineNow > nowEpochMillis) {
                return cooling(current, pet, deadlineNow - nowEpochMillis);
            }
            // Regenerated before it is judged. Stamina is stored as a number plus the instant it was last
            // touched, so the stored value is only correct at that instant; reading it as-is meant a pet's
            // stamina could only ever fall, and a pet whose skills cost stamina stopped casting for good
            // once it hit zero, however long its owner waited.
            ProgressionState progression = regenerated(
                    PetProgressionProjection.read(pet, maxStamina, nowEpochMillis),
                    nowEpochMillis, maxStamina, staminaRegenPerSecond);
            double reserved = skill.pendingActions().values().stream()
                    .mapToDouble(SkillActionReservation::staminaCost).sum();
            if (progression.stamina() - reserved < binding.staminaCost()) {
                // A refusal writes nothing at all — the whole mutation is abandoned. That costs nothing:
                // regeneration is derived from the stored instant, so the next attempt recomputes the same
                // value rather than losing the time that passed.
                return rejected(RepositorySkillActionResult.Status.INSUFFICIENT_STAMINA, current, pet, null,
                        "not enough unreserved stamina");
            }
            long deadline = saturatingAdd(nowEpochMillis, binding.cooldown().toMillis());
            SkillActionReservation reservation = new SkillActionReservation(
                    actionId, binding.bindingId(), binding.staminaCost(), deadline, nowEpochMillis,
                    binding.persistCooldown());
            Map<UUID, SkillActionReservation> pending = new LinkedHashMap<>(skill.pendingActions());
            pending.put(actionId, reservation);
            PetInstance updated = PetSkillStateProjection.write(
                    PetProgressionProjection.write(pet, progression),
                    new PetSkillState(skill.cooldownDeadlines(), pending, skill.extensions()));
            return accepted(RepositorySkillActionResult.Status.PREPARED, current, updated, reservation,
                    "skill action prepared");
        });
    }

    /** Completes with no stamina regeneration, for callers with no progression configuration to hand. */
    public RepositorySkillActionResult complete(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            UUID actionId,
            long nowEpochMillis,
            double maxStamina) throws IOException {
        return complete(playerId, expectedRevision, petId, actionId, nowEpochMillis, maxStamina, 0);
    }

    public RepositorySkillActionResult complete(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            UUID actionId,
            long nowEpochMillis,
            double maxStamina,
            double staminaRegenPerSecond) throws IOException {
        RepositorySkillActionResult result = mutate(playerId, expectedRevision, petId, (current, pet) -> {
            PetSkillState skill = PetSkillStateProjection.read(pet);
            SkillActionReservation reservation = skill.pendingActions().get(actionId);
            if (reservation == null) return rejected(RepositorySkillActionResult.Status.ACTION_NOT_FOUND, current, pet,
                    null, "pending skill action is absent");
            // Regenerated here too, or the time between reserving a cast and committing it would be the one
            // stretch of a pet's life where stamina stands still.
            ProgressionState progression = regenerated(
                    PetProgressionProjection.read(pet, maxStamina, nowEpochMillis),
                    nowEpochMillis, maxStamina, staminaRegenPerSecond);
            if (progression.stamina() < reservation.staminaCost()) {
                return rejected(RepositorySkillActionResult.Status.CONFLICT, current, pet, reservation,
                        "reserved stamina was consumed by another workflow");
            }
            ProgressionState nextProgression = new ProgressionState(
                    progression.level(), progression.experience(), progression.evolution(),
                    progression.stamina() - reservation.staminaCost(),
                    progression.lastStaminaEpochMillis(), progression.extensions());
            Map<String, Long> cooldowns = new LinkedHashMap<>(skill.cooldownDeadlines());
            if (reservation.persistCooldown() && reservation.cooldownDeadline() > nowEpochMillis) {
                cooldowns.put(reservation.bindingId(), reservation.cooldownDeadline());
            }
            Map<UUID, SkillActionReservation> pending = new LinkedHashMap<>(skill.pendingActions());
            pending.remove(actionId);
            PetInstance updated = PetProgressionProjection.write(pet, nextProgression);
            updated = PetSkillStateProjection.write(updated, new PetSkillState(cooldowns, pending, skill.extensions()));
            return accepted(RepositorySkillActionResult.Status.COMPLETED, current, updated, reservation,
                    "skill stamina and cooldown committed");
        });
        // Recorded only after the durable write succeeds; a failed save must not leave a
        // cooldown that blocks a cast the player was never charged for.
        SkillActionReservation committed = result.reservation();
        if (result.status() == RepositorySkillActionResult.Status.COMPLETED
                && committed != null
                && !committed.persistCooldown()
                && committed.cooldownDeadline() > nowEpochMillis) {
            rememberTransient(playerId, petId, committed, nowEpochMillis);
        }
        return result;
    }

    public RepositorySkillActionResult rollback(
            UUID playerId, long expectedRevision, UUID petId, UUID actionId) throws IOException {
        return mutate(playerId, expectedRevision, petId, (current, pet) -> {
            PetSkillState skill = PetSkillStateProjection.read(pet);
            SkillActionReservation reservation = skill.pendingActions().get(actionId);
            if (reservation == null) return rejected(RepositorySkillActionResult.Status.ACTION_NOT_FOUND, current, pet,
                    null, "pending skill action is absent");
            Map<UUID, SkillActionReservation> pending = new LinkedHashMap<>(skill.pendingActions());
            pending.remove(actionId);
            PetInstance updated = PetSkillStateProjection.write(pet,
                    new PetSkillState(skill.cooldownDeadlines(), pending, skill.extensions()));
            return accepted(RepositorySkillActionResult.Status.ROLLED_BACK, current, updated, reservation,
                    "failed skill action rolled back");
        });
    }

    private RepositorySkillActionResult mutate(
            UUID playerId, long revision, UUID petId, Mutation mutation) throws IOException {
        Objects.requireNonNull(playerId, "skill player ID");
        Objects.requireNonNull(petId, "skill pet ID");
        try {
            final RepositorySkillActionResult[] captured = new RepositorySkillActionResult[1];
            PlayerState saved = players.withLocked(playerId, revision, current -> {
                int index = index(current.pets(), petId);
                if (index < 0) throw new Rejected(result(RepositorySkillActionResult.Status.PET_NOT_FOUND,
                        current, null, null, "pet UUID is not owned"));
                PetInstance pet = current.pets().get(index);
                MutationResult outcome = mutation.apply(current, pet);
                if (!outcome.accepted()) throw new Rejected(outcome.result());
                List<PetInstance> pets = new ArrayList<>(current.pets());
                pets.set(index, outcome.result().pet());
                captured[0] = outcome.result();
                return current.withStorage(pets, current.vaultCapacity(), current.activeSlotCount(),
                        current.desiredActivePetIds(), current.slotEntitlements());
            });
            RepositorySkillActionResult result = captured[0];
            PetInstance pet = saved.pets().stream().filter(candidate -> candidate.id().equals(petId)).findFirst().orElseThrow();
            return result(result.status(), saved, pet, result.reservation(), result.detail());
        } catch (Rejected rejected) {
            return rejected.result;
        }
    }

    /**
     * Stamina brought up to date before it is judged or spent.
     *
     * <p>Deliberately duplicated rather than reached for through `ProgressionService`: that class is the
     * cultivation workflow's, it carries a formula cache and a `ProgressionConfig`, and the skill path has
     * neither. What it needs is the two numbers the config already hands it. The rule is the same one
     * `ProgressionService.regenerateStamina` applies, and a clock that has gone backwards — a server whose
     * time was corrected — leaves the value alone rather than inventing a debt.
     *
     * @param regenPerSecond zero leaves stamina exactly as stored, which is what an operator gets by setting
     *     `staminaRegenPerSecond: 0`
     */
    private static ProgressionState regenerated(
            ProgressionState state, long nowEpochMillis, double maxStamina, double regenPerSecond) {
        if (regenPerSecond <= 0 || nowEpochMillis <= state.lastStaminaEpochMillis()) return state;
        double seconds = (nowEpochMillis - state.lastStaminaEpochMillis()) / 1000.0;
        double next = Math.min(maxStamina, state.stamina() + seconds * regenPerSecond);
        // Already at or above the ceiling: nothing to add, and the instant is still advanced so the next
        // call measures from here rather than re-deriving the same elapsed time.
        return state.withValues(state.level(), state.experience(), state.evolution(),
                Math.max(state.stamina(), next), nowEpochMillis);
    }

    private static int index(List<PetInstance> pets, UUID id) {
        for (int index = 0; index < pets.size(); index++) if (pets.get(index).id().equals(id)) return index;
        return -1;
    }

    /**
     * Cosmetic cooldowns stay in memory by design, so a restart may clear them. Only bindings
     * that opt into {@code persistCooldown} survive restart in player state.
     */
    private long transientDeadline(UUID playerId, UUID petId, String bindingId) {
        return transientCooldowns.getOrDefault(transientKey(playerId, petId, bindingId), 0L);
    }

    private void rememberTransient(
            UUID playerId, UUID petId, SkillActionReservation reservation, long nowEpochMillis) {
        transientCooldowns.entrySet().removeIf(entry -> entry.getValue() <= nowEpochMillis);
        if (transientCooldowns.size() >= MAX_TRANSIENT_COOLDOWNS) return;
        transientCooldowns.put(
                transientKey(playerId, petId, reservation.bindingId()), reservation.cooldownDeadline());
    }

    private static String transientKey(UUID playerId, UUID petId, String bindingId) {
        return playerId + "/" + petId + "/" + bindingId;
    }

    private static MutationResult accepted(
            RepositorySkillActionResult.Status status, PlayerState state, PetInstance pet,
            SkillActionReservation reservation, String detail) {
        return new MutationResult(true, result(status, state, pet, reservation, detail));
    }

    private static MutationResult rejected(
            RepositorySkillActionResult.Status status, PlayerState state, PetInstance pet,
            SkillActionReservation reservation, String detail) {
        return new MutationResult(false, result(status, state, pet, reservation, detail));
    }

    /** A cooldown refusal that says how much longer, so the caller can put a number in front of the player. */
    private static MutationResult cooling(PlayerState state, PetInstance pet, long remainingMillis) {
        return new MutationResult(false, new RepositorySkillActionResult(
                RepositorySkillActionResult.Status.COOLDOWN, state, pet, null, "skill is cooling down",
                remainingMillis));
    }

    private static RepositorySkillActionResult result(
            RepositorySkillActionResult.Status status, PlayerState state, PetInstance pet,
            SkillActionReservation reservation, String detail) {
        return new RepositorySkillActionResult(status, state, pet, reservation, detail);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
        return left + right;
    }

    @FunctionalInterface private interface Mutation { MutationResult apply(PlayerState state, PetInstance pet); }
    private record MutationResult(boolean accepted, RepositorySkillActionResult result) {}
    private static final class Rejected extends RuntimeException {
        private final RepositorySkillActionResult result;
        private Rejected(RepositorySkillActionResult result) {
            super(null, null, false, false);
            this.result = result;
        }
    }
}
