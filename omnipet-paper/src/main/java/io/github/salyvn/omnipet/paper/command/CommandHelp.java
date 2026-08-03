package io.github.salyvn.omnipet.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import io.github.salyvn.omnipet.paper.config.GuiSettings;

/**
 * Paged {@code /pet help} built from the same {@link CommandSpec} tree that drives suggestions.
 *
 * <p>Pure function, no I/O. A branch the sender cannot invoke never appears.
 */
public final class CommandHelp {
    /**
     * The page size used when no operator override applies.
     *
     * <p>Kept as a constant rather than becoming mutable static state: {@link #page(List, int)} reads
     * the operator's value from config, while {@link #page(List, int, int)} takes it explicitly so the
     * paging logic stays a pure function that tests can drive without binding config.
     */
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

    /** Clamps a requested page into range using the operator-configured page size. */
    public static Page page(List<Line> lines, int requestedPage) {
        return page(lines, requestedPage, GuiSettings.gui().helpLinesPerPage());
    }

    /** Clamps a requested page into range and slices the matching lines. */
    public static Page page(List<Line> lines, int requestedPage, int linesPerPage) {
        Objects.requireNonNull(lines, "help lines");
        int size = Math.max(1, linesPerPage);
        int pages = Math.max(1, (lines.size() + size - 1) / size);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * size;
        int end = Math.min(start + size, lines.size());
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
