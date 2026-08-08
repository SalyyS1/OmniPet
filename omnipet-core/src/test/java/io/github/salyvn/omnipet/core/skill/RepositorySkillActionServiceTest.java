package io.github.salyvn.omnipet.core.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;
import io.github.salyvn.omnipet.core.progression.PetProgressionProjection;

class RepositorySkillActionServiceTest {
    @TempDir Path temporary;

    @Test
    void durablePrepareThenCompleteCommitsStaminaAndCooldown() throws Exception {
        Fixture fixture = fixture();
        UUID action = UUID.randomUUID();

        var prepared = fixture.service.prepare(
                fixture.playerId, fixture.revision(), fixture.petId, binding(), action, 1_000, 100);
        var completed = fixture.service.complete(
                fixture.playerId, prepared.state().revision(), fixture.petId, action, 1_100, 100);

        assertEquals(RepositorySkillActionResult.Status.PREPARED, prepared.status());
        assertEquals(RepositorySkillActionResult.Status.COMPLETED, completed.status());
        assertEquals(90.0, PetProgressionProjection.read(completed.pet(), 100, 1_100).stamina());
        assertEquals(6_000L, PetSkillStateProjection.read(completed.pet()).cooldownDeadlines().get("active_one"));
        assertTrue(PetSkillStateProjection.read(completed.pet()).pendingActions().isEmpty());
    }

    @Test
    void failedProviderCanRollbackWithoutChargingReservation() throws Exception {
        Fixture fixture = fixture();
        UUID action = UUID.randomUUID();
        var prepared = fixture.service.prepare(
                fixture.playerId, fixture.revision(), fixture.petId, binding(), action, 1_000, 100);

        var rolledBack = fixture.service.rollback(
                fixture.playerId, prepared.state().revision(), fixture.petId, action);

        assertEquals(RepositorySkillActionResult.Status.ROLLED_BACK, rolledBack.status());
        assertEquals(100.0, PetProgressionProjection.read(rolledBack.pet(), 100, 1_100).stamina());
        assertTrue(PetSkillStateProjection.read(rolledBack.pet()).pendingActions().isEmpty());
    }

    @Test
    void pendingReservationSurvivesRepositoryRestartAndBlocksDuplicateBinding() throws Exception {
        Fixture fixture = fixture();
        UUID action = UUID.randomUUID();
        var prepared = fixture.service.prepare(
                fixture.playerId, fixture.revision(), fixture.petId, binding(), action, 1_000, 100);
        RepositorySkillActionService restarted = new RepositorySkillActionService(
                new FilePlayerStateRepository(fixture.root));

        var duplicate = restarted.prepare(
                fixture.playerId, prepared.state().revision(), fixture.petId, binding(), UUID.randomUUID(), 1_100, 100);

        assertEquals(RepositorySkillActionResult.Status.ACTION_PENDING, duplicate.status());
    }

    @Test
    void cosmeticCooldownStaysInMemoryWhileMaterialCooldownSurvivesRestart() throws Exception {
        Fixture fixture = fixture();
        SkillBinding cosmetic = new SkillBinding(
                "cosmetic_one", "MYTHICMOBS", "sparkle", SkillTrigger.ACTIVE,
                Duration.ofSeconds(5), 1.0, 10.0, SkillTargetPolicy.OWNER, false);
        UUID first = UUID.randomUUID();
        var prepared = fixture.service.prepare(
                fixture.playerId, fixture.revision(), fixture.petId, cosmetic, first, 1_000, 100);
        var completed = fixture.service.complete(
                fixture.playerId, prepared.state().revision(), fixture.petId, first, 1_100, 100);

        assertTrue(PetSkillStateProjection.read(completed.pet()).cooldownDeadlines().isEmpty());

        var blocked = fixture.service.prepare(
                fixture.playerId, completed.state().revision(), fixture.petId, cosmetic,
                UUID.randomUUID(), 2_000, 100);
        assertEquals(RepositorySkillActionResult.Status.COOLDOWN, blocked.status());

        RepositorySkillActionService restarted = new RepositorySkillActionService(
                new FilePlayerStateRepository(fixture.root));
        var afterRestart = restarted.prepare(
                fixture.playerId, completed.state().revision(), fixture.petId, cosmetic,
                UUID.randomUUID(), 2_000, 100);
        assertEquals(RepositorySkillActionResult.Status.PREPARED, afterRestart.status());
    }

