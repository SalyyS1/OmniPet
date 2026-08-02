package io.github.salyvn.omnipet.core.runtime;

public final class PetActionAuthorizationPolicy {
    public Decision authorize(PetActionAuthorizationContext context, double maximumDistance) {
        if (context == null) throw new IllegalArgumentException("authorization context is required");
        if (!Double.isFinite(maximumDistance) || maximumDistance < 0) {
            throw new IllegalArgumentException("maximum action distance must be finite and non-negative");
        }
        if (context.resolvedRendererGeneration() != context.currentRendererGeneration()) {
            return Decision.STALE_RENDERER;
        }
        if (!context.sameWorld()) return Decision.WRONG_WORLD;
        if (context.distance() > maximumDistance) return Decision.OUT_OF_RANGE;
        if (context.adminOverride()) return Decision.ALLOWED;
        if (context.actorId().equals(context.ownerId())) return Decision.ALLOWED;
        if (context.publicAccess() && context.action() == PetAction.INTERACT) return Decision.ALLOWED;
        return Decision.NOT_AUTHORIZED;
    }

    public enum Decision {
        ALLOWED,
        STALE_RENDERER,
        WRONG_WORLD,
        OUT_OF_RANGE,
        NOT_AUTHORIZED
    }
}
