package io.github.salyvn.omnipet.paper.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;

class PlayerPetAsyncQueueTest {
    @Test
    void serializesOnePlayersTasksInSubmissionOrder() {
        ControlledExecutor executor = new ControlledExecutor();
        PlayerPetAsyncQueue queue = new PlayerPetAsyncQueue(executor);
        UUID playerId = UUID.randomUUID();
        List<Integer> completed = new ArrayList<>();

        queue.submit(playerId, () -> completed.add(1));
        queue.submit(playerId, () -> completed.add(2));

        assertEquals(1, executor.pendingCount());
        executor.runNext();
        assertEquals(List.of(1, 2), completed);
        assertEquals(0, executor.pendingCount());
    }

    @Test
    void shutdownRejectsNewWorkDropsPendingReadsAndDrainsAcceptedMutations() throws InterruptedException {
        ControlledExecutor executor = new ControlledExecutor();
        PlayerPetAsyncQueue queue = new PlayerPetAsyncQueue(executor);
        UUID playerId = UUID.randomUUID();
        List<Integer> completed = new ArrayList<>();

        assertEquals(true, queue.submit(playerId, () -> completed.add(1)));
        assertEquals(true, queue.submitLatest(playerId, "view", () -> completed.add(99)));
        assertEquals(true, queue.submit(playerId, () -> completed.add(2)));
        queue.shutdown();
        assertEquals(false, queue.submit(playerId, () -> completed.add(3)));

        executor.runNext();
        assertEquals(List.of(1, 2), completed);
        assertEquals(true, queue.awaitIdle(Duration.ofMillis(10)));
        assertEquals(0, executor.pendingCount());
    }

    @Test
    void latestCoalescedReadReplacesThePendingReadWithoutReorderingMutations() {
        ControlledExecutor executor = new ControlledExecutor();
        PlayerPetAsyncQueue queue = new PlayerPetAsyncQueue(executor);
        UUID playerId = UUID.randomUUID();
        List<Integer> completed = new ArrayList<>();

        queue.submitLatest(playerId, "view", () -> completed.add(1));
        queue.submitLatest(playerId, "view", () -> completed.add(2));
        queue.submit(playerId, () -> completed.add(3));
        queue.submitLatest(playerId, "view", () -> completed.add(4));

        executor.runNext();
        assertEquals(List.of(3, 4), completed);
        assertEquals(0, executor.pendingCount());
    }

    @Test
    void executorRejectionDoesNotLeaveThePlayerQueueBusy() throws InterruptedException {
        PlayerPetAsyncQueue queue = new PlayerPetAsyncQueue(command -> {
            throw new RejectedExecutionException("executor stopped");
        });

        org.junit.jupiter.api.Assertions.assertThrows(
                RejectedExecutionException.class,
                () -> queue.submit(UUID.randomUUID(), () -> {}));
        assertEquals(true, queue.awaitIdle(Duration.ofMillis(10)));
    }

    private static final class ControlledExecutor implements Executor {
        private final Queue<Runnable> pending = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        int pendingCount() {
            return pending.size();
        }

        void runNext() {
            pending.remove().run();
        }
    }
}
