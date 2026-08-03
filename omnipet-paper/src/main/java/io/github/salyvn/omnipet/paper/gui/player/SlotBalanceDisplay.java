package io.github.salyvn.omnipet.paper.gui.player;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A provider balance as the purchase menu needs to show it.
 *
 * <p>The controller performs the provider lookup off the render path and hands over this value, so
 * the renderer owns the wording while the controller owns the I/O. Previously the controller built
 * the display string itself, which put GUI text outside the message catalog.
 */
public record SlotBalanceDisplay(BigDecimal amount, String unavailableDetail) {
    public SlotBalanceDisplay {
        if (amount == null && unavailableDetail == null) {
            throw new IllegalArgumentException("a balance display needs an amount or a reason");
        }
    }

    /** A known balance. */
    public static SlotBalanceDisplay available(BigDecimal amount) {
        return new SlotBalanceDisplay(Objects.requireNonNull(amount, "amount"), null);
    }

    /** The provider could not answer; {@code detail} explains why. */
    public static SlotBalanceDisplay unavailable(String detail) {
        return new SlotBalanceDisplay(null, detail == null || detail.isBlank() ? "unknown" : detail);
    }

    public boolean known() {
        return amount != null;
    }

    /** The plain numeric text, only valid when {@link #known()}. */
    public String plain() {
        return amount == null ? "" : amount.toPlainString();
    }
}
