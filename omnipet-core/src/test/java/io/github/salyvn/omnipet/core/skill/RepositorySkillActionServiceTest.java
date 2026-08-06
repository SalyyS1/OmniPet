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
