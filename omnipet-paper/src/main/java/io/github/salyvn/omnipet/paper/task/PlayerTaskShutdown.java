package io.github.salyvn.omnipet.paper.task;

import java.time.Duration;
import java.util.Objects;

/** Defines the disable order needed before awaiting async player mutations on Paper's main thread. */
public final class PlayerTaskShutdown {
    private PlayerTaskShutdown() {}

    public static boolean stopAndDrain(
            Runnable stopControllerIntake,
            Runnable closeProviderBridges,
            PerPlayerTaskQueue taskQueue,
            Duration timeout) throws InterruptedException {
        Objects.requireNonNull(stopControllerIntake, "controller shutdown").run();
        Objects.requireNonNull(closeProviderBridges, "provider shutdown").run();
        if (taskQueue == null) return true;
        taskQueue.shutdown();
        return taskQueue.awaitIdle(timeout);
    }
}
