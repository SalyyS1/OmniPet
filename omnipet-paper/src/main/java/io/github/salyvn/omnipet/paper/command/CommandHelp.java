package io.github.salyvn.omnipet.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Paged {@code /pet help} built from the same {@link CommandSpec} tree that drives suggestions.
 *
 * <p>Pure function, no I/O. A branch the sender cannot invoke never appears.
 */
public final class CommandHelp {
    public static final int LINES_PER_PAGE = 8;

    private CommandHelp() {}

    /** Every command the sender may invoke, depth-first, root first. */
    public static List<Line> lines(CommandSpec root, Predicate<String> hasPermission, boolean isPlayer) {
        Objects.requireNonNull(root, "command tree root");
        Objects.requireNonNull(hasPermission, "permission test");
        List<Line> collected = new ArrayList<>();
        collect(root, "/", hasPermission, isPlayer, collected);
        return List.copyOf(collected);
    }

    private static void collect(
            CommandSpec node,
            String prefix,
            Predicate<String> hasPermission,
            boolean isPlayer,
            List<Line> collected) {
        if (!node.visible(hasPermission, isPlayer)) return;
        String usage = prefix + node.usage();
        if (node.invocable(hasPermission, isPlayer)) collected.add(new Line(usage, node.description()));
        // Children are addressed through this node's literal, not its argument hints.
        String childPrefix = prefix + node.literal() + " ";
        for (CommandSpec child : node.children()) {
            collect(child, childPrefix, hasPermission, isPlayer, collected);
        }
    }

    /** Clamps a requested page into range and slices the matching lines. */
    public static Page page(List<Line> lines, int requestedPage) {
        Objects.requireNonNull(lines, "help lines");
        int pages = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * LINES_PER_PAGE;
        int end = Math.min(start + LINES_PER_PAGE, lines.size());
        return new Page(page, pages, start >= end ? List.of() : List.copyOf(lines.subList(start, end)));
    }

    /** One help entry: full usage plus its one-line description. */
    public record Line(String usage, String description) {
        public Line {
            Objects.requireNonNull(usage, "usage");
            Objects.requireNonNull(description, "description");
        }
    }

    /** A clamped slice of help lines. */
    public record Page(int page, int pages, List<Line> lines) {
        public Page {
            lines = List.copyOf(lines);
        }
    }
}
