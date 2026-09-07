package io.github.salyvn.omnipet.core.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;

class ReleaseOutboxDeliveryServiceTest {
    @TempDir
    Path temporary;

    @Test
    void fullInventoryLeavesDurablePendingEntitlement() throws Exception {
        Released fixture = released(false);
        ReleaseOutboxDeliveryService delivery = new ReleaseOutboxDeliveryService(fixture.repository());

        InternalOutboxResult result = delivery.recoverInternal(
                fixture.playerId(), fixture.transactionId(),
                (player, transaction, rewards) -> InternalRewardDeliveryPort.Outcome.capacityFull("inventory full"));

        assertEquals(InternalOutboxResult.Status.PENDING_CAPACITY, result.status());
        // A full inventory proves nothing was handed over, so the attempt is rolled back and the entry
        // is an ordinary pending one again rather than needing an operator to look at it.
        assertEquals(ReleaseOutboxEntry.InternalState.PENDING, read(fixture).internalState());
    }

    @Test
    void restartRetryAndAcknowledgementDeliverPhysicalRewardOnce() throws Exception {
        Released fixture = released(false);
        IdempotentInternalPort port = new IdempotentInternalPort();
        ReleaseOutboxDeliveryService firstProcess = new ReleaseOutboxDeliveryService(fixture.repository());

        InternalOutboxResult first = firstProcess.recoverInternal(fixture.playerId(), fixture.transactionId(), port);
        ReleaseOutboxDeliveryService restarted = new ReleaseOutboxDeliveryService(
                new FilePlayerStateRepository(fixture.root()));
        InternalOutboxResult second = restarted.recoverInternal(fixture.playerId(), fixture.transactionId(), port);

        assertEquals(InternalOutboxResult.Status.ACKNOWLEDGED, first.status());
        assertEquals(InternalOutboxResult.Status.ACKNOWLEDGED, second.status());
        assertEquals(1, port.invocations.get());
        assertEquals(1, port.physicalDeliveries.get());
    }

    @Test
    void anIntentThatCannotBePersistedDeliversNothingAndRetriesCleanly() throws Exception {
        Released fixture = released(false);
        IdempotentInternalPort port = new IdempotentInternalPort();
        PlayerStateRepository failIntent = new FailBeforeWriteRepository(fixture.repository());

        InternalOutboxResult first = new ReleaseOutboxDeliveryService(failIntent)
                .recoverInternal(fixture.playerId(), fixture.transactionId(), port);
        InternalOutboxResult recovered = new ReleaseOutboxDeliveryService(fixture.repository())
                .recoverInternal(fixture.playerId(), fixture.transactionId(), port);

        // The intent write is what fails here, before the port is ever called, so there is nothing to be
        // unsure about: the entry is untouched and the next attempt is an ordinary first attempt.
        assertEquals(InternalOutboxResult.Status.PENDING_FAILURE, first.status());
        assertEquals(InternalOutboxResult.Status.ACKNOWLEDGED, recovered.status());
        assertEquals(1, port.invocations.get());
        assertEquals(1, port.physicalDeliveries.get());
    }

    @Test
    void aLostAcknowledgementLeavesTheAttemptRecordedAndIsNeverBlindRetried() throws Exception {
        Released fixture = released(false);
        IdempotentInternalPort port = new IdempotentInternalPort();
        // Write 1 is the delivery intent, write 2 is the acknowledgement: the crash lands between the
        // rewards reaching the player and the record saying so.
        ReleaseOutboxDeliveryService crashing = new ReleaseOutboxDeliveryService(
                new FailOnSecondWriteRepository(fixture.repository()));

        InternalOutboxResult first = crashing.recoverInternal(fixture.playerId(), fixture.transactionId(), port);
        assertEquals(InternalOutboxResult.Status.ACK_PERSIST_FAILED, first.status());
        assertEquals(ReleaseOutboxEntry.InternalState.ATTEMPTING, read(fixture).internalState(),
                "the attempt must be on disk, or recovery cannot tell it happened");

        InternalOutboxResult recovered = new ReleaseOutboxDeliveryService(fixture.repository())
                .recoverInternal(fixture.playerId(), fixture.transactionId(), port);

        // The items may already be in the player's inventory. Handing them over again would pay twice,
        // so recovery stops and says so rather than guessing.
        assertEquals(InternalOutboxResult.Status.PENDING_FAILURE, recovered.status());
        assertEquals(1, port.invocations.get(), "an interrupted delivery must not be replayed");
        assertEquals(1, port.physicalDeliveries.get());
    }

