package io.github.salyvn.omnipet.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Parses console-safe administration of persisted incubation state. */
public final class HatchAdminCommandParser {
    public static final String INSPECT_PERMISSION = "omnipet.admin.inspect";
    public static final String MANAGE_PERMISSION = "omnipet.admin.manageegg";

    private HatchAdminCommandParser() {}

    public static Result parse(List<String> arguments) {
        List<String> args = List.copyOf(arguments == null ? List.of() : arguments);
        if (args.size() < 2
                || !args.get(0).equalsIgnoreCase("admin")
                || !args.get(1).equalsIgnoreCase("hatch")) {
            return NotMatched.INSTANCE;
        }
        if (args.size() < 3) return Invalid.INSTANCE;

        return switch (args.get(2).toLowerCase(Locale.ROOT)) {
            case "inspect" -> parseInspect(args);
            case "reduce" -> parseTimedMutation(args, true);
            case "set" -> parseTimedMutation(args, false);
            case "complete" -> parseTerminalMutation(args, true);
            case "cancel" -> parseTerminalMutation(args, false);
            default -> Invalid.INSTANCE;
        };
    }

    private static Result parseInspect(List<String> args) {
        if (args.size() != 4) return Invalid.INSTANCE;
        UUID playerId = uuid(args.get(3));
        return playerId == null ? Invalid.INSTANCE : new Inspect(playerId);
    }

    private static Result parseTimedMutation(List<String> args, boolean reduction) {
        if (args.size() != 7) return Invalid.INSTANCE;
        UUID playerId = uuid(args.get(3));
        UUID incubationId = uuid(args.get(4));
        Long millis = nonNegativeLong(args.get(5));
        UUID actionId = uuid(args.get(6));
        if (playerId == null || incubationId == null || millis == null || actionId == null) {
            return Invalid.INSTANCE;
        }
        return reduction
                ? new Reduce(playerId, incubationId, millis, actionId)
                : new SetRemaining(playerId, incubationId, millis, actionId);
    }

    private static Result parseTerminalMutation(List<String> args, boolean completion) {
        if (args.size() != 6) return Invalid.INSTANCE;
        UUID playerId = uuid(args.get(3));
        UUID incubationId = uuid(args.get(4));
        UUID actionId = uuid(args.get(5));
        if (playerId == null || incubationId == null || actionId == null) return Invalid.INSTANCE;
        return completion
                ? new Complete(playerId, incubationId, actionId)
                : new Cancel(playerId, incubationId, actionId);
    }

    private static UUID uuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equalsIgnoreCase(value) ? parsed : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Long nonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public sealed interface Result permits Inspect, Reduce, SetRemaining, Complete, Cancel, Invalid, NotMatched {}

    public record Inspect(UUID playerId) implements Result {}

    public record Reduce(UUID playerId, UUID incubationId, long millis, UUID actionId) implements Result {}

    public record SetRemaining(UUID playerId, UUID incubationId, long millis, UUID actionId) implements Result {}

    public record Complete(UUID playerId, UUID incubationId, UUID actionId) implements Result {}

    public record Cancel(UUID playerId, UUID incubationId, UUID actionId) implements Result {}

    public enum Invalid implements Result { INSTANCE }

    public enum NotMatched implements Result { INSTANCE }
}
