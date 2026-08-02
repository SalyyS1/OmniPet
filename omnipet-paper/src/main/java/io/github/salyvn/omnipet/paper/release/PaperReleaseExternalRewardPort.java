package io.github.salyvn.omnipet.paper.release;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.github.salyvn.omnipet.core.economy.EconomyAmount;
import io.github.salyvn.omnipet.core.economy.EconomyOperationResult;
import io.github.salyvn.omnipet.core.economy.EconomyPort;
import io.github.salyvn.omnipet.core.economy.EconomyPortResolver;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;
import io.github.salyvn.omnipet.core.economy.EconomyRequest;
import io.github.salyvn.omnipet.core.release.ExternalRewardDeliveryPort;
import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;

public final class PaperReleaseExternalRewardPort implements ExternalRewardDeliveryPort {
    private static final int RELEASE_SENTINEL_SLOT = 2;
    private final EconomyPortResolver providers;
    private final ConcurrentHashMap<UUID, Attempt> attempts = new ConcurrentHashMap<>();

    public PaperReleaseExternalRewardPort(EconomyPortResolver providers) {
        this.providers = Objects.requireNonNull(providers, "economy provider resolver");
    }

    @Override
    public Outcome deliver(
            UUID playerId,
            UUID transactionId,
            List<ReleaseRewardBundle.ExternalReward> rewards) {
        List<ReleaseRewardBundle.ExternalReward> frozen = List.copyOf(rewards);
        Attempt marker = new Attempt(playerId, frozen, null);
        Attempt existing = attempts.putIfAbsent(transactionId, marker);
        if (existing != null) {
            if (!existing.playerId().equals(playerId) || !existing.rewards().equals(frozen)) {
                return Outcome.unknown("external release transaction identity mismatch");
            }
            return existing.outcome() == null
                    ? Outcome.unknown("external release transaction is already in flight")
                    : existing.outcome();
        }
        Outcome outcome = deliverOnce(playerId, transactionId, frozen);
        attempts.put(transactionId, new Attempt(playerId, frozen, outcome));
        return outcome;
    }

    private Outcome deliverOnce(
            UUID playerId,
            UUID transactionId,
            List<ReleaseRewardBundle.ExternalReward> rewards) {
        EnumMap<EconomyProvider, BigDecimal> totals;
        try {
            totals = aggregate(rewards);
        } catch (RuntimeException invalid) {
            return Outcome.failed(detail(invalid, "external release reward is invalid"));
        }
        EnumMap<EconomyProvider, EconomyPort> ports = new EnumMap<>(EconomyProvider.class);
        for (EconomyProvider provider : totals.keySet()) {
            EconomyPort port = providers.find(provider).orElse(null);
            if (port == null) return Outcome.failed(provider + " reward provider is unavailable");
            ports.put(provider, port);
        }

        boolean committedAny = false;
        StringBuilder evidence = new StringBuilder();
        for (Map.Entry<EconomyProvider, BigDecimal> reward : totals.entrySet()) {
            EconomyOperationResult result;
            try {
                EconomyAmount amount = new EconomyAmount(reward.getKey(), reward.getValue());
                result = ports.get(reward.getKey()).refund(
                        new EconomyRequest(transactionId, playerId, RELEASE_SENTINEL_SLOT, amount));
                if (result == null) result = EconomyOperationResult.unknown("provider returned no result");
            } catch (RuntimeException | LinkageError failure) {
                result = EconomyOperationResult.unknown(detail(failure, "external reward provider threw"));
            }
            append(evidence, reward.getKey() + ": " + result.evidence());
            if (result.ambiguous()) return Outcome.unknown(limited(evidence.toString()));
            if (!result.provenSuccess()) {
                return committedAny
                        ? Outcome.unknown(limited(evidence + "; partial provider commit requires reconciliation"))
                        : Outcome.failed(limited(evidence.toString()));
            }
            committedAny = true;
        }
        return Outcome.succeeded(limited(evidence.length() == 0 ? "no external release rewards" : evidence.toString()));
    }

    private static EnumMap<EconomyProvider, BigDecimal> aggregate(
            List<ReleaseRewardBundle.ExternalReward> rewards) {
        EnumMap<EconomyProvider, BigDecimal> totals = new EnumMap<>(EconomyProvider.class);
        for (ReleaseRewardBundle.ExternalReward reward : rewards) {
            EconomyProvider provider = provider(reward.provider());
            totals.merge(provider, reward.amount(), BigDecimal::add);
        }
        totals.forEach(EconomyAmount::new);
        return totals;
    }

    private static EconomyProvider provider(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "vault" -> EconomyProvider.VAULT;
            case "player_points", "playerpoints" -> EconomyProvider.PLAYER_POINTS;
            default -> throw new IllegalArgumentException("unsupported external reward provider: " + value);
        };
    }

    private static void append(StringBuilder target, String detail) {
        if (target.length() > 0) target.append("; ");
        target.append(detail);
    }

    private static String detail(Throwable failure, String fallback) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? fallback : fallback + ": " + message;
    }

    private static String limited(String value) {
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private record Attempt(
            UUID playerId,
            List<ReleaseRewardBundle.ExternalReward> rewards,
            Outcome outcome) {}
}
