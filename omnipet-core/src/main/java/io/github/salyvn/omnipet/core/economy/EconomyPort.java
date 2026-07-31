package io.github.salyvn.omnipet.core.economy;

public interface EconomyPort {
    EconomyProvider provider();

    EconomyOperationResult withdraw(EconomyRequest request);

    EconomyOperationResult refund(EconomyRequest request);

    default EconomyBalanceResult balance(java.util.UUID playerId) {
        return EconomyBalanceResult.unavailable("provider does not expose balance lookup");
    }
}
