package io.github.salyvn.omnipet.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.github.salyvn.omnipet.core.economy.SlotReconciliationDecision;

public final class SlotTransactionAdminCommandParser {
    public static final String PERMISSION = "omnipet.admin.reconcile";

    private SlotTransactionAdminCommandParser() {}

    public static Result parse(List<String> arguments) {
        List<String> args = List.copyOf(arguments == null ? List.of() : arguments);
        if (args.size() >= 2
                && args.get(0).equalsIgnoreCase("admin")
                && args.get(1).equalsIgnoreCase("transactions")) {
            if (args.size() == 2) return new ListPending(20);
            if (args.size() < 3 || args.size() > 4) return Invalid.INSTANCE;
            try {
                int limit = Integer.parseInt(args.get(2));
                if (limit < 1 || limit > 50) return Invalid.INSTANCE;
                if (args.size() == 3) return new ListPending(limit);
                String cursor = args.get(3);
                return validCursor(cursor) ? new ListPending(limit, cursor) : Invalid.INSTANCE;
            } catch (NumberFormatException ignored) {
                return Invalid.INSTANCE;
            }
        }
        if (args.size() == 4
                && args.get(0).equalsIgnoreCase("admin")
                && args.get(1).equalsIgnoreCase("reconcile")) {
            try {
                UUID transactionId = UUID.fromString(args.get(2));
                SlotReconciliationDecision decision = switch (args.get(3).toLowerCase(Locale.ROOT)) {
                    case "charge" -> SlotReconciliationDecision.CHARGE_CONFIRMED;
                    case "no-charge" -> SlotReconciliationDecision.NO_CHARGE_CONFIRMED;
                    case "refund" -> SlotReconciliationDecision.REFUND_CONFIRMED;
                    case "sync" -> SlotReconciliationDecision.ENTITLEMENT_SYNC_RETRY;
                    default -> null;
                };
                return decision == null ? Invalid.INSTANCE : new Reconcile(transactionId, decision);
            } catch (IllegalArgumentException ignored) {
                return Invalid.INSTANCE;
            }
        }
        return NotMatched.INSTANCE;
    }

    private static boolean validCursor(String cursor) {
        if (cursor == null || !cursor.matches(
                "(?i)v1\\.(?:root|[0-9a-f]{1,32})\\.(?:start|[0-9a-f]{32})")) {
            return false;
        }
        String[] parts = cursor.toLowerCase(Locale.ROOT).split("\\.");
        String prefix = parts[1].equals("root") ? "" : parts[1];
        return parts[2].equals("start") || parts[2].startsWith(prefix);
    }

    public sealed interface Result permits ListPending, Reconcile, Invalid, NotMatched {}

    public record ListPending(int limit, String cursor) implements Result {
        public ListPending(int limit) {
            this(limit, null);
        }
    }

    public record Reconcile(UUID transactionId, SlotReconciliationDecision decision) implements Result {}

    public enum Invalid implements Result { INSTANCE }

    public enum NotMatched implements Result { INSTANCE }
}
