package io.github.salyvn.omnipet.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.economy.SlotReconciliationDecision;
import io.papermc.paper.command.brigadier.CommandSourceStack;

class OmniPetCommandDispatchTest {
    @Test
    void executeForwardsTransactionListLimitAndCursor() {
        RecordingTransactions transactions = new RecordingTransactions();
        OmniPetCommand command = command(transactions);

        command.execute(source(permittedSender()), new String[] {
                "admin", "transactions", "7", "v1.a.start"
        });

        assertEquals(7, transactions.limit);
        assertEquals("v1.a.start", transactions.cursor);
        assertNull(transactions.transactionId);
    }

    @Test
    void executeDispatchesEveryReconciliationDecision() {
        LinkedHashMap<String, SlotReconciliationDecision> decisions = new LinkedHashMap<>();
        decisions.put("charge", SlotReconciliationDecision.CHARGE_CONFIRMED);
        decisions.put("no-charge", SlotReconciliationDecision.NO_CHARGE_CONFIRMED);
        decisions.put("refund", SlotReconciliationDecision.REFUND_CONFIRMED);
        decisions.put("sync", SlotReconciliationDecision.ENTITLEMENT_SYNC_RETRY);

        for (Map.Entry<String, SlotReconciliationDecision> entry : decisions.entrySet()) {
            RecordingTransactions transactions = new RecordingTransactions();
            UUID transactionId = UUID.randomUUID();

            command(transactions).execute(source(permittedSender()), new String[] {
                    "admin", "reconcile", transactionId.toString(), entry.getKey()
            });

            assertEquals(transactionId, transactions.transactionId);
            assertEquals(entry.getValue(), transactions.decision);
        }
    }

    private static OmniPetCommand command(SlotTransactionAdminTarget transactions) {
        return new OmniPetCommand(null, null, transactions, null, () -> false);
    }

    private static CommandSourceStack source(CommandSender sender) {
        return (CommandSourceStack) Proxy.newProxyInstance(
                OmniPetCommandDispatchTest.class.getClassLoader(),
                new Class<?>[] {CommandSourceStack.class},
                (proxy, method, arguments) -> method.getName().equals("getSender") ? sender : defaultValue(method.getReturnType()));
    }

    private static CommandSender permittedSender() {
        return (CommandSender) Proxy.newProxyInstance(
                OmniPetCommandDispatchTest.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission", "isPermissionSet" -> true;
                    case "getName" -> "Console";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new IllegalArgumentException("unsupported primitive: " + type);
    }

    private static final class RecordingTransactions implements SlotTransactionAdminTarget {
        private int limit;
        private String cursor;
        private UUID transactionId;
        private SlotReconciliationDecision decision;

        @Override
        public void list(CommandSender sender, int requestedLimit, String requestedCursor) {
            limit = requestedLimit;
            cursor = requestedCursor;
        }

        @Override
        public void reconcile(
                CommandSender sender,
                UUID requestedTransactionId,
                SlotReconciliationDecision requestedDecision) {
            transactionId = requestedTransactionId;
            decision = requestedDecision;
        }
    }
}
