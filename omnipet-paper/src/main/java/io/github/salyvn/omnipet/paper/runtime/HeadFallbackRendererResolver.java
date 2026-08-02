package io.github.salyvn.omnipet.paper.runtime;

import java.util.Objects;

import io.github.salyvn.omnipet.core.runtime.ActivationRendererResolver;
import io.github.salyvn.omnipet.core.runtime.PetRendererPort;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.runtime.RendererHandle;
import io.github.salyvn.omnipet.core.runtime.RendererHealth;
import io.github.salyvn.omnipet.core.runtime.RendererSpawnRequest;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;

/** Routes optional renderers through a linkage-safe built-in HEAD fallback. */
public final class HeadFallbackRendererResolver implements ActivationRendererResolver {
    private final RoutingRenderer routing;

    public HeadFallbackRendererResolver(ActivationRendererResolver preferred, PetRendererPort headRenderer) {
        routing = new RoutingRenderer(
                Objects.requireNonNull(preferred, "preferred renderer resolver"),
                Objects.requireNonNull(headRenderer, "HEAD fallback renderer"));
    }

    @Override
    public PetRendererPort resolve(RendererSpawnRequest request) {
        Objects.requireNonNull(request, "renderer spawn request");
        return routing;
    }

    private static final class RoutingRenderer implements PetRendererPort {
        private final ActivationRendererResolver preferred;
        private final PetRendererPort head;

        private RoutingRenderer(ActivationRendererResolver preferred, PetRendererPort head) {
            this.preferred = preferred;
            this.head = head;
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
            if (!"HEAD".equals(request.appearance().provider())) {
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
                return new RoutedHandle(head, head.spawn(request));
            } catch (RuntimeException | LinkageError fallbackFailure) {
                if (preferredFailure != null) fallbackFailure.addSuppressed(preferredFailure);
                throw fallbackFailure;
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
