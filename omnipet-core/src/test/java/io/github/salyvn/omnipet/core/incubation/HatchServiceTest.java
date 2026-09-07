package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.core.storage.PetStorageService;

class HatchServiceTest {
    private final HatchService hatches = new HatchService();

    @Test
    void startPersistsResolvedOutcomeAndOfflineTimeDoesNotAdvanceWithoutTick() {
        PlayerState state = PlayerState.empty(UUID.randomUUID());
        UUID incubationId = UUID.randomUUID();

        HatchResult started = hatches.start(
                state,
                incubationId,
                IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(),
                42,
                new PetStorageLimits(1, 1, true));

        assertEquals(HatchResult.Status.STARTED, started.status());
        assertEquals(incubationId, started.incubation().id());
        assertEquals("ember_fox", started.incubation().outcome().definitionId());
        assertEquals(started.incubation().outcome().totalActiveMillis(),
                started.incubation().remainingActiveMillis());
    }

    @Test
    void reductionsAreIdempotentClampAtZeroAndReachReadyOnce() {
        UUID incubationId = UUID.randomUUID();
        PlayerState started = start(incubationId);
        UUID token = UUID.randomUUID();

        HatchResult reduced = hatches.reduce(started, incubationId, 250, token);
        HatchResult duplicate = hatches.reduce(reduced.state(), incubationId, 250, token);
        HatchResult completed = hatches.reduce(
                duplicate.state(), incubationId, Long.MAX_VALUE, UUID.randomUUID());
        HatchResult alreadyReady = hatches.tick(completed.state(), incubationId, 1000);

        assertEquals(started.incubation().remainingActiveMillis() - 250,
                reduced.incubation().remainingActiveMillis());
        assertEquals(HatchResult.Status.ALREADY_APPLIED, duplicate.status());
        assertSame(reduced.state(), duplicate.state());
        assertEquals(IncubationStatus.READY, completed.incubation().status());
        assertEquals(0, completed.incubation().remainingActiveMillis());
        assertEquals(HatchResult.Status.ALREADY_READY, alreadyReady.status());
        assertSame(completed.state(), alreadyReady.state());
    }

    @Test
    void tickUsesOnlyProvidedMonotonicDeltaAndSaturatesLagSizedValues() {
        UUID incubationId = UUID.randomUUID();
        PlayerState started = start(incubationId);

        HatchResult unchanged = hatches.tick(started, incubationId, 0);
        HatchResult ready = hatches.tick(started, incubationId, Long.MAX_VALUE);

        assertSame(started, unchanged.state());
        assertEquals(IncubationStatus.READY, ready.incubation().status());
        assertEquals(0, ready.incubation().remainingActiveMillis());
    }

    @Test
    void fullVaultRetainsReadyOutcomeAndCapacityReturnClaimsExactlyOnce() {
        UUID incubationId = UUID.randomUUID();
        PlayerState started = start(incubationId);
        HatchResult ready = hatches.complete(started, incubationId, UUID.randomUUID());

        HatchResult full = hatches.claim(ready.state(), incubationId, new PetStorageLimits(0, 1, true));
        HatchResult claimed = hatches.claim(full.state(), incubationId, new PetStorageLimits(1, 1, true));
        HatchResult repeated = hatches.claim(claimed.state(), incubationId, new PetStorageLimits(1, 1, true));

        assertEquals(HatchResult.Status.VAULT_CAPACITY_REACHED, full.status());
        assertEquals(IncubationStatus.READY, full.incubation().status());
        assertEquals(0, full.state().pets().size());
        assertEquals(HatchResult.Status.CLAIMED, claimed.status());
        assertEquals(IncubationStatus.CLAIMED, claimed.incubation().status());
        assertEquals(1, claimed.state().pets().size());
        assertEquals(claimed.incubation().outcome().petInstanceId(), claimed.state().pets().getFirst().id());
        assertTrue(claimed.state().pets().getFirst().rawComponents().containsKey("hatching"));
        assertTrue(claimed.state().pets().getFirst().rawComponents().containsKey("stats"));
        assertEquals(Map.of(
                        "provider", "HEAD",
                        "fallbackHeadSource", claimed.incubation().outcome().icon().source(),
                        "fallbackHeadValue", claimed.incubation().outcome().icon().value()),
                claimed.state().pets().getFirst().rawComponents().get("appearance"));
        assertEquals(HatchResult.Status.ALREADY_CLAIMED, repeated.status());
        assertEquals(1, repeated.state().pets().size());
    }

