package io.github.salyvn.omnipet.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Brigadier suggestions computed from {@link CommandSpec}.
 *
 * <p>Pure function, no I/O. Suggestions run on the main thread for every keystroke, so this must
 * never touch disk, enumerate player UUIDs, or query a repository. At an argument position it
 * returns nothing rather than guessing.
 */
public final class CommandSuggestions {
    private CommandSuggestions() {}

    /**
     * @param args the tokens typed so far; a trailing empty token means the sender just typed a space
     * @return literal children the sender may use, filtered by permission and by prefix
     */
    public static List<String> suggest(
            CommandSpec root, Predicate<String> hasPermission, boolean isPlayer, String[] args) {
        Objects.requireNonNull(root, "command tree root");
        Objects.requireNonNull(hasPermission, "permission test");
        if (!root.visible(hasPermission, isPlayer)) return List.of();

        String[] tokens = args == null ? new String[0] : args;
        CommandSpec node = root;
        // Every token except the last is a completed word; walk into it when it names a child.
        for (int index = 0; index < tokens.length - 1; index++) {
            CommandSpec child = node.child(tokens[index]);
            // An unmatched completed token means the sender is inside an argument list, not a
            // literal path, and no further literal can be suggested.
            if (child == null) return List.of();
            node = child;
        }

        String partial = tokens.length == 0 ? "" : tokens[tokens.length - 1];
        return children(node, hasPermission, isPlayer, partial);
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