    @Test
    void anInterruptedInternalAttemptStaysVisibleToAnOperator() throws Exception {
        Released fixture = released(false);
        IdempotentInternalPort port = new IdempotentInternalPort();
        new ReleaseOutboxDeliveryService(new FailOnSecondWriteRepository(fixture.repository()))
                .recoverInternal(fixture.playerId(), fixture.transactionId(), port);

        List<ReleaseOutboxEntry> pending = new ReleaseOutboxDeliveryService(fixture.repository())
                .pending(fixture.playerId(), 10);

        assertEquals(List.of(fixture.transactionId()),
                pending.stream().map(ReleaseOutboxEntry::transactionId).toList(),
                "an interrupted attempt is exactly what an operator needs to find");
    }

    @Test
    void ambiguousExternalOutcomeIsPersistedAndNeverBlindRetried() throws Exception {
        Released fixture = released(true);
        AtomicInteger calls = new AtomicInteger();
        ExternalRewardDeliveryPort port = (player, transaction, rewards) -> {
            calls.incrementAndGet();
            return ExternalRewardDeliveryPort.Outcome.unknown("provider timeout after request");
        };
        ReleaseOutboxDeliveryService delivery = new ReleaseOutboxDeliveryService(fixture.repository());

        ExternalReleaseResult first = delivery.attemptExternal(fixture.playerId(), fixture.transactionId(), port);
        ExternalReleaseResult retry = delivery.attemptExternal(fixture.playerId(), fixture.transactionId(), port);
        ExternalReleaseResult recovered = delivery.recoverExternal(fixture.playerId(), fixture.transactionId());

        assertEquals(ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, first.status());
        assertEquals(ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, retry.status());
        assertEquals(ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, recovered.status());
        assertEquals(1, calls.get());
        assertEquals(ReleaseOutboxEntry.ExternalState.UNKNOWN_REQUIRES_RECONCILIATION,
                read(fixture).externalState());
    }

    @Test
    void explicitExternalReconciliationDoesNotCallProvider() throws Exception {
        Released fixture = released(true);
        ReleaseOutboxDeliveryService delivery = new ReleaseOutboxDeliveryService(fixture.repository());
        delivery.attemptExternal(fixture.playerId(), fixture.transactionId(),
                (player, transaction, rewards) -> ExternalRewardDeliveryPort.Outcome.unknown("unknown"));

        ExternalReleaseResult reconciled = delivery.reconcileExternal(
                fixture.playerId(), fixture.transactionId(), ExternalReconciliation.CONFIRM_DELIVERED,
                "operator ledger proof");

        assertEquals(ExternalReleaseResult.Status.DELIVERED, reconciled.status());
        assertEquals("operator ledger proof", read(fixture).externalEvidence());
    }

    @Test
    void lostExternalAcknowledgementBecomesUnknownWithoutProviderReplay() throws Exception {
        Released fixture = released(true);
        AtomicInteger calls = new AtomicInteger();
        ExternalRewardDeliveryPort port = (player, transaction, rewards) -> {
            calls.incrementAndGet();
            return ExternalRewardDeliveryPort.Outcome.succeeded("provider receipt");
        };
        ReleaseOutboxDeliveryService firstProcess = new ReleaseOutboxDeliveryService(
                new FailOnSecondWriteRepository(fixture.repository()));

        ExternalReleaseResult first = firstProcess.attemptExternal(fixture.playerId(), fixture.transactionId(), port);
        ReleaseOutboxDeliveryService restarted = new ReleaseOutboxDeliveryService(fixture.repository());
        ExternalReleaseResult retry = restarted.attemptExternal(fixture.playerId(), fixture.transactionId(), port);
        ExternalReleaseResult recovered = restarted.recoverExternal(fixture.playerId(), fixture.transactionId());

        assertEquals(ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, first.status());
        assertEquals(ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, retry.status());
        assertEquals(ExternalReleaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, recovered.status());
        assertEquals(1, calls.get());
        assertEquals(ReleaseOutboxEntry.ExternalState.UNKNOWN_REQUIRES_RECONCILIATION,
                read(fixture).externalState());
    }

