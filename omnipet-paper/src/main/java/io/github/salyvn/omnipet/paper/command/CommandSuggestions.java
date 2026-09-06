package io.github.salyvn.omnipet.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Brigadier suggestions computed from {@link CommandSpec}.
 *
 * <p>Pure function, no I/O. Suggestions run on the main thread for every keystroke, so this must
 * never touch disk, enumerate player UUIDs, or query a repository.
 *
 * <p>The one argument position it does complete is a player: the caller supplies the online names,
 * which is an in-memory roster read rather than the profile lookup that resolving an offline name or a
 * UUID would need. Every other argument position returns nothing rather than guessing.
 */
public final class CommandSuggestions {
    /** Required and optional player-name hints are the only arguments that get value suggestions. */
    private static final String REQUIRED_PLAYER_HINT = "<player_name>";
    private static final String OPTIONAL_PLAYER_HINT = "[player_name]";

    private CommandSuggestions() {}

    /**
     * @param args the tokens typed so far; a trailing empty token means the sender just typed a space
     * @return literal children the sender may use, filtered by permission and by prefix
     */
    public static List<String> suggest(
            CommandSpec root, Predicate<String> hasPermission, boolean isPlayer, String[] args) {
        return suggest(root, hasPermission, isPlayer, args, List::of);
    }

    /**
     * @param onlineNames the names of currently online players, consulted only at a player argument
     */
    public static List<String> suggest(
            CommandSpec root,
            Predicate<String> hasPermission,
            boolean isPlayer,
            String[] args,
            Supplier<List<String>> onlineNames) {
        Objects.requireNonNull(root, "command tree root");
        Objects.requireNonNull(hasPermission, "permission test");
        Objects.requireNonNull(onlineNames, "online name supplier");
        if (!root.visible(hasPermission, isPlayer)) return List.of();

        String[] tokens = args == null ? new String[0] : args;
        CommandSpec node = root;
        int consumed = 0;
        // Every token except the last is a completed word; walk into it when it names a child.
        for (int index = 0; index < tokens.length - 1; index++) {
            CommandSpec child = node.child(tokens[index]);
            if (child == null) {
                // Inside an argument list rather than a literal path. The first argument of a node that
                // takes a player is the one position worth completing.
                return playerNames(node, hasPermission, isPlayer, tokens, index, onlineNames);
            }
            node = child;
            consumed = index + 1;
        }

        String partial = tokens.length == 0 ? "" : tokens[tokens.length - 1];
        List<String> literals = children(node, hasPermission, isPlayer, partial);
        if (!literals.isEmpty()) return literals;
        // No literal matched, so the sender is typing this node's first argument.
        return tokens.length == consumed + 1 && takesPlayerFirst(node)
                ? prefixed(onlineNames.get(), partial)
                : List.of();
    }

    /** Names for a player argument the sender has already started, when the node takes one first. */
    private static List<String> playerNames(
            CommandSpec node,
            Predicate<String> hasPermission,
            boolean isPlayer,
            String[] tokens,
            int argumentIndex,
            Supplier<List<String>> onlineNames) {
        if (!node.visible(hasPermission, isPlayer) || !takesPlayerFirst(node)) return List.of();
        // Only the first argument is a player; a later position is an ID with nothing to suggest.
        return argumentIndex == tokens.length - 1
                ? prefixed(onlineNames.get(), tokens[tokens.length - 1])
                : List.of();
    }

    private static boolean takesPlayerFirst(CommandSpec node) {
        List<String> arguments = node.arguments();
        if (arguments.isEmpty()) return false;
        String hint = arguments.getFirst();
        return REQUIRED_PLAYER_HINT.equals(hint) || OPTIONAL_PLAYER_HINT.equals(hint);
    }

    private static List<String> prefixed(List<String> values, String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) matches.add(value);
        }
        return List.copyOf(matches);
    }

    private static List<String> children(
            CommandSpec node, Predicate<String> hasPermission, boolean isPlayer, String partial) {
        String prefix = partial.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (CommandSpec child : node.children()) {
            if (!child.visible(hasPermission, isPlayer)) continue;
            if (!child.literal().startsWith(prefix)) continue;
            matches.add(child.literal());
        }
        return List.copyOf(matches);
    }
}
