package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class IncubationTickBaselinesTest {
    @Test
    void excludesSamplesTakenBeforeCommittedEscrowWasObserved() {
        IncubationTickBaselines baselines = new IncubationTickBaselines();
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();

        assertEquals(0, baselines.elapsedMillis(playerId, incubationId, 50_000_000L, 100_000_000L));
        assertEquals(0, baselines.elapsedMillis(playerId, incubationId, 90_000_000L, 110_000_000L));
        assertEquals(50, baselines.elapsedMillis(playerId, incubationId, 150_000_000L, 160_000_000L));

        baselines.commit(playerId, incubationId, 150_000_000L);

        assertEquals(25, baselines.elapsedMillis(playerId, incubationId, 175_000_000L, 180_000_000L));
    }

    @Test
    void failedTicksAreDiscardedSoProcessKillCannotRollBackUnboundedTime() {
        IncubationTickBaselines baselines = new IncubationTickBaselines();
        UUID playerId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(0, baselines.elapsedMillis(playerId, first, 100_000_000L, 100_000_000L));
        assertEquals(50, baselines.elapsedMillis(playerId, first, 150_000_000L, 150_000_000L));
        baselines.discard(playerId, first, 150_000_000L);
        assertEquals(50, baselines.elapsedMillis(playerId, first, 200_000_000L, 200_000_000L));
        assertEquals(0, baselines.elapsedMillis(playerId, second, 250_000_000L, 250_000_000L));

        baselines.reset(playerId);

        assertEquals(0, baselines.elapsedMillis(playerId, second, 300_000_000L, 300_000_000L));
    }

    @Test
    void committedObservationStartsTimingImmediatelyWithoutCountingEarlierSamples() {
        IncubationTickBaselines baselines = new IncubationTickBaselines();
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();

        baselines.observeCommitted(playerId, incubationId, 100_000_000L);

        assertEquals(50, baselines.elapsedMillis(
                playerId, incubationId, 150_000_000L, 160_000_000L));
    }
}
