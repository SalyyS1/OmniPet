package io.github.salyvn.omnipet.core.runtime;

/**
 * What a renderer can actually do, so a caller asks instead of assuming.
 *
 * <p>{@code animation} is resolved per renderer instance rather than per implementation: the ModelEngine
 * adapter binds its animation methods reflectively and reports false when a particular server's build
 * does not expose them, while still rendering the pet.
 */
public record RendererCapabilities(
        boolean interaction,
        boolean riding,
        boolean model,
        boolean appearanceUpdates,
        boolean animation) {
    /** The built-in renderer: an item on an invisible carrier, so no model and no animation clips. */
    public static RendererCapabilities head() {
        return new RendererCapabilities(true, false, false, true, false);
    }

    /** A renderer with no animation support, for a caller that only names the other four. */
    public RendererCapabilities(
            boolean interaction, boolean riding, boolean model, boolean appearanceUpdates) {
        this(interaction, riding, model, appearanceUpdates, false);
    }
}
