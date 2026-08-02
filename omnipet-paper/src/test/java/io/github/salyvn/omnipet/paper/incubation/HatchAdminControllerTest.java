package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationOutcome;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.incubation.HatchResult;

class HatchAdminControllerTest {
    @Test
    void inspectQueuesOfflineSnapshotAndSendsResponseThroughMainDispatcher() {
        UUID playerId = UUID.randomUUID();
        RecordingOperations operations = new RecordingOperations(PlayerState.empty(playerId).withRevision(7));
        Harness harness = new Harness(operations);

        harness.controller.inspect(harness.sender, playerId);

        assertEquals(List.of(playerId), harness.queue.playerIds);
        assertTrue(harness.messages.isEmpty());
        harness.queue.runAll();
        assertTrue(harness.messages.isEmpty());
        assertEquals(1, harness.mainTasks.size());
        harness.runMain();
        assertTrue(harness.messages.getFirst().contains("player=" + playerId));
        assertTrue(harness.messages.getFirst().contains("revision=7"));
        assertTrue(harness.messages.getFirst().contains("incubation=none"));
    }

    @Test
    void mutationRejectsIncubationMismatchBeforeCallingCore() {
        UUID playerId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        UUID requestedId = UUID.randomUUID();
        RecordingOperations operations = new RecordingOperations(state(playerId, 11, currentId, 900));
        Harness harness = new Harness(operations);

        harness.controller.complete(harness.sender, playerId, requestedId, UUID.randomUUID());
        harness.runEverything();

        assertEquals(0, operations.mutationCalls);
        assertTrue(harness.messages.getFirst().contains("requested=" + requestedId));
        assertTrue(harness.messages.getFirst().contains("current=" + currentId));
    }

    @Test
    void mutationUsesSnapshotRevisionAndReportsStructuredCoreStatus() {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        PlayerState snapshot = state(playerId, 41, incubationId, 900);
        PlayerState saved = state(playerId, 42, incubationId, 400);
        RecordingOperations operations = new RecordingOperations(snapshot);
        operations.result = new HatchResult(HatchResult.Status.REDUCED, saved, saved.incubation(), null);
        Harness harness = new Harness(operations);

        harness.controller.reduce(harness.sender, playerId, incubationId, 500, actionId);
        harness.runEverything();

        assertEquals(1, operations.mutationCalls);
        assertEquals(41, operations.revision);
        assertEquals(500, operations.millis);
        assertEquals(actionId, operations.actionId);
        assertTrue(harness.messages.getFirst().contains("result=reduced"));
        assertTrue(harness.messages.getFirst().contains("revision=42"));
        assertTrue(harness.messages.getFirst().contains("remaining=400ms"));
    }

    @Test
    void submissionsRemainNonCoalescedForTheSamePlayer() {
        UUID playerId = UUID.randomUUID();
        Harness harness = new Harness(new RecordingOperations(PlayerState.empty(playerId)));

        harness.controller.inspect(harness.sender, playerId);
        harness.controller.inspect(harness.sender, playerId);

        assertEquals(2, harness.queue.tasks.size());
    }

    @Test
    void queueRejectionAndOperationFailureAreReportedSafely() {
        UUID playerId = UUID.randomUUID();
        RecordingOperations operations = new RecordingOperations(PlayerState.empty(playerId));
        Harness rejected = new Harness(operations);
        rejected.queue.accepting = false;

        rejected.controller.inspect(rejected.sender, playerId);
        rejected.runMain();
        assertTrue(rejected.messages.getFirst().contains("could not be queued"));

        Harness failed = new Harness(operations);
        operations.snapshotFailure = new IOException("disk unavailable");
        failed.controller.inspect(failed.sender, playerId);
        failed.runEverything();
        assertTrue(failed.messages.getFirst().contains("disk unavailable"));
        assertTrue(failed.warnings.getFirst().contains("disk unavailable"));
    }

    private static PlayerState state(UUID playerId, long revision, UUID incubationId, long remaining) {
        IncubationOutcome outcome = new IncubationOutcome(
                UUID.randomUUID(),
                "ember_fox",
                1,
                PetTier.D,
                new HeadIcon("minecraft", "stone"),
                Map.of(),
                "common",
                50,
                7,
                "test",
                List.of(),
                1000,
                Map.of());
        IncubationState incubation = new IncubationState(
                incubationId,
                "starter_egg",
                outcome,
                remaining,
                remaining == 0 ? IncubationStatus.READY : IncubationStatus.INCUBATING,
                List.of(),
                Map.of());
        return PlayerState.empty(playerId).withRevision(revision).withIncubation(incubation);
    }

    private static CommandSender sender(List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                HatchAdminControllerTest.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sendMessage") && arguments.length == 1
                            && arguments[0] instanceof String message) {
                        messages.add(message);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class || !type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new IllegalArgumentException("unsupported primitive: " + type);
    }

    private static final class Harness {
        private final RecordingQueue queue = new RecordingQueue();
        private final List<Runnable> mainTasks = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final CommandSender sender = sender(messages);
        private final HatchAdminController controller;

        private Harness(RecordingOperations operations) {
            controller = new HatchAdminController(operations, queue, mainTasks::add, warnings::add);
        }

        private void runMain() {
            List<Runnable> pending = List.copyOf(mainTasks);
            mainTasks.clear();
            pending.forEach(Runnable::run);
        }

        private void runEverything() {
            queue.runAll();
            runMain();
        }
    }

    private static final class RecordingQueue implements HatchAdminController.TaskSubmitter {
        private final List<UUID> playerIds = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();
        private boolean accepting = true;

        @Override
        public boolean submit(UUID playerId, Runnable task) {
            playerIds.add(playerId);
            if (!accepting) return false;
            tasks.add(task);
            return true;
        }

        private void runAll() {
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }

    private static final class RecordingOperations implements HatchAdminController.HatchOperations {
        private final PlayerState snapshot;
        private HatchResult result;
        private IOException snapshotFailure;
        private int mutationCalls;
        private long revision = -1;
        private long millis = -1;
        private UUID actionId;

        private RecordingOperations(PlayerState snapshot) {
            this.snapshot = snapshot;
            this.result = new HatchResult(HatchResult.Status.REDUCED, snapshot, snapshot.incubation(), null);
        }

        @Override
        public PlayerState snapshot(UUID playerId) throws IOException {
            if (snapshotFailure != null) throw snapshotFailure;
            return snapshot;
        }

        @Override
        public HatchResult reduce(
                UUID playerId, long requestedRevision, UUID incubationId, long requestedMillis, UUID requestedActionId) {
            record(requestedRevision, requestedMillis, requestedActionId);
            return result;
        }

        @Override
        public HatchResult setRemaining(
                UUID playerId, long requestedRevision, UUID incubationId, long requestedMillis, UUID requestedActionId) {
            record(requestedRevision, requestedMillis, requestedActionId);
            return result;
        }

        @Override
        public HatchResult complete(
                UUID playerId, long requestedRevision, UUID incubationId, UUID requestedActionId) {
            record(requestedRevision, 0, requestedActionId);
            return result;
        }

        @Override
        public HatchResult cancel(UUID playerId, long requestedRevision, UUID incubationId, UUID requestedActionId) {
            record(requestedRevision, 0, requestedActionId);
            return result;
        }

        private void record(long requestedRevision, long requestedMillis, UUID requestedActionId) {
            mutationCalls++;
            revision = requestedRevision;
            millis = requestedMillis;
            actionId = requestedActionId;
        }
    }
}
