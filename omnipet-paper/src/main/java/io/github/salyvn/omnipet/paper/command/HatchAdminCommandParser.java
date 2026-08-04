package io.github.salyvn.omnipet.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Parses console-safe administration of persisted incubation state.
 *
 * <p>The player argument accepts an online name or a UUID, and the trailing action ID is optional:
 * it exists only to make a retry idempotent, so requiring the operator to invent one by hand added
 * nothing but a chance to mistype it. Both relaxations are additive — every previous invocation still
 * parses identically.
 */
public final class HatchAdminCommandParser {
    public static final String INSPECT_PERMISSION = "omnipet.admin.inspect";
    public static final String MANAGE_PERMISSION = "omnipet.admin.manageegg";

    private HatchAdminCommandParser() {}

    /** Parses with UUID-only player arguments, for a caller with no access to the online roster. */
    public static Result parse(List<String> arguments) {
        return parse(arguments, new PlayerArgumentResolver(name -> Optional.empty()), UUID::randomUUID);
    }

    public static Result parse(List<String> arguments, PlayerArgumentResolver players) {
        return parse(arguments, players, UUID::randomUUID);
    }

    public static Result parse(
            List<String> arguments, PlayerArgumentResolver players, Supplier<UUID> actionIds) {
        Objects.requireNonNull(players, "player argument resolver");
        Objects.requireNonNull(actionIds, "action id supplier");
        List<String> args = List.copyOf(arguments == null ? List.of() : arguments);
        if (args.size() < 2
                || !args.get(0).equalsIgnoreCase("admin")
                || !args.get(1).equalsIgnoreCase("hatch")) {
            return NotMatched.INSTANCE;
        }
        if (args.size() < 3) return Invalid.INSTANCE;

        return switch (args.get(2).toLowerCase(Locale.ROOT)) {
            case "inspect" -> parseInspect(args, players);
            case "reduce" -> parseTimedMutation(args, players, actionIds, true);
            case "set" -> parseTimedMutation(args, players, actionIds, false);
            case "complete" -> parseTerminalMutation(args, players, actionIds, true);
            case "cancel" -> parseTerminalMutation(args, players, actionIds, false);
            default -> Invalid.INSTANCE;
        };
    }

    private static Result parseInspect(List<String> args, PlayerArgumentResolver players) {
        if (args.size() != 4) return Invalid.INSTANCE;
        UUID playerId = players.resolve(args.get(3)).orElse(null);
        return playerId == null ? Invalid.INSTANCE : new Inspect(playerId);
    }

    private static Result parseTimedMutation(
            List<String> args,
            PlayerArgumentResolver players,
            Supplier<UUID> actionIds,
            boolean reduction) {
        if (args.size() < 6 || args.size() > 7) return Invalid.INSTANCE;
        UUID playerId = players.resolve(args.get(3)).orElse(null);
        UUID incubationId = uuid(args.get(4));
        Long millis = nonNegativeLong(args.get(5));
        UUID actionId = args.size() > 6 ? uuid(args.get(6)) : actionIds.get();
        if (playerId == null || incubationId == null || millis == null || actionId == null) {
            return Invalid.INSTANCE;
        }
        return reduction
                ? new Reduce(playerId, incubationId, millis, actionId)
                : new SetRemaining(playerId, incubationId, millis, actionId);
    }

    private static Result parseTerminalMutation(
            List<String> args,
            PlayerArgumentResolver players,
            Supplier<UUID> actionIds,
            boolean completion) {
        if (args.size() < 5 || args.size() > 6) return Invalid.INSTANCE;
        UUID playerId = players.resolve(args.get(3)).orElse(null);
        UUID incubationId = uuid(args.get(4));
        UUID actionId = args.size() > 5 ? uuid(args.get(5)) : actionIds.get();
        if (playerId == null || incubationId == null || actionId == null) return Invalid.INSTANCE;
        return completion
                ? new Complete(playerId, incubationId, actionId)
                : new Cancel(playerId, incubationId, actionId);
    }

    private static UUID uuid(String value) {
        return PlayerArgumentResolver.uuid(value).orElse(null);
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
