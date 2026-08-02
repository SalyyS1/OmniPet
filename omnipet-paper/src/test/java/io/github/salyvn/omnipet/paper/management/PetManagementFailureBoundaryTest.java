package io.github.salyvn.omnipet.paper.management;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.progression.CultivationItemActionFileJournal;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionService;
import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;

class PetManagementFailureBoundaryTest {
    @TempDir Path temporary;
    @Test
    void rejectedRepositoryDispatchDoesNotLeaveTheViewerBusy() {
        PetManagementControllerContext context = context(task -> {
            throw new RejectedExecutionException("closed");
        }, () -> UUID.randomUUID());
        PetManagementSession session = session();
        context.sessions.put(session.viewerId(), session);
        PetManagementStateActions actions = new PetManagementStateActions(context);

        assertEquals(PetManagementOutcome.Status.ERROR,
                join(actions.favorite(session, true)).status());
        assertEquals(PetManagementOutcome.Status.ERROR,
                join(actions.favorite(session, true)).status());
    }

    @Test
    void failedReleaseIdGenerationDoesNotLeaveTheViewerBusy() {
        PetManagementControllerContext context = context(Runnable::run, () -> null);
        PetManagementSession session = session();
        context.sessions.put(session.viewerId(), session);
        PetManagementReleaseActions actions = new PetManagementReleaseActions(
                context, (ownerId, transactionId) -> {
                    throw new AssertionError("recovery is not expected");
                });

        assertEquals(PetManagementOutcome.Status.ERROR, join(actions.preview(session)).status());
        assertEquals(PetManagementOutcome.Status.ERROR, join(actions.preview(session)).status());
    }

    @Test
    void offThreadCaptureRefusalDoesNotLeaveTheViewerBusy() {
        PetManagementControllerContext context = context(Runnable::run, UUID::randomUUID);
        PetManagementSession session = session();
        context.sessions.put(session.viewerId(), session);
        PetManagementCultivationActions actions = new PetManagementCultivationActions(
                context,
                new CultivationItemActionService(new CultivationItemActionFileJournal(
                        temporary.resolve("cultivation-actions"))),
                new PetConsumableInventoryPort() {
                    @Override public CaptureResult capture(UUID viewerId, Kind kind) {
                        throw new AssertionError("capture is not expected off-thread");
                    }

                    @Override public ConsumeResult consumeOne(Capture capture) {
                        throw new AssertionError("consume is not expected off-thread");
                    }
                },
                new PetManagementMainThread() {
                    @Override public boolean isMainThread() { return false; }
                    @Override public void execute(Runnable task) { throw new AssertionError("dispatch is not expected"); }
                });

        assertEquals(PetManagementOutcome.Status.ERROR,
                join(actions.addExperience(session, progression())).status());
        assertEquals(PetManagementOutcome.Status.ERROR,
                join(actions.addExperience(session, progression())).status());
    }

    private static PetManagementControllerContext context(
            java.util.concurrent.Executor executor,
            java.util.function.Supplier<UUID> ids) {
        return new PetManagementControllerContext(
                repository(), PetManagementAuthorizationPort.ownerOnly(), executor, ids);
    }

    private static PetManagementRepositoryPort repository() {
        return (PetManagementRepositoryPort) Proxy.newProxyInstance(
                PetManagementRepositoryPort.class.getClassLoader(),
                new Class<?>[] {PetManagementRepositoryPort.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError("repository call is not expected: " + method.getName());
                });
    }

    private static PetManagementSession session() {
        UUID ownerId = UUID.randomUUID();
        return new PetManagementSession(ownerId, ownerId, UUID.randomUUID(), UUID.randomUUID(), 4);
    }

    private static ProgressionMutationContext progression() {
        return new ProgressionMutationContext(
                new ProgressionConfig(100, 100, 1, values -> 100, java.util.Map.of(),
                        ProgressionConfig.OverflowPolicy.CARRY),
                null, java.util.Map.of(), 100, 1_000);
    }

    private static PetManagementOutcome join(
            java.util.concurrent.CompletionStage<PetManagementOutcome> stage) {
        return stage.toCompletableFuture().join();
    }
}
