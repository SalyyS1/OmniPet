package io.github.salyvn.omnipet.paper.release;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ReleaseAdminCommandParser {
    public ReleaseAdminParseResult parse(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) return invalid(usage());
        try {
            return switch (arguments.getFirst().toLowerCase(Locale.ROOT)) {
                case "list" -> parseList(arguments);
                case "recover" -> parseRecover(arguments);
                case "reconcile" -> parseReconcile(arguments);
                default -> invalid(usage());
            };
        } catch (IllegalArgumentException invalid) {
            return invalid(invalid.getMessage());
        }
    }

    private static ReleaseAdminParseResult parseList(List<String> arguments) {
        if (arguments.size() > 2) return invalid("usage: list [limit]");
        int limit = arguments.size() == 2 ? Integer.parseInt(arguments.get(1)) : 20;
        if (limit < 1 || limit > 50) return invalid("release list limit must be between 1 and 50");
        return accepted(new ReleaseAdminCommand.ListPending(limit));
    }

    private static ReleaseAdminParseResult parseRecover(List<String> arguments) {
        if (arguments.size() != 4) return invalid("usage: recover <player-uuid> <transaction-uuid> <internal|external>");
        ReleaseAdminCommand.Channel channel = ReleaseAdminCommand.Channel.valueOf(
                arguments.get(3).toUpperCase(Locale.ROOT));
        return accepted(new ReleaseAdminCommand.Recover(
                UUID.fromString(arguments.get(1)), UUID.fromString(arguments.get(2)), channel));
    }

    private static ReleaseAdminParseResult parseReconcile(List<String> arguments) {
        if (arguments.size() != 4) {
            return invalid("usage: reconcile <player-uuid> <transaction-uuid> <decision>");
        }
        String decision = arguments.get(3).replace('-', '_').toUpperCase(Locale.ROOT);
        return accepted(new ReleaseAdminCommand.Reconcile(
                UUID.fromString(arguments.get(1)), UUID.fromString(arguments.get(2)),
                ReleaseAdminCommand.Decision.valueOf(decision)));
    }

    private static ReleaseAdminParseResult accepted(ReleaseAdminCommand command) {
        return new ReleaseAdminParseResult(ReleaseAdminParseResult.Status.ACCEPTED, command, "");
    }

    private static ReleaseAdminParseResult invalid(String detail) {
        return new ReleaseAdminParseResult(ReleaseAdminParseResult.Status.INVALID, null, detail);
    }

    private static String usage() {
        return "usage: list [limit] | recover <player> <transaction> <internal|external>"
                + " | reconcile <player> <transaction> <decision>";
    }
}
