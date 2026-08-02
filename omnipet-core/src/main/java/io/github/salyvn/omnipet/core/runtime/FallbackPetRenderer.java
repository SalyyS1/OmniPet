package io.github.salyvn.omnipet.core.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tries healthy renderers in priority order and retains the selected delegate per handle. */
public final class FallbackPetRenderer implements PetRendererPort {
    private final List<PetRendererPort> renderers;

    public FallbackPetRenderer(List<PetRendererPort> renderers) {
        this.renderers = List.copyOf(renderers == null ? List.of() : renderers);
        if (this.renderers.isEmpty() || this.renderers.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("at least one fallback renderer is required");
        }
    }

    @Override
    public RendererHealth health() {
        List<String> diagnostics = new ArrayList<>();
        for (PetRendererPort renderer : renderers) {
            try {
                RendererHealth health = Objects.requireNonNull(renderer.health(), "renderer health");
                diagnostics.add(health.detail());
                if (health.available()) return new RendererHealth(RendererHealth.Status.AVAILABLE,
                        "fallback chain available: " + health.detail());
            } catch (RuntimeException | LinkageError failure) {
                diagnostics.add(detail(failure));
            }
        }
        return new RendererHealth(RendererHealth.Status.UNAVAILABLE,
                "no fallback renderer is available: " + String.join("; ", diagnostics));
    }

    @Override
    public RendererHandle spawn(RendererSpawnRequest request) {
        Objects.requireNonNull(request, "renderer spawn request");
        Throwable failure = null;
        for (PetRendererPort renderer : renderers) {
            try {
                RendererHealth health = Objects.requireNonNull(renderer.health(), "renderer health");
                if (!health.available()) continue;
                RendererHandle handle = Objects.requireNonNull(renderer.spawn(request), "renderer returned null handle");
                return new SelectedHandle(renderer, handle);
            } catch (RuntimeException | LinkageError attempt) {
                if (failure == null) failure = attempt;
                else failure.addSuppressed(attempt);
            }
        }
        IllegalStateException unavailable = new IllegalStateException("all renderer candidates failed");
        if (failure != null) unavailable.addSuppressed(failure);
        throw unavailable;
    }

    @Override
    public void update(RendererHandle handle, RuntimeTransform transform) {
        SelectedHandle selected = require(handle);
        selected.renderer.update(selected.delegate, transform);
    }

    @Override
    public void updateAppearance(RendererHandle handle, RendererAppearance appearance) {
        SelectedHandle selected = require(handle);
        selected.renderer.updateAppearance(selected.delegate, appearance);
    }

    @Override
    public void remove(RendererHandle handle) {
        SelectedHandle selected = require(handle);
        if (selected.removed()) return;
        selected.renderer.remove(selected.delegate);
    }

    private static SelectedHandle require(RendererHandle handle) {
        if (!(handle instanceof SelectedHandle selected)) {
            throw new IllegalArgumentException("renderer handle was not created by this fallback chain");
        }
        return selected;
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static final class SelectedHandle implements RendererHandle {
        private final PetRendererPort renderer;
        private final RendererHandle delegate;

        private SelectedHandle(PetRendererPort renderer, RendererHandle delegate) {
            this.renderer = renderer;
            this.delegate = delegate;
        }

        @Override public java.util.UUID ownerId() { return delegate.ownerId(); }
        @Override public java.util.UUID petInstanceId() { return delegate.petInstanceId(); }
        @Override public long rendererGeneration() { return delegate.rendererGeneration(); }
        @Override public RendererCapabilities capabilities() { return delegate.capabilities(); }
        @Override public java.util.Set<java.util.UUID> interactionEntityIds() { return delegate.interactionEntityIds(); }
        @Override public boolean removed() { return delegate.removed(); }
    }
}