    @Test
    void failedCompleteWriteDoesNotStrandACosmeticCooldown() throws Exception {
        Fixture fixture = fixture();
        SkillBinding cosmetic = new SkillBinding(
                "cosmetic_two", "MYTHICMOBS", "sparkle", SkillTrigger.ACTIVE,
                Duration.ofSeconds(5), 1.0, 10.0, SkillTargetPolicy.OWNER, false);
        UUID action = UUID.randomUUID();
        var prepared = fixture.service.prepare(
                fixture.playerId, fixture.revision(), fixture.petId, cosmetic, action, 1_000, 100);

        // A stale expected revision makes the durable write fail, so the player is never charged.
        assertThrows(StaleRevisionException.class, () -> fixture.service.complete(
                fixture.playerId, prepared.state().revision() + 5, fixture.petId, action, 1_100, 100));

        var retried = fixture.service.complete(
                fixture.playerId, prepared.state().revision(), fixture.petId, action, 1_200, 100);
        assertEquals(RepositorySkillActionResult.Status.COMPLETED, retried.status());
        assertEquals(90.0, PetProgressionProjection.read(retried.pet(), 100, 1_200).stamina());
    }

    /**
     * A cooldown refusal has to say how much longer.
     *
     * <p>Without the number the player is told only "rejected: cooldown", and the only way to find out when
     * the skill is ready is to keep pressing — which is the behaviour a cooldown exists to prevent. The
     * remaining time is known only here, at the refusal, so it has to travel out on the result.
     */
    @Test
    void aCooldownRefusalReportsHowMuchLongerIsLeft() throws Exception {
        Fixture fixture = fixture();
        UUID first = UUID.randomUUID();
        var prepared = fixture.service.prepare(
                fixture.playerId, fixture.revision(), fixture.petId, binding(), first, 1_000, 100);
        var completed = fixture.service.complete(
                fixture.playerId, prepared.state().revision(), fixture.petId, first, 1_100, 100);

        // The binding's cooldown is five seconds from 1_000, so at 3_500 there are 2_500ms left.
        var blocked = fixture.service.prepare(
                fixture.playerId, completed.state().revision(), fixture.petId, binding(),
                UUID.randomUUID(), 3_500, 100);

        assertEquals(RepositorySkillActionResult.Status.COOLDOWN, blocked.status());
        assertEquals(2_500L, blocked.cooldownRemainingMillis());
    }

    /**
     * Stamina spent on skills has to come back, or a pet stops casting for good.
     *
     * <p>`staminaRegenPerSecond` is configured and `ProgressionService.regenerateStamina` implements it, but
     * nothing on the skill path ever applied it: `prepare` read the stored stamina as-is, so the number only
     * ever went down. A pet with a stamina cost therefore worked for its first few casts and then refused
     * every one after that, forever, however long its owner waited — and the refusal is a passive-trigger
     * refusal, so most of the time it is not even reported. That is "my pet stopped casting" with no cause
     * an operator can see.
     */
    @Test
    void staminaSpentOnSkillsComesBackOverTime() throws Exception {
        Fixture fixture = fixture();
        long revision = fixture.revision();
        long now = 1_000;
        // Ten casts at ten stamina each, from a hundred: the last one leaves nothing.
        for (int cast = 0; cast < 10; cast++) {
            UUID action = UUID.randomUUID();
            var prepared = fixture.service.prepare(
                    fixture.playerId, revision, fixture.petId, drain(cast), action, now, 100, 1);
            assertEquals(RepositorySkillActionResult.Status.PREPARED, prepared.status(),
                    "cast " + cast + " should have been affordable");
            var completed = fixture.service.complete(
                    fixture.playerId, prepared.state().revision(), fixture.petId, action, now, 100, 1);
            revision = completed.state().revision();
        }

        var exhausted = fixture.service.prepare(
                fixture.playerId, revision, fixture.petId, drain(10), UUID.randomUUID(), now, 100, 1);
        assertEquals(RepositorySkillActionResult.Status.INSUFFICIENT_STAMINA, exhausted.status());

        // A minute later, at one stamina per second, the pet can cast again.
        var recovered = fixture.service.prepare(
                fixture.playerId, revision, fixture.petId, drain(11), UUID.randomUUID(), now + 60_000, 100, 1);
        assertEquals(RepositorySkillActionResult.Status.PREPARED, recovered.status(),
                "stamina must regenerate, or a pet with a stamina cost stops casting permanently");
    }

