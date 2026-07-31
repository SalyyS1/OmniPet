package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlayerPetInventoryHolderTest {
    @Test
    void keepsStableRevisionAndLiveImmutableActionView() {
        UUID viewerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        Map<Integer, PlayerPetInventoryHolder.Action> actions = new HashMap<>();
        PlayerPetInventoryHolder holder = new PlayerPetInventoryHolder(viewerId, 7, 2, actions);

        actions.put(10, PlayerPetInventoryHolder.Action.pet(petId, true));

        assertEquals(viewerId, holder.viewerId());
        assertEquals(7, holder.expectedRevision());
        assertEquals(2, holder.page());
        assertEquals(petId, holder.action(10).petId());
    }

    @Test
    void rejectsInvalidHolderAndActionPayloads() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerPetInventoryHolder(UUID.randomUUID(), -1, 1, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerPetInventoryHolder(UUID.randomUUID(), 0, 0, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerPetInventoryHolder.Action(PlayerPetInventoryHolder.Type.PET, null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerPetInventoryHolder.Action(
                        PlayerPetInventoryHolder.Type.NEXT, UUID.randomUUID(), false));
    }
}
