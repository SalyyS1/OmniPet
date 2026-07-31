package io.github.salyvn.omnipet.paper.economy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.Objects;

import io.github.salyvn.omnipet.core.economy.EconomyOperationResult;
import io.github.salyvn.omnipet.core.economy.EconomyBalanceResult;
import io.github.salyvn.omnipet.core.economy.EconomyPort;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;
import io.github.salyvn.omnipet.core.economy.EconomyRequest;

final class ReflectivePlayerPointsEconomyPort implements EconomyPort {
    private final Object api;
    private final ProviderCallExecutor calls;
    private final Method take;
    private final Method give;
    private final Method look;

    ReflectivePlayerPointsEconomyPort(Object api, ProviderCallExecutor calls) {
        this.api = Objects.requireNonNull(api, "PlayerPoints API");
        this.calls = Objects.requireNonNull(calls, "provider call executor");
        this.take = method(api.getClass(), "take");
        this.give = method(api.getClass(), "give");
        try {
            this.look = api.getClass().getMethod("look", UUID.class);
        } catch (NoSuchMethodException failure) {
            throw new IllegalArgumentException("PlayerPoints method is missing: look", failure);
        }
    }

    @Override
    public EconomyProvider provider() {
        return EconomyProvider.PLAYER_POINTS;
    }

    @Override
    public EconomyOperationResult withdraw(EconomyRequest request) {
        return invoke(request, take, "PlayerPoints withdrawal");
    }

    @Override
    public EconomyOperationResult refund(EconomyRequest request) {
        return invoke(request, give, "PlayerPoints refund");
    }

    @Override
    public EconomyBalanceResult balance(UUID playerId) {
        try {
            Object response = calls.call(() -> ReflectiveEconomySupport.invoke(look, api, playerId));
            if (!(response instanceof Number number) || number.longValue() < 0) {
                return EconomyBalanceResult.unknown("PlayerPoints returned an invalid balance");
            }
            return EconomyBalanceResult.available(BigDecimal.valueOf(number.longValue()));
        } catch (Exception | LinkageError failure) {
            return EconomyBalanceResult.unknown(
                    ReflectiveEconomySupport.detail(failure, "PlayerPoints balance lookup threw"));
        }
    }

    private EconomyOperationResult invoke(EconomyRequest request, Method method, String operation) {
        if (request.amount().provider() != EconomyProvider.PLAYER_POINTS) {
            return EconomyOperationResult.failed(operation + " received a non-PlayerPoints amount");
        }
        try {
            Object response = calls.call(() -> ReflectiveEconomySupport.invoke(
                    method,
                    api,
                    request.playerId(),
                    request.amount().playerPointsValue()));
            if (!(response instanceof Boolean completed)) {
                return EconomyOperationResult.unknown(operation + " returned an incompatible response");
            }
            return completed
                    ? EconomyOperationResult.succeeded(operation + " confirmed")
                    : EconomyOperationResult.failed(operation + " rejected");
        } catch (Exception | LinkageError failure) {
            return EconomyOperationResult.unknown(ReflectiveEconomySupport.detail(failure, operation + " threw"));
        }
    }

    private static Method method(Class<?> type, String name) {
        try {
            return type.getMethod(name, java.util.UUID.class, int.class);
        } catch (NoSuchMethodException failure) {
            throw new IllegalArgumentException("PlayerPoints method is missing: " + name, failure);
        }
    }
}
