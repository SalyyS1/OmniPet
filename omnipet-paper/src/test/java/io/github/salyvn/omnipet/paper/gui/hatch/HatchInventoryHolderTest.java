package io.github.salyvn.omnipet.paper.gui.hatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HatchInventoryHolderTest {
    @Test
    void keepsTheLiveActionMapAndIncubationIdentity() {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        Map<Integer, HatchInventoryHolder.Action> actions = new HashMap<>();
        HatchInventoryHolder holder = new HatchInventoryHolder(playerId, 4, incubationId, actions);

        actions.put(13, HatchInventoryHolder.Action.claim());

        assertEquals(playerId, holder.viewerId());
        assertEquals(4, holder.expectedRevision());
        assertEquals(incubationId, holder.incubationId());
        assertEquals(HatchInventoryHolder.Type.CLAIM, holder.action(13).type());
    }

    @Test
    void rejectsNegativeRevisionsAndMissingActionTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> new HatchInventoryHolder(UUID.randomUUID(), -1, null, Map.of()));
        assertThrows(NullPointerException.class,
                () -> new HatchInventoryHolder.Action(null));
    }
}
