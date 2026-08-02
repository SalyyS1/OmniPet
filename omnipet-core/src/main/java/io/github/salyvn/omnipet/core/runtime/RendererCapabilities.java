package io.github.salyvn.omnipet.core.runtime;

public record RendererCapabilities(
        boolean interaction,
        boolean riding,
        boolean model,
        boolean appearanceUpdates) {
    public static RendererCapabilities head() {
        return new RendererCapabilities(true, false, false, true);
    }
}
