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

    public RepositorySkillActionResult prepare(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            SkillBinding binding,
            UUID actionId,
            long nowEpochMillis,
            double initialStamina) throws IOException {
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
            if (skill.cooldownDeadlines().getOrDefault(binding.bindingId(), 0L) > nowEpochMillis) {
                return rejected(RepositorySkillActionResult.Status.COOLDOWN, current, pet, null, "skill is cooling down");
            }
            if (!binding.persistCooldown()
                    && transientDeadline(playerId, petId, binding.bindingId()) > nowEpochMillis) {
                return rejected(RepositorySkillActionResult.Status.COOLDOWN, current, pet, null, "skill is cooling down");
            }
            ProgressionState progression = PetProgressionProjection.read(pet, initialStamina, nowEpochMillis);
            double reserved = skill.pendingActions().values().stream()
                    .mapToDouble(SkillActionReservation::staminaCost).sum();
            if (progression.stamina() - reserved < binding.staminaCost()) {
                return rejected(RepositorySkillActionResult.Status.INSUFFICIENT_STAMINA, current, pet, null,
                        "not enough unreserved stamina");
            }
            long deadline = saturatingAdd(nowEpochMillis, binding.cooldown().toMillis());
            SkillActionReservation reservation = new SkillActionReservation(
                    actionId, binding.bindingId(), binding.staminaCost(), deadline, nowEpochMillis,
                    binding.persistCooldown());
            Map<UUID, SkillActionReservation> pending = new LinkedHashMap<>(skill.pendingActions());
            pending.put(actionId, reservation);
            PetInstance updated = PetSkillStateProjection.write(pet,
                    new PetSkillState(skill.cooldownDeadlines(), pending, skill.extensions()));
            return accepted(RepositorySkillActionResult.Status.PREPARED, current, updated, reservation,
                    "skill action prepared");
        });
    }

    public RepositorySkillActionResult complete(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            UUID actionId,
            long nowEpochMillis,
            double initialStamina) throws IOException {
        return mutate(playerId, expectedRevision, petId, (current, pet) -> {
            PetSkillState skill = PetSkillStateProjection.read(pet);
            SkillActionReservation reservation = skill.pendingActions().get(actionId);
            if (reservation == null) return rejected(RepositorySkillActionResult.Status.ACTION_NOT_FOUND, current, pet,
                    null, "pending skill action is absent");
            ProgressionState progression = PetProgressionProjection.read(pet, initialStamina, nowEpochMillis);
            if (progression.stamina() < reservation.staminaCost()) {
                return rejected(RepositorySkillActionResult.Status.CONFLICT, current, pet, reservation,
                        "reserved stamina was consumed by another workflow");
            }
            ProgressionState nextProgression = new ProgressionState(
                    progression.level(), progression.experience(), progression.evolution(),
                    progression.stamina() - reservation.staminaCost(),
                    progression.lastStaminaEpochMillis(), progression.extensions());
            Map<String, Long> cooldowns = new LinkedHashMap<>(skill.cooldownDeadlines());
            if (reservation.cooldownDeadline() > nowEpochMillis) {
                if (reservation.persistCooldown()) {
                    cooldowns.put(reservation.bindingId(), reservation.cooldownDeadline());
                } else {
                    rememberTransient(playerId, petId, reservation, nowEpochMillis);
                }
            }
            Map<UUID, SkillActionReservation> pending = new LinkedHashMap<>(skill.pendingActions());
            pending.remove(actionId);
            PetInstance updated = PetProgressionProjection.write(pet, nextProgression);
            updated = PetSkillStateProjection.write(updated, new PetSkillState(cooldowns, pending, skill.extensions()));
            return accepted(RepositorySkillActionResult.Status.COMPLETED, current, updated, reservation,
                    "skill stamina and cooldown committed");
        });
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
