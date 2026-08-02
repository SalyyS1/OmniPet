package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.release.ReleasePreview;
import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;
import io.github.salyvn.omnipet.paper.management.PetManagementSession;
import io.github.salyvn.omnipet.paper.management.PetManagementViewModel;

class PetManagementMenuContractTest {
    @Test
    void holderSnapshotsActionsInsteadOfTrustingCallerMutation() {
        PetManagementSession session = view(null).session();
        Map<Integer, PetManagementInventoryHolder.Action> actions = new LinkedHashMap<>();
        PetManagementInventoryHolder.Action favorite = PetManagementInventoryHolder.Action.favorite(true);
        actions.put(10, favorite);
        PetManagementInventoryHolder holder = new PetManagementInventoryHolder(
                session, PetManagementInventoryHolder.View.MANAGEMENT, actions, null);

        actions.put(10, PetManagementInventoryHolder.Action.lock(true));
        actions.put(11, PetManagementInventoryHolder.Action.simple(PetManagementInventoryHolder.Type.BACK));

        assertEquals(favorite, holder.action(10));
        assertNull(holder.action(11));
    }

    @Test
    void replacementHoldersCarryDistinctInventoryGenerations() {
        PetManagementSession session = view(null).session();
        PetManagementInventoryHolder first = new PetManagementInventoryHolder(
                session, PetManagementInventoryHolder.View.MANAGEMENT, Map.of(), null);
        PetManagementInventoryHolder replacement = new PetManagementInventoryHolder(
                session, PetManagementInventoryHolder.View.MANAGEMENT, Map.of(), null);

        assertNotNull(first.generationId());
        assertNotNull(replacement.generationId());
        assertFalse(first.generationId().equals(replacement.generationId()));
    }

    @Test
    void managementLayoutBindsStableIdentitiesAndOnlyAcceptsSafeTopClicks() {
        PetManagementViewModel view = view(null);
        PetManagementMenuRenderer.Layout layout = new PetManagementMenuRenderer().layout(view);
        Inventory inventory = inventory();
        layout.holder().bind(inventory);

        assertEquals(view.session().viewerId(), layout.holder().viewerId());
        assertEquals(view.session().ownerId(), layout.holder().ownerId());
        assertEquals(view.pet().id(), layout.holder().petId());
        assertEquals(view.session().sessionId(), layout.holder().sessionId());
        assertEquals(view.session().expectedRevision(), layout.holder().expectedRevision());
        assertNotNull(layout.holder().action(10));
        assertTrue(PetManagementInventoryGuard.acceptsAction(
                layout.holder(), view.session().viewerId(), inventory, 10, 45, ClickType.LEFT));
        assertFalse(PetManagementInventoryGuard.acceptsAction(
                layout.holder(), view.session().viewerId(), inventory, 10, 45, ClickType.NUMBER_KEY));
        assertFalse(PetManagementInventoryGuard.acceptsAction(
                layout.holder(), UUID.randomUUID(), inventory, 10, 45, ClickType.LEFT));
    }

    @Test
    void releaseLayoutCarriesFrozenPreviewAndDragGuardProtectsTopInventory() {
        PetManagementViewModel view = view(new ReleasePreview(
                UUID.randomUUID(), OWNER, PET, 7, "f".repeat(64),
                new ReleaseRewardBundle(
                        List.of(new ReleaseRewardBundle.InternalReward("pet_dust", 4, Map.of())), List.of()),
                "confirmation"));
        PetManagementMenuRenderer.Layout layout = new PetManagementMenuRenderer().layout(view);

        assertEquals(PetManagementInventoryHolder.View.RELEASE_CONFIRMATION, layout.holder().view());
        assertEquals(view.releasePreview(), layout.holder().releasePreview());
        assertNotNull(layout.holder().action(15));
        assertTrue(layout.entries().get(15).lore().stream().anyMatch(line -> line.contains("pet_dust")));
        assertTrue(PetManagementInventoryGuard.cancelsDrag(Set.of(5, 30), 27));
        assertFalse(PetManagementInventoryGuard.cancelsDrag(Set.of(27, 30), 27));
    }

    private static final UUID VIEWER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PET = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static PetManagementViewModel view(ReleasePreview preview) {
        PetInstance pet = new PetInstance(PET, "wolf", 1, Map.of(), Map.of());
        PlayerState state = new PlayerState(
                OWNER, 7, List.of(pet), 30, 1, List.of(PET), List.of(), Map.of(), null, Map.of());
        PetManagementSession session = new PetManagementSession(
                VIEWER, OWNER, PET, UUID.randomUUID(), state.revision());
        return PetManagementViewModel.create(session, state, preview);
    }

    private static Inventory inventory() {
        return (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) return "inventory";
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
