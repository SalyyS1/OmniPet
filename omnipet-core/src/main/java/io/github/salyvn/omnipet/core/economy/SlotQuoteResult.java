package io.github.salyvn.omnipet.core.economy;

import java.util.Objects;

public record SlotQuoteResult(Status status, SlotPurchaseQuote quote, String detail) {
    public SlotQuoteResult {
        status = Objects.requireNonNull(status, "quote status");
        quote = Objects.requireNonNull(quote, "quote");
        detail = detail == null ? "" : detail;
    }

    public boolean purchasable() {
        return status == Status.AVAILABLE;
    }

    public enum Status {
        AVAILABLE,
        NOT_NEXT_SLOT,
        ALREADY_ENTITLED,
        PROVIDER_UNAVAILABLE
    }
}
