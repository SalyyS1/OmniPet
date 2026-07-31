package io.github.salyvn.omnipet.core.economy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Provider-aware price constrained to values adapters and the journal can represent safely. */
public record EconomyAmount(EconomyProvider provider, BigDecimal value) {
    private static final BigDecimal MAX_PLAYER_POINTS = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final int MAX_VAULT_SCALE = 8;
    private static final int MAX_SERIALIZED_LENGTH = 384;

    public EconomyAmount {
        provider = Objects.requireNonNull(provider, "economy provider");
        value = Objects.requireNonNull(value, "economy amount");
        if (value.signum() < 0) throw new IllegalArgumentException("economy amount cannot be negative");
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > 0 && provider == EconomyProvider.PLAYER_POINTS) {
            throw new IllegalArgumentException("PlayerPoints amount must be a non-fractional integer");
        }
        if (provider == EconomyProvider.PLAYER_POINTS && value.compareTo(MAX_PLAYER_POINTS) > 0) {
            throw new IllegalArgumentException("PlayerPoints amount exceeds the supported range");
        }
        if (provider == EconomyProvider.VAULT) validateVault(normalized);
        value = normalized;
        if (value.signum() == 0) value = BigDecimal.ZERO;
    }

    public static EconomyAmount vault(BigDecimal amount) {
        return new EconomyAmount(EconomyProvider.VAULT, amount);
    }

    public static EconomyAmount playerPoints(long amount) {
        return new EconomyAmount(EconomyProvider.PLAYER_POINTS, BigDecimal.valueOf(amount));
    }

    public static EconomyAmount playerPoints(BigDecimal amount) {
        return new EconomyAmount(EconomyProvider.PLAYER_POINTS, amount);
    }

    public int playerPointsValue() {
        if (provider != EconomyProvider.PLAYER_POINTS) {
            throw new IllegalStateException("amount is not PlayerPoints");
        }
        BigInteger integer = value.toBigIntegerExact();
        return integer.intValueExact();
    }

    private static void validateVault(BigDecimal amount) {
        if (amount.scale() > MAX_VAULT_SCALE) {
            throw new IllegalArgumentException("Vault amount has more than " + MAX_VAULT_SCALE + " decimal places");
        }
        double providerValue = amount.doubleValue();
        if (!Double.isFinite(providerValue) || (amount.signum() > 0 && providerValue == 0.0d)) {
            throw new IllegalArgumentException("Vault amount is outside the provider-safe double range");
        }
        if (amount.toPlainString().length() > MAX_SERIALIZED_LENGTH) {
            throw new IllegalArgumentException("Vault amount is too large to serialize safely");
        }
    }
}
