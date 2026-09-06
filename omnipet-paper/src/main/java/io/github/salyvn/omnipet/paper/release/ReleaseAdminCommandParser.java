package io.github.salyvn.omnipet.paper.release;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class ReleaseAdminCommandParser {
    /**
     * Maps an online player name to its UUID, so an operator can name the player instead of pasting a
     * UUID copied out of a data file. Injected rather than calling Bukkit directly, which keeps this
     * parser unit-testable; the default resolves nothing, so a UUID is then required.
     */
    private final Function<String, Optional<UUID>> onlineNames;

    public ReleaseAdminCommandParser() {
        this(name -> Optional.empty());
    }

    public ReleaseAdminCommandParser(Function<String, Optional<UUID>> onlineNames) {
        this.onlineNames = java.util.Objects.requireNonNull(onlineNames, "online name lookup");
    }

    /** An online name when one matches, otherwise strict UUID text. */
    private UUID player(String value) {
        Optional<UUID> byName = onlineNames.apply(value);
        return byName.orElseGet(() -> UUID.fromString(value));
    }

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

    private ReleaseAdminParseResult parseRecover(List<String> arguments) {
        if (arguments.size() != 4) return invalid("usage: recover <player_name> <transaction-uuid> <internal|external>");
        ReleaseAdminCommand.Channel channel = ReleaseAdminCommand.Channel.valueOf(
                arguments.get(3).toUpperCase(Locale.ROOT));
        return accepted(new ReleaseAdminCommand.Recover(
                player(arguments.get(1)), UUID.fromString(arguments.get(2)), channel));
    }

    private ReleaseAdminParseResult parseReconcile(List<String> arguments) {
        if (arguments.size() != 4) {
            return invalid("usage: reconcile <player_name> <transaction-uuid> <decision>");
        }
        String decision = arguments.get(3).replace('-', '_').toUpperCase(Locale.ROOT);
        return accepted(new ReleaseAdminCommand.Reconcile(
                player(arguments.get(1)), UUID.fromString(arguments.get(2)),
                ReleaseAdminCommand.Decision.valueOf(decision)));
    }

    private static ReleaseAdminParseResult accepted(ReleaseAdminCommand command) {
        return new ReleaseAdminParseResult(ReleaseAdminParseResult.Status.ACCEPTED, command, "");
    }

    private static ReleaseAdminParseResult invalid(String detail) {
        return new ReleaseAdminParseResult(ReleaseAdminParseResult.Status.INVALID, null, detail);
    }

    private static String usage() {
        return "usage: list [limit] | recover <player_name> <transaction> <internal|external>"
                + " | reconcile <player_name> <transaction> <decision>";
    }
}
