package io.github.salyvn.omnipet.paper.command;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;

/**
 * One {@code admin <area> ...} branch: its permission, its handler, and why it might be unavailable.
 *
 * <p>Every admin area used to be a hand-written {@code if} in one 653-line {@code execute} method, and
 * each repeated the same three steps in the same order — match the literal, check the permission, check
 * the handler is wired — so a new area meant copying that shape and hoping the copy was faithful. This
 * makes the shape the type instead, which is what lets {@code /petadmin} and {@code /pet admin} route
 * into the same objects rather than into two parallel chains of ifs.
 *
 * @param literal the second token, e.g. {@code egg} in {@code /pet admin egg give ...}
 * @param permission the node the sender must hold
 * @param unavailableMessage what to say when the handler is not wired yet, which happens in the
 *     narrower constructor overloads the tests use
 * @param deniedMessage what to say when the permission is missing
 */
public record AdminArea(
        String literal,
        String permission,
        String deniedMessage,
        String unavailableMessage,
        Handler handler) {
    public AdminArea {
        literal = required(literal, "admin area literal");
        permission = required(permission, "admin area permission");
        deniedMessage = required(deniedMessage, "denied message");
        unavailableMessage = required(unavailableMessage, "unavailable message");
    }

    /**
     * Runs this area, or explains why it did not.
     *
     * @param arguments the tokens after {@code admin <literal>}
     */
    public void dispatch(CommandSender sender, List<String> arguments) {
        Objects.requireNonNull(sender, "sender");
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(deniedMessage);
            return;
        }
        if (handler == null) {
            sender.sendMessage(unavailableMessage);
            return;
        }
        handler.accept(sender, arguments == null ? List.of() : arguments);
    }

    /** A handler that takes the sender and the remaining tokens. */
    @FunctionalInterface
    public interface Handler {
        void accept(CommandSender sender, List<String> arguments);
    }

    /** Adapts a target that only wants the arguments. */
    public static Handler of(Consumer<List<String>> target) {
        return (sender, arguments) -> target.accept(arguments);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value;
    }
}
