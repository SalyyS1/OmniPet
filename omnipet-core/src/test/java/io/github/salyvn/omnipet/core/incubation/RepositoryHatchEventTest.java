package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

class RepositoryHatchEventTest {
    @TempDir
    Path temporary;

    @Test
    void startPublishesPersistedStateWithNoPreviousIncubation() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        FilePlayerStateRepository repository = repository("start");
        RepositoryHatchService hatches = new RepositoryHatchService(repository);
        List<HatchEvent> events = new ArrayList<>();
        hatches.addListener(event -> {
            assertEquals(1, repository.snapshot(playerId).revision());
            events.add(event);
        });

        HatchResult started = start(hatches, playerId, incubationId);

        HatchEvent event = events.getFirst();
        assertEquals(1, events.size());
        assertEquals(playerId, event.playerId());
        assertEquals(incubationId, event.incubationId());
        assertNull(event.oldRemainingActiveMillis());
        assertNull(event.oldStatus());
        assertEquals(started.incubation().remainingActiveMillis(), event.newRemainingActiveMillis());
        assertEquals(IncubationStatus.INCUBATING, event.newStatus());
        assertEquals(HatchResult.Status.STARTED, event.reason());
        assertEquals(HatchEvent.DeliveryStage.STATE_PERSISTED, event.deliveryStage());
        assertNull(event.claimedPet());
    }

    @Test
    void tickAndReducePublishOldAndNewActiveTime() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        RepositoryHatchService hatches = service("active-time");
        HatchResult started = start(hatches, playerId, incubationId);
        List<HatchEvent> events = new ArrayList<>();
        hatches.addListener(events::add);

        HatchResult ticked = hatches.tick(playerId, started.state().revision(), incubationId, 100);
        HatchResult reduced = hatches.reduce(
                playerId, ticked.state().revision(), incubationId, 250, UUID.randomUUID());

        HatchEvent tickEvent = events.get(0);
        HatchEvent reduceEvent = events.get(1);
        assertEquals(HatchResult.Status.TICKED, tickEvent.reason());
        assertEquals(started.incubation().remainingActiveMillis(), tickEvent.oldRemainingActiveMillis());
        assertEquals(ticked.incubation().remainingActiveMillis(), tickEvent.newRemainingActiveMillis());
        assertEquals(HatchResult.Status.REDUCED, reduceEvent.reason());
        assertEquals(ticked.incubation().remainingActiveMillis(), reduceEvent.oldRemainingActiveMillis());
        assertEquals(reduced.incubation().remainingActiveMillis(), reduceEvent.newRemainingActiveMillis());
        assertEquals(IncubationStatus.INCUBATING, reduceEvent.oldStatus());
        assertEquals(IncubationStatus.INCUBATING, reduceEvent.newStatus());
    }

    @Test
    void readyAndClaimedTransitionsPublishStatusAndClaimedPet() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        PetStorageLimits limits = limits();
        RepositoryHatchService hatches = service("claim");
        HatchResult started = start(hatches, playerId, incubationId);
        List<HatchEvent> events = new ArrayList<>();
        hatches.addListener(events::add);

        HatchResult ready = hatches.tick(
                playerId, started.state().revision(), incubationId, Long.MAX_VALUE);
        HatchResult claimed = hatches.claim(
                playerId, ready.state().revision(), incubationId, limits);

        HatchEvent readyEvent = events.get(0);
        HatchEvent claimedEvent = events.get(1);
        assertEquals(IncubationStatus.INCUBATING, readyEvent.oldStatus());
        assertEquals(IncubationStatus.READY, readyEvent.newStatus());
        assertEquals(0L, readyEvent.newRemainingActiveMillis());
        assertEquals(HatchResult.Status.CLAIMED, claimedEvent.reason());
        assertEquals(IncubationStatus.READY, claimedEvent.oldStatus());
        assertEquals(IncubationStatus.CLAIMED, claimedEvent.newStatus());
        assertSame(claimed.claimedPet(), claimedEvent.claimedPet());
    }

    @Test
    void unchangedResultsDoNotPublishEvents() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        RepositoryHatchService hatches = service("unchanged");
        AtomicInteger eventCount = new AtomicInteger();
        hatches.addListener(ignored -> eventCount.incrementAndGet());

        HatchResult missing = hatches.tick(playerId, 0, incubationId, 100);
        HatchResult started = start(hatches, playerId, incubationId);
        int afterStart = eventCount.get();
        HatchResult zeroTick = hatches.tick(playerId, started.state().revision(), incubationId, 0);
        HatchResult alreadyReady = hatches.tick(
                playerId,
                hatches.complete(playerId, zeroTick.state().revision(), incubationId, UUID.randomUUID())
                        .state().revision(),
                incubationId,
                1);

        assertEquals(HatchResult.Status.NO_INCUBATION, missing.status());
        assertEquals(HatchResult.Status.TICKED, zeroTick.status());
        assertEquals(HatchResult.Status.ALREADY_READY, alreadyReady.status());
        assertEquals(afterStart + 1, eventCount.get());
    }

    @Test
    void listenerFailuresAreIsolatedAndRegistrationIsIdempotent() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        RepositoryHatchService hatches = service("listeners");
        AtomicInteger failingCalls = new AtomicInteger();
        List<HatchEvent> received = new ArrayList<>();
        HatchEventListener failing = event -> {
            failingCalls.incrementAndGet();
            throw new AssertionError("listener failure");
        };
        HatchEventListener healthy = received::add;

        assertTrue(hatches.addListener(failing));
        assertFalse(hatches.addListener(failing));
        assertTrue(hatches.addListener(healthy));
        HatchResult started = start(hatches, playerId, incubationId);
        assertTrue(hatches.removeListener(failing));
        assertFalse(hatches.removeListener(failing));
        assertTrue(hatches.removeListener(healthy));
        HatchResult ticked = hatches.tick(playerId, started.state().revision(), incubationId, 100);

        assertEquals(HatchResult.Status.TICKED, ticked.status());
        assertEquals(1, failingCalls.get());
        assertEquals(1, received.size());
    }

    @Test
    void reentrantMutationsPreserveEventOrderForLaterListeners() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        RepositoryHatchService hatches = service("reentrant-order");
        List<HatchResult.Status> received = new ArrayList<>();
        hatches.addListener(event -> {
            if (event.reason() == HatchResult.Status.STARTED) {
                var snapshot = hatches.snapshot(playerId);
                hatches.tick(playerId, snapshot.revision(), incubationId, 100);
            }
        });
        hatches.addListener(event -> received.add(event.reason()));

        start(hatches, playerId, incubationId);

        assertEquals(List.of(HatchResult.Status.STARTED, HatchResult.Status.TICKED), received);
    }

    @Test
    void startingAfterTerminalStateDoesNotMixDifferentIncubationIdentities() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        RepositoryHatchService hatches = service("terminal-restart");
        HatchResult first = start(hatches, playerId, firstId);
        HatchResult cancelled = hatches.cancel(
                playerId, first.state().revision(), firstId, UUID.randomUUID());
        List<HatchEvent> events = new ArrayList<>();
        hatches.addListener(events::add);

        HatchResult second = hatches.start(
                playerId,
                cancelled.state().revision(),
                secondId,
                IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(),
                456,
                limits());

        HatchEvent event = events.getFirst();
        assertEquals(HatchResult.Status.STARTED, second.status());
        assertEquals(secondId, event.incubationId());
        assertNull(event.oldRemainingActiveMillis());
        assertNull(event.oldStatus());
        assertEquals(IncubationStatus.INCUBATING, event.newStatus());
    }

    private RepositoryHatchService service(String directory) throws Exception {
        return new RepositoryHatchService(repository(directory));
    }

    private FilePlayerStateRepository repository(String directory) throws Exception {
        return new FilePlayerStateRepository(temporary.resolve(directory));
    }

    private HatchResult start(RepositoryHatchService hatches, UUID playerId, UUID incubationId) throws Exception {
        return hatches.start(
                playerId,
                0,
                incubationId,
                IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(),
                123,
                limits());
    }

    private PetStorageLimits limits() {
        return new PetStorageLimits(1, 1, true);
    }
}
