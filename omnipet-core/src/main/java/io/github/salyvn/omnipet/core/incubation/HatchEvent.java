package io.github.salyvn.omnipet.core.incubation;

import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;

/** Reports a persisted state transition, not external item or payment settlement. */
public record HatchEvent(
        UUID playerId,
        UUID incubationId,
        Long oldRemainingActiveMillis,
        Long newRemainingActiveMillis,
        IncubationStatus oldStatus,
        IncubationStatus newStatus,
        HatchResult.Status reason,
        DeliveryStage deliveryStage,
        PetInstance claimedPet) {
    public enum DeliveryStage {
        STATE_PERSISTED
    }

    public HatchEvent {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        if (incubationId == null) throw new IllegalArgumentException("incubation id is required");
        validateState("old", oldRemainingActiveMillis, oldStatus);
        validateState("new", newRemainingActiveMillis, newStatus);
        if (oldStatus == null && newStatus == null) {
            throw new IllegalArgumentException("at least one incubation state is required");
        }
        if (reason == null) throw new IllegalArgumentException("hatch event reason is required");
        if (deliveryStage == null) throw new IllegalArgumentException("hatch event delivery stage is required");
    }

    private static void validateState(String label, Long remainingActiveMillis, IncubationStatus status) {
        if ((remainingActiveMillis == null) != (status == null)) {
            throw new IllegalArgumentException(label + " incubation time and status must both be present or absent");
        }
        if (remainingActiveMillis != null && remainingActiveMillis < 0) {
            throw new IllegalArgumentException(label + " remaining active time cannot be negative");
        }
    }
}