    @Test
    void copiedOutboxTokenCannotBeRedeemedByAnotherPlayerIdentity() throws Exception {
        Released fixture = released(false);
        ReleaseOutboxEntry copied = read(fixture);
        UUID otherPlayer = UUID.randomUUID();
        FilePlayerStateRepository otherRepository = new FilePlayerStateRepository(temporary.resolve("other-player"));
        ReleaseOutboxStore store = new ReleaseOutboxStore();
        otherRepository.withLocked(otherPlayer, 0, state -> store.update(state, copied));
        AtomicInteger calls = new AtomicInteger();

        InternalOutboxResult result = new ReleaseOutboxDeliveryService(otherRepository).recoverInternal(
                otherPlayer, fixture.transactionId(), (player, transaction, rewards) -> {
                    calls.incrementAndGet();
                    return InternalRewardDeliveryPort.Outcome.delivered("should not run");
                });

        assertEquals(InternalOutboxResult.Status.IDENTITY_MISMATCH, result.status());
        assertEquals(0, calls.get());
    }

    @Test
    void pendingFiltersTerminalRowsBeforeApplyingTheBoundedLimit() throws Exception {
        UUID playerId = UUID.randomUUID();
        FilePlayerStateRepository repository = new FilePlayerStateRepository(
                temporary.resolve("pending-filter"));
        ReleaseOutboxStore store = new ReleaseOutboxStore();
        ReleaseOutboxEntry terminalInternal = entry(
                "00000000-0000-0000-0000-000000000001", playerId,
                ReleaseOutboxEntry.InternalState.ACKNOWLEDGED,
                ReleaseOutboxEntry.ExternalState.NOT_REQUIRED);
        ReleaseOutboxEntry terminalExternal = entry(
                "00000000-0000-0000-0000-000000000002", playerId,
                ReleaseOutboxEntry.InternalState.ACKNOWLEDGED,
                ReleaseOutboxEntry.ExternalState.DELIVERED);
        ReleaseOutboxEntry pendingInternal = entry(
                "00000000-0000-0000-0000-000000000003", playerId,
                ReleaseOutboxEntry.InternalState.PENDING,
                ReleaseOutboxEntry.ExternalState.NOT_REQUIRED);
        ReleaseOutboxEntry pendingExternal = entry(
                "00000000-0000-0000-0000-000000000004", playerId,
                ReleaseOutboxEntry.InternalState.ACKNOWLEDGED,
                ReleaseOutboxEntry.ExternalState.PENDING);
        ReleaseOutboxEntry attemptingExternal = entry(
                "00000000-0000-0000-0000-000000000005", playerId,
                ReleaseOutboxEntry.InternalState.ACKNOWLEDGED,
                ReleaseOutboxEntry.ExternalState.ATTEMPTING);
        ReleaseOutboxEntry unknownExternal = entry(
                "00000000-0000-0000-0000-000000000006", playerId,
                ReleaseOutboxEntry.InternalState.ACKNOWLEDGED,
                ReleaseOutboxEntry.ExternalState.UNKNOWN_REQUIRES_RECONCILIATION);
        ReleaseOutboxEntry failedExternal = entry(
                "00000000-0000-0000-0000-000000000007", playerId,
                ReleaseOutboxEntry.InternalState.ACKNOWLEDGED,
                ReleaseOutboxEntry.ExternalState.FAILED);
        repository.withLocked(playerId, 0, state -> {
            PlayerState updated = state;
            for (ReleaseOutboxEntry entry : List.of(
                    terminalInternal, terminalExternal, pendingInternal, pendingExternal,
                    attemptingExternal, unknownExternal, failedExternal)) {
                updated = store.update(updated, entry);
            }
            return updated;
        });
        ReleaseOutboxDeliveryService delivery = new ReleaseOutboxDeliveryService(repository);

        assertEquals(
                List.of(pendingInternal.transactionId(), pendingExternal.transactionId()),
                delivery.pending(playerId, 2).stream().map(ReleaseOutboxEntry::transactionId).toList());
        assertEquals(
                List.of(
                        pendingInternal.transactionId(), pendingExternal.transactionId(),
                        attemptingExternal.transactionId(), unknownExternal.transactionId()),
                delivery.pending(playerId, 10).stream().map(ReleaseOutboxEntry::transactionId).toList());
    }

