package io.github.salyvn.omnipet.core.incubation;

public record EggEscrowRecovery(
        EggEscrowRecoveryDirective directive,
        EggEscrowTransaction transaction,
        String reason) {
    public EggEscrowRecovery {
        if (directive == null) throw new IllegalArgumentException("egg escrow recovery directive is required");
        if (transaction == null) throw new IllegalArgumentException("egg escrow transaction is required");
        reason = reason == null ? "" : reason;
    }
}
