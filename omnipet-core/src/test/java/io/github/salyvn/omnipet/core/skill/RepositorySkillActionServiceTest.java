package io.github.salyvn.omnipet.core.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
