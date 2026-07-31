package io.github.salyvn.omnipet.core.economy;

import java.math.BigDecimal;

public record EconomyBalanceResult(Status status, BigDecimal balance, String detail) {
    public EconomyBalanceResult {
        if (status == null) throw new IllegalArgumentException("balance status is required");
        if (status == Status.AVAILABLE && balance == null) {
            throw new IllegalArgumentException("available balance requires a value");
        }
        if (balance != null && balance.signum() < 0) {
            throw new IllegalArgumentException("economy balance cannot be negative");
        }
        detail = detail == null ? "" : detail;
        if (detail.length() > 512) throw new IllegalArgumentException("balance detail is too long");
    }

    public static EconomyBalanceResult available(BigDecimal balance) {
        return new EconomyBalanceResult(Status.AVAILABLE, balance, "");
    }

    public static EconomyBalanceResult unavailable(String detail) {
        return new EconomyBalanceResult(Status.UNAVAILABLE, null, detail);
    }

    public static EconomyBalanceResult unknown(String detail) {
        return new EconomyBalanceResult(Status.UNKNOWN, null, detail);
    }

    public enum Status { AVAILABLE, UNAVAILABLE, UNKNOWN }
}
