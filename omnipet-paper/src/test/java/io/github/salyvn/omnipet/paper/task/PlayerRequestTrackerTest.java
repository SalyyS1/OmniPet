package io.github.salyvn.omnipet.paper.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlayerRequestTrackerTest {
    @Test
    void newerRequestsAndLogoutInvalidateOlderCompletionsWithoutTokenReuse() {
        PlayerRequestTracker tracker = new PlayerRequestTracker();
        UUID playerId = UUID.randomUUID();

        long first = tracker.begin(playerId);
        long second = tracker.begin(playerId);
        assertFalse(tracker.isCurrent(playerId, first));
        assertTrue(tracker.isCurrent(playerId, second));

        tracker.invalidate(playerId);
        assertFalse(tracker.isCurrent(playerId, second));

        long afterJoin = tracker.begin(playerId);
        assertFalse(tracker.isCurrent(playerId, second));
        assertTrue(tracker.isCurrent(playerId, afterJoin));
    }
}
