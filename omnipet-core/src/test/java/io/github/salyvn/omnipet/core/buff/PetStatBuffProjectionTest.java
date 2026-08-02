package io.github.salyvn.omnipet.core.buff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;

class PetStatBuffProjectionTest {
    @Test
    void readsOnlyActiveFiniteSupportedStats() {
        PetInstance active = new PetInstance(UUID.randomUUID(), "ember_fox", 1, Map.of("stats", List.of(
                Map.of("id", "ATTACK_DAMAGE", "modifierType", "FLAT", "value", 4.5),
                Map.of("id", "MOVE_SPEED", "modifierType", "RELATIVE", "value", 0.1),
                Map.of("id", "BROKEN", "modifierType", "UNKNOWN", "value", 3))), Map.of());
        PetInstance stored = new PetInstance(UUID.randomUUID(), "wolf", 1, Map.of("stats", List.of(
                Map.of("id", "MAX_HEALTH", "value", 20))), Map.of());

        List<PetStatBuff> buffs = PetStatBuffProjection.readActive(
                List.of(active, stored), List.of(active.id()));

        assertEquals(2, buffs.size());
        assertEquals("ATTACK_DAMAGE", buffs.getFirst().statId());
        assertEquals("RELATIVE", buffs.get(1).modifierType());
    }
}
