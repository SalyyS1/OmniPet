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

    private GuiSettings() {}

    public static void bind(GuiConfig config) {
        active = Objects.requireNonNull(config, "gui config");
    }

    /** Restores defaults so a disabled plugin cannot serve a stale operator override. */
    public static void unbind() {
        active = GuiConfig.defaults();
    }

    public static GuiConfig gui() {
        return active;
    }
}
