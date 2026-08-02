package io.github.salyvn.omnipet.paper.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.economy.EconomyBalanceResult;
import io.github.salyvn.omnipet.core.economy.EconomyOperationResult;
import io.github.salyvn.omnipet.core.economy.EconomyPort;
import io.github.salyvn.omnipet.core.economy.EconomyPortResolver;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;
import io.github.salyvn.omnipet.core.economy.EconomyRequest;
import io.github.salyvn.omnipet.core.release.ExternalRewardDeliveryPort;
import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;

class PaperReleaseExternalRewardPortTest {
    @Test
    void vaultRewardUsesDepositPathAndCachesTransactionOutcome() {
        StubPort vault = new StubPort(EconomyProvider.VAULT, EconomyOperationResult.succeeded("receipt"));
        PaperReleaseExternalRewardPort port = new PaperReleaseExternalRewardPort(
                EconomyPortResolver.fixed(Map.of(EconomyProvider.VAULT, vault)));
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        List<ReleaseRewardBundle.ExternalReward> rewards = rewards("vault", "25.50");

        ExternalRewardDeliveryPort.Outcome first = port.deliver(playerId, transactionId, rewards);
        ExternalRewardDeliveryPort.Outcome retry = port.deliver(playerId, transactionId, rewards);

        assertEquals(ExternalRewardDeliveryPort.Status.PROVEN_SUCCESS, first.status());
        assertEquals(ExternalRewardDeliveryPort.Status.PROVEN_SUCCESS, retry.status());
        assertEquals(1, vault.refunds.get());
        assertEquals(transactionId, vault.last.transactionId());
        assertEquals(new BigDecimal("25.5"), vault.last.amount().value());
    }

    @Test
    void ambiguousProviderOutcomeMapsToUnknownAndNeverBlindRetries() {
        StubPort points = new StubPort(
                EconomyProvider.PLAYER_POINTS, EconomyOperationResult.unknown("timeout after request"));
        PaperReleaseExternalRewardPort port = new PaperReleaseExternalRewardPort(
                EconomyPortResolver.fixed(Map.of(EconomyProvider.PLAYER_POINTS, points)));
        UUID transactionId = UUID.randomUUID();
        List<ReleaseRewardBundle.ExternalReward> rewards = rewards("player_points", "10");

        ExternalRewardDeliveryPort.Outcome first = port.deliver(UUID.randomUUID(), transactionId, rewards);
        ExternalRewardDeliveryPort.Outcome retry = port.deliver(
                points.last.playerId(), transactionId, rewards);

        assertEquals(ExternalRewardDeliveryPort.Status.UNKNOWN_COMMIT, first.status());
        assertEquals(ExternalRewardDeliveryPort.Status.UNKNOWN_COMMIT, retry.status());
        assertEquals(1, points.refunds.get());
    }

    @Test
    void partialMultiProviderCommitIsUnknownRatherThanFailure() {
        StubPort vault = new StubPort(EconomyProvider.VAULT, EconomyOperationResult.succeeded("vault receipt"));
        StubPort points = new StubPort(EconomyProvider.PLAYER_POINTS, EconomyOperationResult.failed("rejected"));
        PaperReleaseExternalRewardPort port = new PaperReleaseExternalRewardPort(EconomyPortResolver.fixed(Map.of(
                EconomyProvider.VAULT, vault,
                EconomyProvider.PLAYER_POINTS, points)));

        ExternalRewardDeliveryPort.Outcome result = port.deliver(
                UUID.randomUUID(), UUID.randomUUID(), List.of(
                        reward("vault", "5"), reward("player_points", "8")));

        assertEquals(ExternalRewardDeliveryPort.Status.UNKNOWN_COMMIT, result.status());
        assertEquals(1, vault.refunds.get());
        assertEquals(1, points.refunds.get());
    }

    private static List<ReleaseRewardBundle.ExternalReward> rewards(String provider, String amount) {
        return List.of(reward(provider, amount));
    }

    private static ReleaseRewardBundle.ExternalReward reward(String provider, String amount) {
        return new ReleaseRewardBundle.ExternalReward(
                provider, "coins", new BigDecimal(amount), Map.of());
    }

    private static final class StubPort implements EconomyPort {
        private final EconomyProvider provider;
        private final EconomyOperationResult result;
        private final AtomicInteger refunds = new AtomicInteger();
        private EconomyRequest last;

        private StubPort(EconomyProvider provider, EconomyOperationResult result) {
            this.provider = provider;
            this.result = result;
        }

        @Override
        public EconomyProvider provider() {
            return provider;
        }

        @Override
        public EconomyOperationResult withdraw(EconomyRequest request) {
            throw new AssertionError("release reward must not withdraw");
        }

        @Override
        public EconomyOperationResult refund(EconomyRequest request) {
            refunds.incrementAndGet();
            last = request;
            return result;
        }

        @Override
        public EconomyBalanceResult balance(UUID playerId) {
            return EconomyBalanceResult.unavailable("unused");
        }
    }
}
