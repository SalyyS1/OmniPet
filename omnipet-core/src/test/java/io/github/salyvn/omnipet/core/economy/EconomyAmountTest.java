package io.github.salyvn.omnipet.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class EconomyAmountTest {
    @Test
    void vaultPreservesDecimalSemantics() {
        EconomyAmount amount = EconomyAmount.vault(new BigDecimal("125.50"));

        assertEquals(EconomyProvider.VAULT, amount.provider());
        assertEquals(new BigDecimal("125.5"), amount.value());
    }

    @Test
    void vaultRejectsNonFiniteUnderflowAndJournalAmplificationValues() {
        assertThrows(IllegalArgumentException.class, () -> EconomyAmount.vault(new BigDecimal("1E+10000")));
        assertThrows(IllegalArgumentException.class, () -> EconomyAmount.vault(new BigDecimal("1E-10000")));
        assertThrows(IllegalArgumentException.class, () -> EconomyAmount.vault(new BigDecimal("0.000000001")));

        EconomyAmount maximumFinite = EconomyAmount.vault(BigDecimal.valueOf(Double.MAX_VALUE));
        assertEquals(Double.MAX_VALUE, maximumFinite.value().doubleValue());
    }

    @Test
    void vaultRejectsAmountsThatChangeWhenConvertedToDouble() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyAmount.vault(new BigDecimal("9007199254740993")));
        assertEquals(
                new BigDecimal("9007199254740992"),
                EconomyAmount.vault(new BigDecimal("9007199254740992")).value());
    }

    @Test
    void playerPointsRejectsFractionalNegativeAndOutOfRangeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> EconomyAmount.playerPoints(new BigDecimal("1.5")));
        assertThrows(IllegalArgumentException.class, () -> EconomyAmount.playerPoints(-1));
        assertThrows(IllegalArgumentException.class, () -> EconomyAmount.playerPoints(new BigDecimal("2147483648")));

        EconomyAmount amount = EconomyAmount.playerPoints(new BigDecimal("50.0"));
        assertEquals(50, amount.playerPointsValue());
    }
}
