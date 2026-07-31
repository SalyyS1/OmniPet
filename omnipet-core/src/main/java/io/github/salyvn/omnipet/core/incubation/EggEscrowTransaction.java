package io.github.salyvn.omnipet.core.incubation;

import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;

public record EggEscrowTransaction(
        UUID transactionId,
        UUID playerId,
        UUID incubationId,
        String eggId,
        long expectedPlayerRevision,
        EggItemIdentity item,
        EggEscrowStage stage,
        Map<String, Object> extensions) {
    public EggEscrowTransaction {
        if (transactionId == null) throw new IllegalArgumentException("egg transaction id is required");
        if (playerId == null) throw new IllegalArgumentException("egg transaction player id is required");
        if (incubationId == null) throw new IllegalArgumentException("egg incubation id is required");
        if (!transactionId.equals(incubationId)) {
            throw new IllegalArgumentException("egg transaction and incubation IDs must match");
        }
        eggId = StableId.requireValid(eggId);
        if (expectedPlayerRevision < 0) throw new IllegalArgumentException("expected player revision cannot be negative");
        if (item == null) throw new IllegalArgumentException("egg item identity is required");
        if (stage == null) throw new IllegalArgumentException("egg escrow stage is required");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(extensions, "eggEscrow.extensions");
    }

    public EggEscrowTransaction withStage(EggEscrowStage next) {
        return new EggEscrowTransaction(
                transactionId, playerId, incubationId, eggId, expectedPlayerRevision, item, next, extensions);
    }
}
