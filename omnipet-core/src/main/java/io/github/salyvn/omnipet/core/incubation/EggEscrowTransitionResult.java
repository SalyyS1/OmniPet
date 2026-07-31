package io.github.salyvn.omnipet.core.incubation;

public record EggEscrowTransitionResult(Status status, EggEscrowTransaction transaction) {
    public enum Status {
        APPLIED,
        ALREADY_AT_TARGET,
        REJECTED
    }

    public EggEscrowTransitionResult {
        if (status == null) throw new IllegalArgumentException("egg escrow transition status is required");
        if (transaction == null) throw new IllegalArgumentException("egg escrow transaction is required");
    }
}