    @Test
    void actionTokenCapacityFailsClosedWithoutPruningOldIdempotencyEvidence() {
        UUID incubationId = UUID.randomUUID();
        PlayerState started = start(incubationId);
        IncubationState current = started.incubation();
        List<UUID> tokens = IntStream.range(0, IncubationState.MAX_ACTION_TOKENS)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        PlayerState full = started.withIncubation(new IncubationState(
                current.id(), current.eggId(), current.outcome(), current.remainingActiveMillis(),
                current.status(), tokens, current.extensions()));

        HatchResult result = hatches.reduce(full, incubationId, 1, UUID.randomUUID());

        assertEquals(HatchResult.Status.ACTION_TOKEN_CAPACITY_REACHED, result.status());
        assertSame(full, result.state());
        assertEquals(tokens, result.incubation().appliedActionTokens());
    }

    @Test
    void aFullTokenListStillAcceptsTheActionsThatEndTheIncubation() {
        UUID incubationId = UUID.randomUUID();
        PlayerState started = start(incubationId);
        IncubationState current = started.incubation();
        List<UUID> tokens = IntStream.range(0, IncubationState.MAX_ACTION_TOKENS)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        PlayerState full = started.withIncubation(new IncubationState(
                current.id(), current.eggId(), current.outcome(), current.remainingActiveMillis(),
                current.status(), tokens, current.extensions()));

        // Cancel and complete are the only two actions that can end an incubation. If the cap refused
        // them as well, an egg that reached 128 tokens could never be finished or abandoned by anyone.
        HatchResult cancelled = hatches.cancel(full, incubationId, UUID.randomUUID());
        assertEquals(HatchResult.Status.CANCELLED, cancelled.status());
        assertEquals(tokens, cancelled.incubation().appliedActionTokens(),
                "a terminal action past the cap must not grow the list");

        HatchResult completed = hatches.complete(full, incubationId, UUID.randomUUID());
        assertEquals(HatchResult.Status.COMPLETED, completed.status());
        assertEquals(0, completed.incubation().remainingActiveMillis());
    }

    @Test
    void repeatedCancellationTokenIsIdempotentAndTerminalIdCannotBeReused() {
        UUID incubationId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        PlayerState started = start(incubationId);

        HatchResult cancelled = hatches.cancel(started, incubationId, token);
        HatchResult repeated = hatches.cancel(cancelled.state(), incubationId, token);
        HatchResult reused = hatches.start(
                cancelled.state(), incubationId, IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(), 99, new PetStorageLimits(1, 1, true));

        assertEquals(HatchResult.Status.CANCELLED, cancelled.status());
        assertEquals(HatchResult.Status.ALREADY_APPLIED, repeated.status());
        assertSame(cancelled.state(), repeated.state());
        assertEquals(HatchResult.Status.INCUBATION_ID_REUSED, reused.status());
        assertSame(cancelled.state(), reused.state());
    }

    @Test
    void claimedPetMayBeReleasedWithoutDestroyingHistoricalIncubation() {
        UUID incubationId = UUID.randomUUID();
        PetStorageLimits limits = new PetStorageLimits(1, 1, true);
        HatchResult ready = hatches.complete(start(incubationId), incubationId, UUID.randomUUID());
        HatchResult claimed = hatches.claim(ready.state(), incubationId, limits);

        var removed = new PetStorageService().remove(
                claimed.state(), claimed.claimedPet().id(), limits);

        assertEquals(0, removed.state().pets().size());
        assertEquals(IncubationStatus.CLAIMED, removed.state().incubation().status());
        assertEquals(claimed.claimedPet().id(), removed.state().incubation().outcome().petInstanceId());
    }

    @Test
    void olderClaimedIncubationIdCannotBeReusedAfterCurrentStateMovesOn() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PetStorageLimits limits = new PetStorageLimits(2, 1, true);
        HatchResult firstReady = hatches.complete(
                hatches.start(PlayerState.empty(UUID.randomUUID()), firstId, IncubationTestFixtures.egg(10_000),
                        IncubationTestFixtures.registry(), 1, limits).state(),
                firstId,
                UUID.randomUUID());
        HatchResult firstClaimed = hatches.claim(firstReady.state(), firstId, limits);
        HatchResult secondStarted = hatches.start(
                firstClaimed.state(), secondId, IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(), 2, limits);
        HatchResult secondCancelled = hatches.cancel(secondStarted.state(), secondId, UUID.randomUUID());

        HatchResult reused = hatches.start(
                secondCancelled.state(), firstId, IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(), 3, limits);

        assertEquals(HatchResult.Status.INCUBATION_ID_REUSED, reused.status());
        assertSame(secondCancelled.state(), reused.state());
        assertEquals(1, reused.state().pets().size());
    }

    private PlayerState start(UUID incubationId) {
        return hatches.start(
                PlayerState.empty(UUID.randomUUID()),
                incubationId,
                IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(),
                7,
                new PetStorageLimits(1, 1, true)).state();
    }
}
