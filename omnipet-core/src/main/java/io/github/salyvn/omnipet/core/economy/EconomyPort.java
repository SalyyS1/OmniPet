package io.github.salyvn.omnipet.core.economy;

public interface EconomyPort {
    EconomyProvider provider();

    EconomyOperationResult withdraw(EconomyRequest request);

    EconomyOperationResult refund(EconomyRequest request);
}
