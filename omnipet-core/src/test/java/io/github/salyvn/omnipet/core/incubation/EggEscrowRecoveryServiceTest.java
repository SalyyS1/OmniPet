package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

class EggEscrowRecoveryServiceTest {
    private final EggEscrowRecoveryService recovery = new EggEscrowRecoveryService();

    @Test
    void mapsEveryDurableBoundaryToAConservativeRecoveryDirective() {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        EggEscrowTransaction prepared = EggEscrowTestFixtures.transaction(playerId, transactionId);
        PlayerState started = new HatchService().start(
                PlayerState.empty(playerId), transactionId, IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(), 5, new PetStorageLimits(1, 1, true)).state();

        assertEquals(EggEscrowRecoveryDirective.REMOVE_MATCHING_ITEM,
                recovery.inspect(prepared, started, EggEscrowItemObservation.MATCHING_ITEM_PRESENT).directive());
        assertEquals(EggEscrowRecoveryDirective.CANCEL_TRANSACTION,
                recovery.inspect(prepared, PlayerState.empty(playerId),
                        EggEscrowItemObservation.MATCHING_ITEM_PRESENT).directive());
        assertEquals(EggEscrowRecoveryDirective.COMMIT_TRANSACTION,
                recovery.inspect(prepared.withStage(EggEscrowStage.ITEM_REMOVED), started,
                        EggEscrowItemObservation.MATCHING_ITEM_ABSENT).directive());
        assertEquals(EggEscrowRecoveryDirective.REFUND_ITEM,
                recovery.inspect(prepared.withStage(EggEscrowStage.ITEM_REMOVED), PlayerState.empty(playerId),
                        EggEscrowItemObservation.MATCHING_ITEM_ABSENT).directive());
        assertEquals(EggEscrowRecoveryDirective.REFUND_ITEM,
                recovery.inspect(prepared.withStage(EggEscrowStage.REFUND_PENDING), started,
                        EggEscrowItemObservation.MATCHING_ITEM_ABSENT).directive());
        assertEquals(EggEscrowRecoveryDirective.OPERATOR_REVIEW,
                recovery.inspect(prepared.withStage(EggEscrowStage.COMMITTED), PlayerState.empty(playerId),
                        EggEscrowItemObservation.AMBIGUOUS).directive());
    }

    @Test
    void preparedEscrowFailsClosedWhenTheMatchingItemIsAbsentOrAmbiguous() {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        EggEscrowTransaction prepared = EggEscrowTestFixtures.transaction(playerId, transactionId);
        PlayerState started = started(playerId, transactionId);

        assertEquals(EggEscrowRecoveryDirective.OPERATOR_REVIEW,
                recovery.inspect(prepared, started, EggEscrowItemObservation.MATCHING_ITEM_ABSENT).directive());
        assertEquals(EggEscrowRecoveryDirective.OPERATOR_REVIEW,
                recovery.inspect(prepared, started, EggEscrowItemObservation.AMBIGUOUS).directive());
        assertEquals(EggEscrowRecoveryDirective.OPERATOR_REVIEW,
                recovery.inspect(prepared, PlayerState.empty(playerId),
                        EggEscrowItemObservation.MATCHING_ITEM_ABSENT).directive());
    }

    @Test
    void terminalEscrowCancelsViableIncubationAndEscalatesClaimedConflicts() {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        EggEscrowTransaction prepared = EggEscrowTestFixtures.transaction(playerId, transactionId);
        PlayerState started = started(playerId, transactionId);
        HatchService hatch = new HatchService();
        PlayerState ready = hatch.complete(started, transactionId, UUID.randomUUID()).state();
        PlayerState claimed = hatch.claim(ready, transactionId, new PetStorageLimits(1, 1, true)).state();

        assertEquals(EggEscrowRecoveryDirective.CANCEL_INCUBATION,
                recovery.inspect(prepared.withStage(EggEscrowStage.CANCELLED), started,
                        EggEscrowItemObservation.MATCHING_ITEM_PRESENT).directive());
        assertEquals(EggEscrowRecoveryDirective.CANCEL_INCUBATION,
                recovery.inspect(prepared.withStage(EggEscrowStage.REFUNDED), ready,
                        EggEscrowItemObservation.MATCHING_ITEM_PRESENT).directive());
        assertEquals(EggEscrowRecoveryDirective.OPERATOR_REVIEW,
                recovery.inspect(prepared.withStage(EggEscrowStage.REFUNDED), claimed,
                        EggEscrowItemObservation.MATCHING_ITEM_PRESENT).directive());
    }

    @Test
    void durablePaymentStagesRequireProofThatTheMatchingItemIsAbsent() {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        EggEscrowTransaction prepared = EggEscrowTestFixtures.transaction(playerId, transactionId);
        PlayerState viable = started(playerId, transactionId);
        HatchService hatch = new HatchService();
        PlayerState ready = hatch.complete(viable, transactionId, UUID.randomUUID()).state();
        PlayerState claimed = hatch.claim(ready, transactionId, new PetStorageLimits(1, 1, true)).state();
        PlayerState[] states = {viable, PlayerState.empty(playerId), claimed};
        String[] stateNames = {"viable", "missing", "claimed"};

        for (EggEscrowStage stage : new EggEscrowStage[] {
                EggEscrowStage.ITEM_REMOVED, EggEscrowStage.REFUND_PENDING, EggEscrowStage.COMMITTED}) {
            for (EggEscrowItemObservation observation : EggEscrowItemObservation.values()) {
                for (int index = 0; index < states.length; index++) {
                    EggEscrowRecoveryDirective actual = recovery.inspect(
                            prepared.withStage(stage), states[index], observation).directive();
                    String context = stage + " / " + observation + " / " + stateNames[index];
                    if (observation != EggEscrowItemObservation.MATCHING_ITEM_ABSENT) {
                        assertEquals(EggEscrowRecoveryDirective.OPERATOR_REVIEW, actual, context);
                        assertFalse(EnumSet.of(
                                EggEscrowRecoveryDirective.COMMIT_TRANSACTION,
                                EggEscrowRecoveryDirective.REFUND_ITEM,
                                EggEscrowRecoveryDirective.NONE).contains(actual), context);
                    } else {
                        assertEquals(expectedAbsentDirective(stage, index), actual, context);
                    }
                }
            }
        }
    }

    private static EggEscrowRecoveryDirective expectedAbsentDirective(EggEscrowStage stage, int stateIndex) {
        if (stateIndex == 2) {
            return stage == EggEscrowStage.COMMITTED
                    ? EggEscrowRecoveryDirective.NONE
                    : EggEscrowRecoveryDirective.OPERATOR_REVIEW;
        }
        return switch (stage) {
            case ITEM_REMOVED -> stateIndex == 0
                    ? EggEscrowRecoveryDirective.COMMIT_TRANSACTION
                    : EggEscrowRecoveryDirective.REFUND_ITEM;
            case REFUND_PENDING -> EggEscrowRecoveryDirective.REFUND_ITEM;
            case COMMITTED -> stateIndex == 0
                    ? EggEscrowRecoveryDirective.NONE
                    : EggEscrowRecoveryDirective.OPERATOR_REVIEW;
            default -> throw new IllegalArgumentException("unexpected durable stage: " + stage);
        };
    }

    private static PlayerState started(UUID playerId, UUID transactionId) {
        return new HatchService().start(
                PlayerState.empty(playerId), transactionId, IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(), 5, new PetStorageLimits(1, 1, true)).state();
    }
}
