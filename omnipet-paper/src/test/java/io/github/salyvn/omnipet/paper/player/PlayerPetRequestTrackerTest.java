package io.github.salyvn.omnipet.paper.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlayerPetRequestTrackerTest {
    @Test
    void newerRequestsAndLogoutInvalidateOlderCompletionsWithoutTokenReuse() {
        PlayerPetRequestTracker tracker = new PlayerPetRequestTracker();
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
