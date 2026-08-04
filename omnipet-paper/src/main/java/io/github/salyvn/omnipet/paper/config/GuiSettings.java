package io.github.salyvn.omnipet.paper.config;

import java.util.Objects;

/**
 * Static accessor for the active {@link GuiConfig}, bound during {@code onEnable} and rebound on a
 * successful reload.
 *
 * <p>Mirrors {@code Messages}: the alternative is threading a config through constructors that exist
 * in seven overloads. Unlike {@code Messages} this never throws when unbound — a display setting is
 * not worth failing a click over, so an unbound read yields {@link GuiConfig#defaults()}, which is
 * exactly the behavior the plugin had before the section existed.
 *
 * <p>Rebinding does not make every value live. Consumers that read at construction time — the vault
 * renderer and the Studio's chat-input service — keep the value they were built with until restart.
 * Only per-call readers and the rebuilt feedback service see a reload.
 */
public final class GuiSettings {
    private static volatile GuiConfig active = GuiConfig.defaults();
    private static volatile java.util.function.Consumer<String> warnings = message -> { };
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private GuiSettings() {}

    public static void bind(GuiConfig config) {
        active = Objects.requireNonNull(config, "gui config");
        // A reload may fix or introduce a layout problem, so previously reported warnings become
        // eligible again rather than being suppressed for the rest of the server's uptime.
        REPORTED.clear();
    }

    /** Routes layout warnings to the plugin log. Absent until {@code onEnable} wires it. */
    public static void bindWarnings(java.util.function.Consumer<String> sink) {
        warnings = Objects.requireNonNull(sink, "warning sink");
    }

    /**
     * Reports a bad layout value once per distinct message.
     *
     * <p>Deduplicated because renderers run per menu open: a single mistyped material would otherwise
     * write a log line every time any player opened that menu.
     */
    public static void warn(String message) {
        if (message == null || message.isBlank()) return;
        if (REPORTED.add(message)) warnings.accept(message);
    }

    /** Restores defaults so a disabled plugin cannot serve a stale operator override. */
    public static void unbind() {
        active = GuiConfig.defaults();
        warnings = message -> { };
        REPORTED.clear();
    }

    public static GuiConfig gui() {
        return active;
    }
}