    /** Regeneration must not mint stamina above the configured maximum. */
    @Test
    void staminaRegenerationStopsAtTheConfiguredMaximum() throws Exception {
        Fixture fixture = fixture();
        UUID action = UUID.randomUUID();
        var prepared = fixture.service.prepare(
                fixture.playerId, fixture.revision(), fixture.petId, binding(), action, 1_000, 100, 1);
        var completed = fixture.service.complete(
                fixture.playerId, prepared.state().revision(), fixture.petId, action, 1_000, 100, 1);
        assertEquals(90.0, PetProgressionProjection.read(completed.pet(), 100, 1_000).stamina());

        // An hour of regeneration at one per second is far more than the ninety missing.
        var later = fixture.service.prepare(
                fixture.playerId, completed.state().revision(), fixture.petId,
                new SkillBinding("other_one", "MYTHICMOBS", "ember_burst", SkillTrigger.ACTIVE,
                        Duration.ZERO, 1.0, 10.0, SkillTargetPolicy.OWNER, true),
                UUID.randomUUID(), 3_601_000, 100, 1);

        assertEquals(RepositorySkillActionResult.Status.PREPARED, later.status());
        // Capped at the configured hundred, not the hundred-and-ninety an hour of regeneration would add.
        // Still a full hundred rather than ninety: preparing only *reserves* the cost, and the reservation is
        // what stops it being spent twice. The charge lands at complete.
        assertEquals(100.0, PetProgressionProjection.read(later.pet(), 100, 3_601_000).stamina());
        assertEquals(10.0, PetSkillStateProjection.read(later.pet()).pendingActions().values().stream()
                .mapToDouble(SkillActionReservation::staminaCost).sum());
    }

    /** A distinct binding per cast, so the one-pending-cast-per-binding rule is not what is being tested. */
    private static SkillBinding drain(int index) {
        return new SkillBinding(
                "drain_" + index, "MYTHICMOBS", "ember_burst", SkillTrigger.ACTIVE,
                Duration.ZERO, 1.0, 10.0, SkillTargetPolicy.OWNER, true);
    }

    /** An outcome that is not a cooldown refusal has no wait to report, and must not invent one. */
    @Test
    void anAcceptedPrepareReportsNoCooldownRemaining() throws Exception {
        Fixture fixture = fixture();
        var prepared = fixture.service.prepare(
                fixture.playerId, fixture.revision(), fixture.petId, binding(), UUID.randomUUID(), 1_000, 100);

        assertEquals(RepositorySkillActionResult.Status.PREPARED, prepared.status());
        assertEquals(0L, prepared.cooldownRemainingMillis());
    }

    private Fixture fixture() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        Path root = temporary.resolve(playerId.toString());
        FilePlayerStateRepository repository = new FilePlayerStateRepository(root);
        repository.withLocked(playerId, 0, state -> state.withStorage(
                List.of(new PetInstance(petId, "ember_fox", 1, Map.of(), Map.of())),
                30, 1, List.of(petId), List.of()));
        return new Fixture(playerId, petId, root, repository, new RepositorySkillActionService(repository));
    }

    private static SkillBinding binding() {
        return new SkillBinding(
                "active_one", "MYTHICMOBS", "ember_burst", SkillTrigger.ACTIVE,
                Duration.ofSeconds(5), 1.0, 10.0, SkillTargetPolicy.OWNER, true);
    }

    private record Fixture(
            UUID playerId,
            UUID petId,
            Path root,
            FilePlayerStateRepository repository,
            RepositorySkillActionService service) {
        long revision() throws Exception { return repository.snapshot(playerId).revision(); }
    }
}
