package io.github.salyvn.omnipet.core.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class StatLogicalIdentityTest {
    @Test
    void treatsLegacyAndNamespacedMythicLibIdsAsOneLogicalStat() {
        assertEquals("mythiclib:ATTACK_DAMAGE", StatLogicalIdentity.key("ATTACK_DAMAGE", Map.of()));
        assertEquals("mythiclib:ATTACK_DAMAGE", StatLogicalIdentity.key("mythiclib:attack_damage", Map.of()));
        assertEquals("mythiclib:ATTACK_DAMAGE",
                StatLogicalIdentity.key("custom-id", Map.of("vendorStatId", "ATTACK_DAMAGE")));
        assertEquals("other:attack_damage", StatLogicalIdentity.key("OTHER:ATTACK_DAMAGE", Map.of()));
    }
}
