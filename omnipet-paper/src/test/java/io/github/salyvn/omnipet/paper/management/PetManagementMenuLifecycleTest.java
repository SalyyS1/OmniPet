package io.github.salyvn.omnipet.paper.management;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.release.ReleasePreview;
import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;
import io.github.salyvn.omnipet.paper.gui.player.PetManagementInventoryHolder;

class PetManagementMenuLifecycleTest {
    @Test
    void oldCloseEventCannotRemoveReplacementInventoryWithSameSessionId() {
        Fixture fixture = fixture();
        PetManagementInventoryHolder original = fixture.holder(fixture.session, "original");
        fixture.install(original, fixture.view(fixture.session));
        PetManagementMenuSupport.ViewLifecycle.Request mutation = fixture.lifecycle.beginAction(original);
        PetManagementSession advanced = fixture.session.withRevision(8);
        PetManagementInventoryHolder replacement = fixture.holder(advanced, "replacement");

        assertTrue(fixture.lifecycle.replace(mutation, replacement, fixture.view(advanced)));
        assertNull(fixture.lifecycle.retire(original, original.getInventory()));
        assertTrue(fixture.lifecycle.current(replacement));
        assertNotEquals(original.generationId(), replacement.generationId());
    }

    @Test
    void releasePreviewReplacementCloseDoesNotInvalidateConfirmSession() {
        Fixture fixture = fixture();
        PetManagementInventoryHolder management = fixture.holder(fixture.session, "management");
        fixture.install(management, fixture.view(fixture.session));
        PetManagementMenuSupport.ViewLifecycle.Request preview = fixture.lifecycle.beginAction(management);
        PetManagementInventoryHolder confirmation = fixture.releaseHolder(fixture.session, "confirmation");

        assertTrue(fixture.lifecycle.replace(preview, confirmation, fixture.view(fixture.session)));
        assertNull(fixture.lifecycle.retire(management, management.getInventory()));
        assertTrue(fixture.lifecycle.current(confirmation));
    }

    @Test
    void mutationRerenderCloseDoesNotDropAdvancedView() {
        Fixture fixture = fixture();
        PetManagementInventoryHolder revisionSeven = fixture.holder(fixture.session, "revision-seven");
        fixture.install(revisionSeven, fixture.view(fixture.session));
        PetManagementMenuSupport.ViewLifecycle.Request request = fixture.lifecycle.beginAction(revisionSeven);
        PetManagementSession revisionEight = fixture.session.withRevision(8);
        PetManagementInventoryHolder advanced = fixture.holder(revisionEight, "revision-eight");

        assertTrue(fixture.lifecycle.replace(request, advanced, fixture.view(revisionEight)));
        assertNull(fixture.lifecycle.retire(revisionSeven, revisionSeven.getInventory()));
        assertTrue(fixture.lifecycle.current(advanced));
    }

    @Test
    void lateAsyncOpenResultCannotReplaceNewerView() {
        Fixture fixture = fixture();
        PetManagementMenuSupport.ViewLifecycle.Request oldOpen = fixture.lifecycle.beginOpen(VIEWER);
        assertNotNull(oldOpen);
        fixture.lifecycle.complete(oldOpen);
        PetManagementMenuSupport.ViewLifecycle.Request newerOpen = fixture.lifecycle.beginOpen(VIEWER);
        PetManagementInventoryHolder current = fixture.holder(fixture.session, "current");
        assertTrue(fixture.lifecycle.replace(newerOpen, current, fixture.view(fixture.session)));

        PetManagementInventoryHolder late = fixture.holder(fixture.session, "late");
        assertFalse(fixture.lifecycle.replace(oldOpen, late, fixture.view(fixture.session)));
        assertTrue(fixture.lifecycle.current(current));
    }

