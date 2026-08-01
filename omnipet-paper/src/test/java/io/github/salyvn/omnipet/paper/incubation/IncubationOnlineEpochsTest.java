package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class IncubationOnlineEpochsTest {
    private static final UUID PLAYER =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void workFromAPreviousSessionCannotBecomeCurrentAfterReconnect() {
        IncubationOnlineEpochs epochs = new IncubationOnlineEpochs();

        long first = epochs.join(PLAYER);
        assertTrue(epochs.isCurrent(PLAYER, first));

        epochs.quit(PLAYER);
        assertFalse(epochs.isCurrent(PLAYER, first));

        long second = epochs.join(PLAYER);
        assertNotEquals(first, second);
        assertFalse(epochs.isCurrent(PLAYER, first));
        assertTrue(epochs.isCurrent(PLAYER, second));
    }
}
