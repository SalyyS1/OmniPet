package io.github.salyvn.omnipet.paper.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.bukkit.OfflinePlayer;

import io.github.salyvn.omnipet.core.economy.EconomyAmount;
import io.github.salyvn.omnipet.core.economy.EconomyOperationResult;
import io.github.salyvn.omnipet.core.economy.EconomyRequest;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

class ReflectiveEconomyPortsTest {
    private static final ProviderCallExecutor DIRECT = operation -> operation.call();

    @Test
    void vaultMapsResponsesAndUsesDepositForRefunds() {
        FakeVault provider = new FakeVault();
        var port = new ReflectiveVaultEconomyPort(
                provider, Economy.class, DIRECT, ignored -> null, OfflinePlayer.class);
        EconomyRequest request = new EconomyRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                EconomyAmount.vault(new BigDecimal("25.50")));

        EconomyOperationResult withdrawal = port.withdraw(request);
        EconomyOperationResult refund = port.refund(request);

        assertEquals(EconomyOperationResult.Status.PROVEN_SUCCESS, withdrawal.status());
        assertEquals(EconomyOperationResult.Status.PROVEN_SUCCESS, refund.status());
        assertEquals(1, provider.withdrawals);
        assertEquals(1, provider.deposits);
        assertEquals(25.5d, provider.withdrawnAmount);
        assertEquals(25.5d, provider.depositedAmount);
        assertEquals(new BigDecimal("100.0"), port.balance(request.playerId()).balance());
    }

    @Test
    void vaultExceptionIsAmbiguousInsteadOfBlindlyRetryable() {
        FakeVault provider = new FakeVault();
        provider.throwOnWithdraw = true;
        var port = new ReflectiveVaultEconomyPort(
                provider, Economy.class, DIRECT, ignored -> null, OfflinePlayer.class);

        EconomyOperationResult result = port.withdraw(new EconomyRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                EconomyAmount.vault(BigDecimal.ONE)));

        assertEquals(EconomyOperationResult.Status.UNKNOWN_COMMIT, result.status());
    }

    @Test
    void vaultFailureReadsEvidenceThroughThePublicResponseContract() {
        FakeVault provider = new FakeVault();
        provider.transactionSuccess = false;
        provider.errorMessage = "insufficient funds";
        var port = new ReflectiveVaultEconomyPort(
                provider, Economy.class, DIRECT, ignored -> null, OfflinePlayer.class);

        EconomyOperationResult result = port.withdraw(new EconomyRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                EconomyAmount.vault(BigDecimal.ONE)));

        assertEquals(EconomyOperationResult.Status.PROVEN_FAILURE, result.status());
        assertTrue(result.evidence().contains("insufficient funds"));
    }

    @Test
    void playerPointsUsesUuidIntegerApi() {
        FakePlayerPoints api = new FakePlayerPoints();
        var port = new ReflectivePlayerPointsEconomyPort(api, DIRECT);
        EconomyRequest request = new EconomyRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                EconomyAmount.playerPoints(50));

        assertEquals(EconomyOperationResult.Status.PROVEN_SUCCESS, port.withdraw(request).status());
        assertEquals(EconomyOperationResult.Status.PROVEN_SUCCESS, port.refund(request).status());
        assertEquals(50, api.taken);
        assertEquals(50, api.given);
        assertEquals(new BigDecimal("900"), port.balance(request.playerId()).balance());
    }

    private static final class FakeVault implements Economy {
        int withdrawals;
        int deposits;
        double withdrawnAmount;
        double depositedAmount;
        boolean throwOnWithdraw;
        boolean transactionSuccess = true;
        String errorMessage = "";

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            withdrawals++;
            withdrawnAmount = amount;
            if (throwOnWithdraw) throw new IllegalStateException("timeout");
            return new PrivateVaultResponse(transactionSuccess, errorMessage);
        }

        @Override
        public EconomyResponse withdrawPlayer(String player, double amount) {
            throw new AssertionError("legacy String overload must not be selected");
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            deposits++;
            depositedAmount = amount;
            return new PrivateVaultResponse(transactionSuccess, errorMessage);
        }

        @Override
        public EconomyResponse depositPlayer(String player, double amount) {
            throw new AssertionError("legacy String overload must not be selected");
        }

        @Override
        public double getBalance(OfflinePlayer player) {
            return 100.0d;
        }

        @Override
        public double getBalance(String player) {
            throw new AssertionError("legacy String overload must not be selected");
        }
    }

    private static final class PrivateVaultResponse extends EconomyResponse {
        PrivateVaultResponse(boolean success, String errorMessage) {
            super(success, errorMessage);
        }

        @Override
        public boolean transactionSuccess() {
            return super.transactionSuccess();
        }
    }

    public static final class FakePlayerPoints {
        int taken;
        int given;

        public boolean take(UUID playerId, int amount) {
            taken += amount;
            return true;
        }

        public boolean give(UUID playerId, int amount) {
            given += amount;
            return true;
        }

        public int look(UUID playerId) {
            return 900;
        }
    }
}
