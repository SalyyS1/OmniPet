package io.github.salyvn.omnipet.paper.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlayerTaskShutdownTest {
    @Test
    void stopsControllerIntakeThenClosesProviderBridgesBeforeQueueShutdown() throws Exception {
        PerPlayerTaskQueue queue = new PerPlayerTaskQueue(Runnable::run);
        List<String> events = new ArrayList<>();

        boolean idle = PlayerTaskShutdown.stopAndDrain(
                () -> {
                    events.add("controllers");
                    assertTrue(queue.submit(UUID.randomUUID(), () -> {}));
                },
                () -> {
                    events.add("providers");
                    assertTrue(queue.submit(UUID.randomUUID(), () -> {}));
                },
                queue,
                Duration.ofMillis(10));

        assertEquals(List.of("controllers", "providers"), events);
        assertTrue(idle);
        assertFalse(queue.submit(UUID.randomUUID(), () -> {}));
    }
}
