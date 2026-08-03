package io.github.salyvn.omnipet.paper.gui.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

/**
 * The hub view must be derivable from one player-state read, and its holder must snapshot its actions
 * the way every other OmniPet holder does.
 */
class HubViewTest {
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void oneReadYieldsBothTheStorageAndIncubationHalves() {
        PlayerState state = state(2, List.of());

        HubView view = HubView.from(state, limits());

        assertEquals(OWNER, view.viewerId());
        assertEquals(2, view.storage().ownedCount());
        assertNull(view.incubation(), "no incubation was set");
    }

    @Test
    void theStorageHalfMatchesTheSameFactoryTheVaultUses() {
        PlayerState state = state(3, List.of());
        PetStorageLimits limits = limits();

        HubView view = HubView.from(state, limits);
        PetStorageSnapshot direct = PetStorageSnapshot.from(state, limits);

        assertEquals(direct.revision(), view.storage().revision());
        assertEquals(direct.ownedCount(), view.storage().ownedCount());
        assertEquals(direct.effectiveVaultCapacity(), view.storage().effectiveVaultCapacity());
        assertEquals(direct.effectiveActiveSlotCount(), view.storage().effectiveActiveSlotCount());
    }

    @Test
    void theRevisionCarriesThroughSoAStaleClickIsDetectable() {
        HubView view = HubView.from(state(1, List.of()), limits());

        assertEquals(7, view.storage().revision());
    }

    @Test
    void aNullStateOrLimitsIsRejected() {
        assertThrows(NullPointerException.class, () -> HubView.from(null, limits()));
        assertThrows(NullPointerException.class, () -> HubView.from(state(1, List.of()), null));
    }

    @Test
    void theHolderSnapshotsItsActionsInsteadOfTrustingCallerMutation() {
        Map<Integer, HubInventoryHolder.Action> actions = new LinkedHashMap<>();
        actions.put(11, HubInventoryHolder.Action.VAULT);
        HubInventoryHolder holder = new HubInventoryHolder(OWNER, 7, actions);

        actions.put(11, HubInventoryHolder.Action.HATCH);
        actions.put(13, HubInventoryHolder.Action.SLOTS);

        assertEquals(HubInventoryHolder.Action.VAULT, holder.action(11));
        assertNull(holder.action(13));
    }

    @Test
    void theHolderRejectsANegativeRevisionAndANullViewer() {
        assertThrows(IllegalArgumentException.class, () -> new HubInventoryHolder(OWNER, -1, Map.of()));
        assertThrows(NullPointerException.class, () -> new HubInventoryHolder(null, 0, Map.of()));
    }

    @Test
    void anInventoryCanOnlyBeBoundOnce() {
        HubInventoryHolder holder = new HubInventoryHolder(OWNER, 1, Map.of());
        holder.bind(new java.util.concurrent.atomic.AtomicReference<org.bukkit.inventory.Inventory>()
                .updateAndGet(ignored -> inventory()));

        assertThrows(IllegalStateException.class, () -> holder.bind(inventory()));
    }

    @Test
    void everyTileDestinationIsRepresentedExactlyOnce() {
        // A duplicate or missing enum constant would leave a tile unroutable.
        assertEquals(5, HubInventoryHolder.Action.values().length);
        assertNotNull(HubInventoryHolder.Action.valueOf("VAULT"));
        assertNotNull(HubInventoryHolder.Action.valueOf("HATCH"));
        assertNotNull(HubInventoryHolder.Action.valueOf("SLOTS"));
        assertNotNull(HubInventoryHolder.Action.valueOf("HELP"));
        assertNotNull(HubInventoryHolder.Action.valueOf("STUDIO"));
    }

    @Test
    void anOverflowingVaultIsVisibleInTheSummary() {
        // Three pets against an effective capacity of 2: one overflows. The hub tile must be able
        // to warn about it, exactly as the vault status row does.
        PlayerState state = new PlayerState(
                OWNER, 7, pets(3), 2, 1, List.of(), List.of(), Map.of(), null, Map.of());

        HubView view = HubView.from(state, new PetStorageLimits(2, 1, true));

        assertEquals(1, view.storage().vaultOverflow());
        assertFalse(view.storage().pets().isEmpty());
    }

    private static PlayerState state(int petCount, List<UUID> active) {
        return new PlayerState(
                OWNER, 7, pets(petCount), 30, 1, active, List.of(), Map.of(), null, Map.of());
    }

    private static List<PetInstance> pets(int count) {
        List<PetInstance> pets = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            pets.add(new PetInstance(UUID.randomUUID(), "wolf", 1, Map.of(), Map.of()));
        }
        return List.copyOf(pets);
    }

    private static PetStorageLimits limits() {
        return new PetStorageLimits(30, 1, true);
    }

    private static org.bukkit.inventory.Inventory inventory() {
        return (org.bukkit.inventory.Inventory) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.inventory.Inventory.class.getClassLoader(),
                new Class<?>[] {org.bukkit.inventory.Inventory.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) return "inventory";
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    static {
        assert IncubationState.class != null;
    }
}