    @Test
    void lateAsyncActionResultCannotReplaceNewerViewAfterClose() {
        Fixture fixture = fixture();
        PetManagementInventoryHolder old = fixture.holder(fixture.session, "old");
        fixture.install(old, fixture.view(fixture.session));
        PetManagementMenuSupport.ViewLifecycle.Request oldAction = fixture.lifecycle.beginAction(old);
        assertNotNull(fixture.lifecycle.retire(old, old.getInventory()));
        PetManagementMenuSupport.ViewLifecycle.Request reopened = fixture.lifecycle.beginOpen(VIEWER);
        PetManagementInventoryHolder current = fixture.holder(fixture.session, "current");
        assertTrue(fixture.lifecycle.replace(reopened, current, fixture.view(fixture.session)));

        PetManagementInventoryHolder late = fixture.holder(fixture.session.withRevision(8), "late");
        assertFalse(fixture.lifecycle.replace(oldAction, late, fixture.view(fixture.session.withRevision(8))));
        assertTrue(fixture.lifecycle.current(current));
    }

    @Test
    void openRequestSharesBusyBoundaryWithActions() {
        Fixture fixture = fixture();
        PetManagementInventoryHolder current = fixture.holder(fixture.session, "current");
        fixture.install(current, fixture.view(fixture.session));
        PetManagementMenuSupport.ViewLifecycle.Request open = fixture.lifecycle.beginOpen(VIEWER);

        assertNotNull(open);
        assertNull(fixture.lifecycle.beginAction(current));
        fixture.lifecycle.complete(open);
        PetManagementMenuSupport.ViewLifecycle.Request action = fixture.lifecycle.beginAction(current);
        assertNotNull(action);
        assertNull(fixture.lifecycle.beginOpen(VIEWER));
    }

    @Test
    void shutdownRejectsOpenAndActionRequests() {
        Fixture fixture = fixture();
        PetManagementInventoryHolder current = fixture.holder(fixture.session, "current");
        fixture.install(current, fixture.view(fixture.session));

        assertNotNull(fixture.lifecycle.shutdown());
        assertNull(fixture.lifecycle.beginOpen(VIEWER));
        assertNull(fixture.lifecycle.beginAction(current));
    }

    private static final UUID VIEWER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PET = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static Fixture fixture() {
        return new Fixture(new PetManagementMenuSupport.ViewLifecycle(),
                new PetManagementSession(VIEWER, VIEWER, PET, UUID.randomUUID(), 7));
    }

    private record Fixture(
            PetManagementMenuSupport.ViewLifecycle lifecycle,
            PetManagementSession session) {
        void install(PetManagementInventoryHolder holder, PetManagementViewModel view) {
            PetManagementMenuSupport.ViewLifecycle.Request request = lifecycle.beginOpen(VIEWER);
            assertNotNull(request);
            assertTrue(lifecycle.replace(request, holder, view));
        }

        PetManagementInventoryHolder holder(PetManagementSession next, String name) {
            PetManagementInventoryHolder holder = new PetManagementInventoryHolder(
                    next, PetManagementInventoryHolder.View.MANAGEMENT, Map.of(), null);
            holder.bind(inventory(name));
            return holder;
        }

        PetManagementInventoryHolder releaseHolder(PetManagementSession next, String name) {
            ReleasePreview preview = new ReleasePreview(
                    UUID.randomUUID(), next.ownerId(), next.petId(), next.expectedRevision(),
                    "f".repeat(64), new ReleaseRewardBundle(List.of(), List.of()), "confirmation");
            PetManagementInventoryHolder holder = new PetManagementInventoryHolder(
                    next, PetManagementInventoryHolder.View.RELEASE_CONFIRMATION, Map.of(), preview);
            holder.bind(inventory(name));
            return holder;
        }

        PetManagementViewModel view(PetManagementSession next) {
            PetInstance pet = new PetInstance(PET, "wolf", 1, Map.of(), Map.of());
            PlayerState state = new PlayerState(
                    VIEWER, next.expectedRevision(), List.of(pet), 30, 1, List.of(), List.of(), Map.of(), null, Map.of());
            return PetManagementViewModel.create(next, state, null);
        }
    }

    private static Inventory inventory(String name) {
        return (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) return name;
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
