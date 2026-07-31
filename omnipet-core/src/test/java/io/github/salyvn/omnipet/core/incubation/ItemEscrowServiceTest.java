package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemEscrowServiceTest {
    @TempDir
    Path temporary;

    @Test
    void enforcesPreparedRemovedCommittedSagaAndIdempotentTransitions() throws Exception {
        UUID transactionId = UUID.randomUUID();
        EggEscrowTransaction transaction = EggEscrowTestFixtures.transaction(UUID.randomUUID(), transactionId);
        ItemEscrowService service = new ItemEscrowService(
                new FileEggEscrowJournal(temporary.resolve("escrow")));

        assertEquals(ItemEscrowResult.Status.CREATED, service.prepare(transaction).status());
        assertEquals(ItemEscrowResult.Status.ALREADY_EXISTS, service.prepare(transaction).status());
        assertEquals(ItemEscrowResult.Status.ITEM_REMOVED, service.markItemRemoved(transactionId).status());
        assertEquals(ItemEscrowResult.Status.ITEM_REMOVED, service.markItemRemoved(transactionId).status());
        assertEquals(ItemEscrowResult.Status.INVALID_TRANSITION, service.cancel(transactionId).status());
        assertEquals(ItemEscrowResult.Status.COMMITTED, service.commit(transactionId).status());
        assertEquals(ItemEscrowResult.Status.INVALID_TRANSITION, service.markRefundPending(transactionId).status());
    }

    @Test
    void supportsCancellationAndRefundRecoveryBranches() throws Exception {
        ItemEscrowService service = new ItemEscrowService(
                new FileEggEscrowJournal(temporary.resolve("escrow")));
        EggEscrowTransaction cancelled = EggEscrowTestFixtures.transaction(UUID.randomUUID(), UUID.randomUUID());
        service.prepare(cancelled);
        assertEquals(ItemEscrowResult.Status.CANCELLED, service.cancel(cancelled.transactionId()).status());

        EggEscrowTransaction refunded = EggEscrowTestFixtures.transaction(UUID.randomUUID(), UUID.randomUUID());
        service.prepare(refunded);
        service.markItemRemoved(refunded.transactionId());
        assertEquals(ItemEscrowResult.Status.REFUND_PENDING,
                service.markRefundPending(refunded.transactionId()).status());
        assertEquals(ItemEscrowResult.Status.REFUNDED, service.markRefunded(refunded.transactionId()).status());
    }

    @Test
    void concurrentPrepareAcrossJournalInstancesCreatesExactlyOnce() throws Exception {
        Path root = temporary.resolve("escrow");
        EggEscrowTransaction transaction = EggEscrowTestFixtures.transaction(UUID.randomUUID(), UUID.randomUUID());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Future<ItemEscrowResult.Status>> futures = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return new ItemEscrowService(new FileEggEscrowJournal(root)).prepare(transaction).status();
                }));
            }
            start.countDown();
            List<ItemEscrowResult.Status> statuses = new ArrayList<>();
            for (Future<ItemEscrowResult.Status> future : futures) statuses.add(future.get());

            assertEquals(1, statuses.stream().filter(status -> status == ItemEscrowResult.Status.CREATED).count());
            assertEquals(23,
                    statuses.stream().filter(status -> status == ItemEscrowResult.Status.ALREADY_EXISTS).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void competingCommitAndRefundAcrossJournalInstancesAcceptOnlyOnePath() throws Exception {
        Path root = temporary.resolve("escrow");
        EggEscrowTransaction transaction = EggEscrowTestFixtures.transaction(UUID.randomUUID(), UUID.randomUUID());
        ItemEscrowService setup = new ItemEscrowService(new FileEggEscrowJournal(root));
        setup.prepare(transaction);
        setup.markItemRemoved(transaction.transactionId());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ItemEscrowResult.Status> commit = executor.submit(() -> {
                start.await();
                return new ItemEscrowService(new FileEggEscrowJournal(root))
                        .commit(transaction.transactionId()).status();
            });
            Future<ItemEscrowResult.Status> refund = executor.submit(() -> {
                start.await();
                return new ItemEscrowService(new FileEggEscrowJournal(root))
                        .markRefundPending(transaction.transactionId()).status();
            });
            start.countDown();

            List<ItemEscrowResult.Status> statuses = List.of(commit.get(), refund.get());
            assertEquals(1,
                    statuses.stream().filter(status -> status == ItemEscrowResult.Status.INVALID_TRANSITION).count());
        } finally {
            executor.shutdownNow();
        }
    }
}
