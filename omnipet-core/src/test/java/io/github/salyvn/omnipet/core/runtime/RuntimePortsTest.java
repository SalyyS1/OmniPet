package io.github.salyvn.omnipet.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RuntimePortsTest {
    @Test
    void interactionIndexUsesStableOwnerPetAndGenerationIdentity() {
        InteractionIndex index = new InteractionIndex();
        UUID owner = UUID.randomUUID();
        UUID pet = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        RendererHandle handle = handle(owner, pet, 7, entity);

        index.register(handle);

        assertEquals(new InteractionIdentity(owner, pet, 7), index.resolve(entity).orElseThrow());
        index.unregister(handle);
        assertTrue(index.resolve(entity).isEmpty());
    }

    @Test
    void interactionIndexRejectsEntityOwnershipCollision() {
        InteractionIndex index = new InteractionIndex();
        UUID entity = UUID.randomUUID();
        index.register(handle(UUID.randomUUID(), UUID.randomUUID(), 1, entity));

        assertThrows(IllegalStateException.class,
                () -> index.register(handle(UUID.randomUUID(), UUID.randomUUID(), 2, entity)));
    }

    @Test
    void authorizationSeparatesIdentityLookupFromActorPolicy() {
        PetActionAuthorizationPolicy policy = new PetActionAuthorizationPolicy();
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        assertEquals(PetActionAuthorizationPolicy.Decision.NOT_AUTHORIZED,
                policy.authorize(context(stranger, owner, PetAction.FEED, true, 2, 3, 3, false, false), 4));
        assertEquals(PetActionAuthorizationPolicy.Decision.ALLOWED,
                policy.authorize(context(stranger, owner, PetAction.INTERACT, true, 2, 3, 3, true, false), 4));
        assertEquals(PetActionAuthorizationPolicy.Decision.STALE_RENDERER,
                policy.authorize(context(owner, owner, PetAction.MANAGE, true, 2, 2, 3, false, false), 4));
    }

    @Test
    void effectBudgetEnforcesCooldownPerPetAndGlobalLimits() {
        EffectBudget budget = new EffectBudget(2, 1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        assertTrue(budget.tryAcquire(1, 100, first, "smoke", 50));
        assertFalse(budget.tryAcquire(1, 101, first, "spark", 0));
        assertTrue(budget.tryAcquire(1, 100, second, "smoke", 50));
        assertFalse(budget.tryAcquire(1, 100, third, "smoke", 0));
        assertFalse(budget.tryAcquire(2, 120, first, "smoke", 50));
        assertTrue(budget.tryAcquire(2, 151, first, "smoke", 50));
    }

    private static RendererHandle handle(UUID owner, UUID pet, long generation, UUID entity) {
        return new RendererHandle() {
            @Override public UUID ownerId() { return owner; }
            @Override public UUID petInstanceId() { return pet; }
            @Override public long rendererGeneration() { return generation; }
            @Override public RendererCapabilities capabilities() { return RendererCapabilities.head(); }
            @Override public Set<UUID> interactionEntityIds() { return Set.of(entity); }
            @Override public boolean removed() { return false; }
        };
    }

    private static PetActionAuthorizationContext context(
            UUID actor,
            UUID owner,
            PetAction action,
            boolean sameWorld,
            double distance,
            long resolvedGeneration,
            long currentGeneration,
            boolean publicAccess,
            boolean admin) {
        return new PetActionAuthorizationContext(
                actor, owner, UUID.randomUUID(), action, sameWorld, distance,
                resolvedGeneration, currentGeneration, publicAccess, admin);
    }
}
