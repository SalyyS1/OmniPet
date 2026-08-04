package io.github.salyvn.omnipet.paper.command;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Resolves the player argument of an admin command from either a name or a UUID.
 *
 * <p>Every admin recovery command used to take a raw {@code <player-uuid>}, which meant an operator had
 * to open a YAML file, copy a UUID out of it, and paste it into chat — for a player who was usually
 * standing in front of them. A name is accepted first, and a UUID still works, so existing scripted
 * invocations are unaffected.
 *
 * <p>Only <em>online</em> names resolve. Mapping an offline name to a UUID needs a blocking profile
 * lookup, which must not happen on the main thread while a command is being dispatched; an operator
 * working on an offline player still has the UUID form. The name check runs first because a name can
 * never be mistaken for a UUID — Minecraft names are at most 16 characters and a UUID text form is 36.
 */
public final class PlayerArgumentResolver {
    private final Function<String, Optional<UUID>> onlineNameLookup;

    public PlayerArgumentResolver(Function<String, Optional<UUID>> onlineNameLookup) {
        this.onlineNameLookup = Objects.requireNonNull(onlineNameLookup, "online name lookup");
    }

    /** The player the argument names, or empty when it is neither an online name nor a valid UUID. */
    public Optional<UUID> resolve(String argument) {
        if (argument == null || argument.isBlank()) return Optional.empty();
        String value = argument.trim();
        Optional<UUID> byName = onlineNameLookup.apply(value);
        if (byName.isPresent()) return byName;
        return uuid(value);
    }

    /** Strict UUID text, rejecting the shortened forms {@code UUID.fromString} would otherwise accept. */
    public static Optional<UUID> uuid(String value) {
        if (value == null) return Optional.empty();
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equalsIgnoreCase(value) ? Optional.of(parsed) : Optional.empty();
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
