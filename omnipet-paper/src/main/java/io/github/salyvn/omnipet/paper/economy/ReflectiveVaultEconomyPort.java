package io.github.salyvn.omnipet.paper.economy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import io.github.salyvn.omnipet.core.economy.EconomyOperationResult;
import io.github.salyvn.omnipet.core.economy.EconomyBalanceResult;
import io.github.salyvn.omnipet.core.economy.EconomyPort;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;
import io.github.salyvn.omnipet.core.economy.EconomyRequest;

final class ReflectiveVaultEconomyPort implements EconomyPort {
    private final Object provider;
    private final ProviderCallExecutor calls;
    private final Function<UUID, Object> playerResolver;
    private final Method withdraw;
    private final Method deposit;
    private final Method balance;
    private final Method transactionSuccess;
    private final Field errorMessage;

    ReflectiveVaultEconomyPort(
            Object provider,
            Class<?> providerApiType,
            ProviderCallExecutor calls,
            Function<UUID, Object> playerResolver,
            Class<?> playerType) {
        this.provider = Objects.requireNonNull(provider, "Vault economy provider");
        Objects.requireNonNull(providerApiType, "Vault economy API type");
        this.calls = Objects.requireNonNull(calls, "provider call executor");
        this.playerResolver = Objects.requireNonNull(playerResolver, "offline player resolver");
        if (!providerApiType.isInstance(provider)) {
            throw new IllegalArgumentException("registered provider does not implement Vault Economy");
        }
        this.withdraw = ReflectiveEconomySupport.binaryAmountMethod(
                providerApiType, "withdrawPlayer", playerType, double.class);
        this.deposit = ReflectiveEconomySupport.binaryAmountMethod(
                providerApiType, "depositPlayer", playerType, double.class);
        this.balance = ReflectiveEconomySupport.unaryMethod(providerApiType, "getBalance", playerType);
        Class<?> responseApiType = withdraw.getReturnType();
        if (deposit.getReturnType() != responseApiType) {
            throw new IllegalArgumentException("Vault transaction response types do not match");
        }
        try {
            this.transactionSuccess = responseApiType.getMethod("transactionSuccess");
            this.errorMessage = responseApiType.getField("errorMessage");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("Vault economy response API is incompatible", failure);
        }
    }

    @Override
    public EconomyProvider provider() {
        return EconomyProvider.VAULT;
    }

    @Override
    public EconomyOperationResult withdraw(EconomyRequest request) {
        return invoke(request, withdraw, "Vault withdrawal");
    }

    @Override
    public EconomyOperationResult refund(EconomyRequest request) {
        return invoke(request, deposit, "Vault refund");
    }

    @Override
    public EconomyBalanceResult balance(UUID playerId) {
        try {
            Object response = calls.call(() -> ReflectiveEconomySupport.invoke(
                    balance,
                    provider,
                    playerResolver.apply(playerId)));
            if (!(response instanceof Number number)) {
                return EconomyBalanceResult.unknown("Vault returned an incompatible balance");
            }
            double value = number.doubleValue();
            if (!Double.isFinite(value) || value < 0) {
                return EconomyBalanceResult.unknown("Vault returned an invalid balance");
            }
            return EconomyBalanceResult.available(BigDecimal.valueOf(value));
        } catch (Exception | LinkageError failure) {
            return EconomyBalanceResult.unknown(
                    ReflectiveEconomySupport.detail(failure, "Vault balance lookup threw"));
        }
    }

    private EconomyOperationResult invoke(EconomyRequest request, Method method, String operation) {
        if (request.amount().provider() != EconomyProvider.VAULT) {
            return EconomyOperationResult.failed(operation + " received a non-Vault amount");
        }
        try {
            Object response = calls.call(() -> ReflectiveEconomySupport.invoke(
                    method,
                    provider,
                    playerResolver.apply(request.playerId()),
                    request.amount().value().doubleValue()));
            if (response == null) return EconomyOperationResult.unknown(operation + " returned no response");
            boolean completed = Boolean.TRUE.equals(
                    ReflectiveEconomySupport.invoke(transactionSuccess, response));
            String evidence = responseEvidence(response, operation);
            return completed
                    ? EconomyOperationResult.succeeded(evidence)
                    : EconomyOperationResult.failed(evidence);
        } catch (Exception | LinkageError failure) {
            return EconomyOperationResult.unknown(ReflectiveEconomySupport.detail(failure, operation + " threw"));
        }
    }

    private String responseEvidence(Object response, String operation) {
        try {
            Object error = errorMessage.get(response);
            if (error instanceof String message && !message.isBlank()) {
                return ReflectiveEconomySupport.detail(new IllegalStateException(message), operation + " failed");
            }
        } catch (IllegalAccessException failure) {
            return ReflectiveEconomySupport.detail(failure, operation + " response was inaccessible");
        }
        return operation + " response received";
    }
}
