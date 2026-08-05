package io.github.salyvn.omnipet.paper.runtime;

import java.util.Objects;

import io.github.salyvn.omnipet.core.runtime.ActivationRendererResolver;
import io.github.salyvn.omnipet.core.runtime.PetRendererPort;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.runtime.RendererHandle;
import io.github.salyvn.omnipet.core.runtime.RendererHealth;
import io.github.salyvn.omnipet.core.runtime.RendererSpawnRequest;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;

/**
 * Routes optional renderers through a linkage-safe built-in HEAD fallback.
 *
 * <p>The fallback used to be silent. A pet authored for ModelEngine that rendered as a player head looked
 * to an operator exactly like a pet whose provider had been ignored, because the reason the preferred
 * renderer was passed over — plugin absent, reflection quarantined, model not loaded — was captured into a
 * local and then dropped the moment HEAD succeeded. Nothing logged it and nothing surfaced it, so the only
 * way to find out was to read this class. It is now reported once per reason.
 */
public final class HeadFallbackRendererResolver implements ActivationRendererResolver {
    private final RoutingRenderer routing;

    public HeadFallbackRendererResolver(ActivationRendererResolver preferred, PetRendererPort headRenderer) {
        this(preferred, headRenderer, reason -> {});
    }

    /**
     * @param fallbackReporter told once for each distinct reason the preferred renderer was passed over.
     *     Deduplicated rather than rate-limited: the reason is a property of the server's configuration,
     *     so it is the same on every pet and every tick, and one line names it without flooding the log.
     */
    public HeadFallbackRendererResolver(
            ActivationRendererResolver preferred,
            PetRendererPort headRenderer,
            java.util.function.Consumer<String> fallbackReporter) {
        routing = new RoutingRenderer(
                Objects.requireNonNull(preferred, "preferred renderer resolver"),
                Objects.requireNonNull(headRenderer, "HEAD fallback renderer"),
                Objects.requireNonNull(fallbackReporter, "fallback reporter"));
    }

    @Override
    public PetRendererPort resolve(RendererSpawnRequest request) {
        Objects.requireNonNull(request, "renderer spawn request");
        return routing;
    }

    private static final class RoutingRenderer implements PetRendererPort {
        private final ActivationRendererResolver preferred;
        private final PetRendererPort head;
        private final java.util.function.Consumer<String> fallbackReporter;
        /** Reasons already reported, so a per-tick fallback does not become a per-tick log line. */
        private final java.util.Set<String> reported =
                java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

        private RoutingRenderer(
                ActivationRendererResolver preferred,
                PetRendererPort head,
                java.util.function.Consumer<String> fallbackReporter) {
            this.preferred = preferred;
            this.head = head;
            this.fallbackReporter = fallbackReporter;
        }

        @Override
        public RendererHealth health() {
            try {
                RendererHealth health = Objects.requireNonNull(head.health(), "HEAD renderer health");
                return health.available()
                        ? health
                        : new RendererHealth(RendererHealth.Status.DEGRADED,
                                "HEAD fallback unavailable; optional renderer will be probed");
            } catch (RuntimeException | LinkageError failure) {
                return new RendererHealth(RendererHealth.Status.DEGRADED,
                        "HEAD fallback probe failed; optional renderer will be probed: " + detail(failure));
            }
        }

        @Override
        public RendererHandle spawn(RendererSpawnRequest request) {
            Throwable preferredFailure = null;
            String provider = request.appearance().provider();
            if (!"HEAD".equals(provider)) {
                try {
                    PetRendererPort candidate = Objects.requireNonNull(
                            preferred.resolve(request), "preferred renderer resolver returned null");
                    RendererHealth health = Objects.requireNonNull(candidate.health(), "preferred renderer health");
                    if (health.available()) return new RoutedHandle(candidate, candidate.spawn(request));
                    preferredFailure = new IllegalStateException("preferred renderer unavailable: " + health.detail());
                } catch (RuntimeException | LinkageError failure) {
                    preferredFailure = failure;
                }
            }
            try {
                RoutedHandle routed = new RoutedHandle(head, head.spawn(request));
                // Reported here rather than swallowed. A pet authored for one provider that renders as a
                // head is a visible surprise, and the reason it happened is the only thing that makes it
                // diagnosable.
                if (preferredFailure != null) reportFallback(provider, preferredFailure);
                return routed;
            } catch (RuntimeException | LinkageError fallbackFailure) {
                if (preferredFailure != null) fallbackFailure.addSuppressed(preferredFailure);
                throw fallbackFailure;
            }
        }

        /** Says once why a provider was passed over, keyed on the reason so it cannot repeat per pet. */
        private void reportFallback(String provider, Throwable failure) {
            String reason = provider + ": " + detail(failure);
            if (!reported.add(reason)) return;
            try {
                fallbackReporter.accept(
                        "rendering as a player head instead of " + reason
                        + " — pets authored for this provider will not show their model until it is resolved");
            } catch (RuntimeException | LinkageError ignored) {
                // An observer must never be able to stop a pet spawning.
            }
        }

        @Override
        public void update(RendererHandle handle, RuntimeTransform transform) {
            RoutedHandle routed = require(handle);
            routed.renderer().update(routed.delegate(), transform);
        }

        @Override
        public void updateAppearance(RendererHandle handle, RendererAppearance appearance) {
            RoutedHandle routed = require(handle);
            routed.renderer().updateAppearance(routed.delegate(), appearance);
        }

        @Override
        public void remove(RendererHandle handle) {
            RoutedHandle routed = require(handle);
            routed.renderer().remove(routed.delegate());
        }

        private static RoutedHandle require(RendererHandle handle) {
            if (handle instanceof RoutedHandle routed) return routed;
            throw new IllegalArgumentException("renderer handle was not created by the fallback resolver");
        }
    }

    private record RoutedHandle(PetRendererPort renderer, RendererHandle delegate) implements RendererHandle {
        private RoutedHandle {
            Objects.requireNonNull(renderer, "routed renderer");
            Objects.requireNonNull(delegate, "routed renderer handle");
        }

        @Override public java.util.UUID ownerId() { return delegate.ownerId(); }
        @Override public java.util.UUID petInstanceId() { return delegate.petInstanceId(); }
        @Override public long rendererGeneration() { return delegate.rendererGeneration(); }
        @Override public io.github.salyvn.omnipet.core.runtime.RendererCapabilities capabilities() {
            return delegate.capabilities();
        }
        @Override public java.util.Set<java.util.UUID> interactionEntityIds() {
            return delegate.interactionEntityIds();
        }
        @Override public boolean removed() { return delegate.removed(); }
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
