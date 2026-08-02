package io.github.salyvn.omnipet.paper.management;

import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryMutationResult;

public interface PetConsumableInventoryPort {
    CaptureResult capture(UUID viewerId, Kind kind);

    ConsumeResult consumeOne(Capture capture);

    default EggEscrowItemObservation observe(UUID playerId, EggItemIdentity item) {
        return EggEscrowItemObservation.AMBIGUOUS;
    }

    default EggInventoryMutationResult removeOne(UUID playerId, EggItemIdentity item) {
        return EggInventoryMutationResult.FAILED;
    }

    default EggInventoryMutationResult refundOne(UUID playerId, EggItemIdentity item) {
        return EggInventoryMutationResult.FAILED;
    }

    enum Kind { EXPERIENCE_CANDY, BREAKTHROUGH_STONE }

    record Capture(
            UUID viewerId,
            EggItemIdentity item,
            Kind kind,
            double experienceAmount,
            int requiredLevel,
            int requiredEvolution) {
        public Capture {
            Objects.requireNonNull(viewerId, "consumable viewer ID");
            Objects.requireNonNull(item, "consumable item identity");
            Objects.requireNonNull(kind, "consumable kind");
            if (kind == Kind.EXPERIENCE_CANDY
                    && (!Double.isFinite(experienceAmount) || experienceAmount <= 0)) {
                throw new IllegalArgumentException("experience candy amount must be positive and finite");
            }
            if (kind == Kind.BREAKTHROUGH_STONE && (requiredLevel < 1 || requiredEvolution < 0)) {
                throw new IllegalArgumentException("breakthrough requirements are invalid");
            }
        }

        public Capture(
                UUID viewerId,
                UUID nonce,
                String fingerprint,
                int slot,
                Kind kind,
                double experienceAmount,
                int requiredLevel,
                int requiredEvolution) {
            this(viewerId, new EggItemIdentity(
                    slot, EggInventoryHand.MAIN_HAND, "minecraft:paper", nonce, fingerprint, 1, java.util.Map.of()),
                    kind, experienceAmount, requiredLevel, requiredEvolution);
        }

        public UUID nonce() { return item.itemNonce(); }
        public String fingerprint() { return item.fingerprint(); }
        public int slot() { return item.inventorySlot(); }
    }

    record CaptureResult(Status status, Capture capture, String detail) {
        public CaptureResult {
            Objects.requireNonNull(status, "consumable capture status");
            detail = detail == null ? "" : detail;
            if (status == Status.CAPTURED && capture == null) {
                throw new IllegalArgumentException("captured consumable receipt is required");
            }
        }

        public enum Status { CAPTURED, NOT_FOUND, AMBIGUOUS, OFFLINE }
    }

    record ConsumeResult(Status status, String detail) {
        public ConsumeResult {
            Objects.requireNonNull(status, "consumable mutation status");
            detail = detail == null ? "" : detail;
        }

        public enum Status { CONSUMED, ALREADY_CONSUMED, NOT_MATCHING, AMBIGUOUS, OFFLINE }
    }
}
