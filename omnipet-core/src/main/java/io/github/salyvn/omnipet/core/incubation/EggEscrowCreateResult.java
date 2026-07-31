package io.github.salyvn.omnipet.core.incubation;

public record EggEscrowCreateResult(Status status, EggEscrowTransaction transaction) {
    public enum Status {
        CREATED,
        ALREADY_EXISTS
    }

    public EggEscrowCreateResult {
        if (status == null) throw new IllegalArgumentException("egg escrow create status is required");
        if (transaction == null) throw new IllegalArgumentException("egg escrow transaction is required");
    }
}
