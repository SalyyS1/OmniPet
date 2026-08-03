package io.github.salyvn.omnipet.paper.text;

import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Static accessor for the active {@link MessageCatalog}, bound once during {@code onEnable}.
 *
 * <p>The ~80 message call sites would otherwise each need a catalog threaded through their
 * constructor. {@link #catalog()} throws when unbound so a missed bind fails loudly in tests rather
 * than silently at runtime.
 *
 * <p>All dynamic values go through {@link #of(String, String)}, which uses
 * {@link Placeholder#unparsed} — a pet name containing {@code <red>} or {@code <click:run_command>}
 * renders literally instead of injecting tags into another viewer's UI.
 */
public final class Messages {
    private static volatile MessageCatalog active;

    private Messages() {}

    /** Installs the catalog. Called from {@code onEnable} and again on a successful reload. */
    public static void bind(MessageCatalog catalog) {
        active = Objects.requireNonNull(catalog, "message catalog");
    }

    /** Drops the binding so a disabled plugin cannot serve stale text. */
    public static void unbind() {
        active = null;
    }

    /** The active catalog. */
    public static MessageCatalog catalog() {
        MessageCatalog current = active;
        if (current == null) throw new IllegalStateException("OmniPet message catalog is not bound");
        return current;
    }

    /** Renders one line from the active catalog. */
    public static Component line(MessageKey key, TagResolver... resolvers) {
        return catalog().line(key, resolvers);
    }

    /** Renders a multi-line block from the active catalog. */
    public static List<Component> lore(MessageKey key, TagResolver... resolvers) {
        return catalog().lore(key, resolvers);
    }

    /** A placeholder whose value is inserted literally, never parsed as MiniMessage. */
    public static TagResolver of(String name, String value) {
        return Placeholder.unparsed(name, value == null ? "" : value);
    }

    /** A placeholder for a numeric value. */
    public static TagResolver of(String name, long value) {
        return Placeholder.unparsed(name, Long.toString(value));
    }

    /**
     * The plain text of a rendered line, for embedding one message inside another placeholder.
     *
     * <p>Used where a detail sentence is composed at the call site and then inserted with
     * {@link #of(String, String)}, so the composed text is still never re-parsed as MiniMessage.
     */
    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
