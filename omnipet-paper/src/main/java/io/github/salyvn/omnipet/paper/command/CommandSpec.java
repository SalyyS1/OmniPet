package io.github.salyvn.omnipet.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * One node of the declarative {@code /pet} command tree.
 *
 * <p>Pure data. {@link CommandSuggestions} and {@link CommandHelp} both read this single tree, so a
 * branch cannot appear in tab-complete while being absent from help, or vice versa.
 *
 * <p>{@code arguments} are display-only hints such as {@code <pet-uuid>}. They are never offered as
 * suggestion values — suggesting a UUID would require enumerating player data on the main thread.
 *
 * @param literal the token the sender types, e.g. {@code hatch}; the root carries the command name
 * @param arguments display hints appended after the literal in help output
 * @param permissions every permission the sender must hold to invoke this node
 * @param description one-line summary shown in help
 * @param playerOnly whether the dispatcher rejects console for this node
 * @param runnable whether the node is a command in its own right, or only a grouping literal
 * @param children nested nodes
 */
public record CommandSpec(
        String literal,
        List<String> arguments,
        List<String> permissions,
        String description,
        boolean playerOnly,
        boolean runnable,
        List<CommandSpec> children) {

    public CommandSpec {
        Objects.requireNonNull(literal, "literal");
        Objects.requireNonNull(description, "description");
        arguments = List.copyOf(arguments == null ? List.of() : arguments);
        permissions = List.copyOf(permissions == null ? List.of() : permissions);
        children = List.copyOf(children == null ? List.of() : children);
    }

    /** The child matching a typed token, ignoring case, or {@code null}. */
    public CommandSpec child(String token) {
        if (token == null) return null;
        for (CommandSpec child : children) {
            if (child.literal.equalsIgnoreCase(token)) return child;
        }
        return null;
    }

    /** Whether the sender may run this node itself. */
    public boolean invocable(Predicate<String> hasPermission, boolean isPlayer) {
        Objects.requireNonNull(hasPermission, "permission test");
        if (!runnable) return false;
        if (playerOnly && !isPlayer) return false;
        for (String permission : permissions) {
            if (!hasPermission.test(permission)) return false;
        }
        return true;
    }

    /**
     * Whether the sender should see this node at all: either they can run it, or it leads to
     * something they can run. A grouping literal such as {@code admin} is therefore hidden from a
     * sender who holds none of its child permissions.
     */
    public boolean visible(Predicate<String> hasPermission, boolean isPlayer) {
        if (invocable(hasPermission, isPlayer)) return true;
        for (CommandSpec child : children) {
            if (child.visible(hasPermission, isPlayer)) return true;
        }
        return false;
    }

    /** The literal plus its argument hints, e.g. {@code skill <pet-uuid> <binding-id>}. */
    public String usage() {
        if (arguments.isEmpty()) return literal;
        return literal + " " + String.join(" ", arguments);
    }

    /** Every permission named anywhere in this subtree, for the descriptor contract test. */
    public List<String> allPermissions() {
        List<String> collected = new ArrayList<>(permissions);
        for (CommandSpec child : children) collected.addAll(child.allPermissions());
        return List.copyOf(collected);
    }

    public static Builder of(String literal, String description) {
        return new Builder(literal, description);
    }

    /** Readable nesting for the tree definition. */
    public static final class Builder {
        private final String literal;
        private final String description;
        private final List<String> arguments = new ArrayList<>();
        private final List<String> permissions = new ArrayList<>();
        private final List<CommandSpec> children = new ArrayList<>();
        private boolean playerOnly;
        private boolean runnable = true;

        private Builder(String literal, String description) {
            this.literal = Objects.requireNonNull(literal, "literal").toLowerCase(Locale.ROOT);
            this.description = Objects.requireNonNull(description, "description");
        }

        public Builder args(String... hints) {
            arguments.addAll(List.of(hints));
            return this;
        }

        public Builder permission(String... required) {
            permissions.addAll(List.of(required));
            return this;
        }

        public Builder playerOnly() {
            playerOnly = true;
            return this;
        }

        /** Marks a literal that only groups children and cannot be run on its own. */
        public Builder group() {
            runnable = false;
            return this;
        }

        public Builder child(Builder... nested) {
            for (Builder builder : nested) children.add(builder.build());
            return this;
        }

        public CommandSpec build() {
            return new CommandSpec(literal, arguments, permissions, description, playerOnly, runnable, children);
        }
    }
}