    private Released released(boolean external) throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Path root = temporary.resolve(playerId.toString());
        FilePlayerStateRepository repository = new FilePlayerStateRepository(root);
        repository.withLocked(playerId, 0, state -> state.withStorage(
                List.of(new PetInstance(petId, "ember_fox", 1, Map.of(), Map.of())),
                10, 1, List.of(), List.of()));
        ReleaseRewardBundle rewards = new ReleaseRewardBundle(
                List.of(new ReleaseRewardBundle.InternalReward("material", 2, Map.of("type", "DIAMOND"))),
                external
                        ? List.of(new ReleaseRewardBundle.ExternalReward(
                                "vault", "coins", BigDecimal.TEN, Map.of()))
                        : List.of());
        ReleaseService service = new ReleaseService(repository, ignored -> rewards);
        service.release(service.preview(transactionId, playerId, petId).preview());
        return new Released(playerId, transactionId, root, repository);
    }

    private static ReleaseOutboxEntry read(Released fixture) throws Exception {
        ReleaseOutboxStore store = new ReleaseOutboxStore();
        return store.find(fixture.repository().snapshot(fixture.playerId()), fixture.transactionId()).orElseThrow();
    }

    private static ReleaseOutboxEntry entry(
            String transactionId,
            UUID playerId,
            ReleaseOutboxEntry.InternalState internal,
            ReleaseOutboxEntry.ExternalState external) {
        return new ReleaseOutboxEntry(
                UUID.fromString(transactionId),
                playerId,
                UUID.randomUUID(),
                "fingerprint",
                "preview-token",
                new ReleaseRewardBundle(List.of(), List.of()),
                internal,
                external,
                "",
                Map.of());
    }

    private record Released(
            UUID playerId,
            UUID transactionId,
            Path root,
            FilePlayerStateRepository repository) {}

    private static final class IdempotentInternalPort implements InternalRewardDeliveryPort {
        private final Set<UUID> delivered = new HashSet<>();
        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicInteger physicalDeliveries = new AtomicInteger();

        @Override
        public Outcome deliver(UUID playerId, UUID transactionId, List<ReleaseRewardBundle.InternalReward> rewards) {
            invocations.incrementAndGet();
            if (delivered.add(transactionId)) physicalDeliveries.incrementAndGet();
            return Outcome.delivered("mailbox accepted");
        }
    }

    private static final class FailBeforeWriteRepository implements PlayerStateRepository {
        private final PlayerStateRepository delegate;
        private boolean failed;

        private FailBeforeWriteRepository(PlayerStateRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public PlayerState snapshot(UUID playerId) throws IOException {
            return delegate.snapshot(playerId);
        }

        @Override
        public PlayerState withLocked(
                UUID playerId,
                long expectedRevision,
                UnaryOperator<PlayerState> mutation) throws IOException {
            if (!failed) {
                failed = true;
                throw new IOException("simulated acknowledgement write failure");
            }
            return delegate.withLocked(playerId, expectedRevision, mutation);
        }
    }

    private static final class FailOnSecondWriteRepository implements PlayerStateRepository {
        private final PlayerStateRepository delegate;
        private int writes;

        private FailOnSecondWriteRepository(PlayerStateRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public PlayerState snapshot(UUID playerId) throws IOException {
            return delegate.snapshot(playerId);
        }

        @Override
        public PlayerState withLocked(
                UUID playerId,
                long expectedRevision,
                UnaryOperator<PlayerState> mutation) throws IOException {
            writes++;
            if (writes == 2) throw new IOException("simulated external acknowledgement write failure");
            return delegate.withLocked(playerId, expectedRevision, mutation);
        }
    }
}
