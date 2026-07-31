package io.github.salyvn.omnipet.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.economy.SlotReconciliationDecision;

class SlotTransactionAdminCommandParserTest {
    @Test
    void parsesBoundedListAndExplicitDecision() {
        assertEquals(
                new SlotTransactionAdminCommandParser.ListPending(20),
                SlotTransactionAdminCommandParser.parse(List.of("admin", "transactions")));
        assertEquals(
                new SlotTransactionAdminCommandParser.ListPending(50),
                SlotTransactionAdminCommandParser.parse(List.of("admin", "transactions", "50")));
        assertEquals(
                new SlotTransactionAdminCommandParser.ListPending(
                        10,
                        "v1.a.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                SlotTransactionAdminCommandParser.parse(List.of(
                        "admin",
                        "transactions",
                        "10",
                        "v1.a.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));

        UUID transactionId = UUID.randomUUID();
        assertEquals(
                new SlotTransactionAdminCommandParser.Reconcile(
                        transactionId,
                        SlotReconciliationDecision.NO_CHARGE_CONFIRMED),
                SlotTransactionAdminCommandParser.parse(List.of(
                        "admin", "reconcile", transactionId.toString(), "no-charge")));
        assertEquals(
                new SlotTransactionAdminCommandParser.Reconcile(
                        transactionId,
                        SlotReconciliationDecision.ENTITLEMENT_SYNC_RETRY),
                SlotTransactionAdminCommandParser.parse(List.of(
                        "admin", "reconcile", transactionId.toString(), "sync")));
    }

    @Test
    void rejectsUnsafeArgumentsWithoutStealingOtherAdminBranches() {
        assertInstanceOf(
                SlotTransactionAdminCommandParser.Invalid.class,
                SlotTransactionAdminCommandParser.parse(List.of("admin", "transactions", "500")));
        assertInstanceOf(
                SlotTransactionAdminCommandParser.Invalid.class,
                SlotTransactionAdminCommandParser.parse(List.of(
                        "admin", "transactions", "20", "not-a-cursor")));
        assertInstanceOf(
                SlotTransactionAdminCommandParser.Invalid.class,
                SlotTransactionAdminCommandParser.parse(List.of(
                        "admin",
                        "transactions",
                        "20",
                        "v1.a.b0000000000000000000000000000000")));
        assertInstanceOf(
                SlotTransactionAdminCommandParser.Invalid.class,
                SlotTransactionAdminCommandParser.parse(List.of(
                        "admin", "transactions", "20", "v1.a.start", "extra")));
        assertInstanceOf(
                SlotTransactionAdminCommandParser.NotMatched.class,
                SlotTransactionAdminCommandParser.parse(List.of("admin", "browse")));
    }
}
