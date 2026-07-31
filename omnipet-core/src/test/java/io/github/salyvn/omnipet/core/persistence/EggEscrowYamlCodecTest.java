package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.incubation.EggEscrowStage;
import io.github.salyvn.omnipet.core.incubation.EggEscrowTransaction;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

class EggEscrowYamlCodecTest {
    private final EggEscrowYamlCodec codec = new EggEscrowYamlCodec();

    @Test
    void roundTripsIdentityStageAndUnknownNodes() {
        UUID id = UUID.randomUUID();
        Map<String, Object> itemExtensions = new java.util.LinkedHashMap<>();
        itemExtensions.put("futureItem", null);
        EggEscrowTransaction transaction = new EggEscrowTransaction(
                id,
                UUID.randomUUID(),
                id,
                "tier_s_egg",
                8,
                new EggItemIdentity(
                        40, EggInventoryHand.OFF_HAND, "minecraft:player_head", UUID.randomUUID(),
                        "F".repeat(64), 12, itemExtensions),
                EggEscrowStage.ITEM_REMOVED,
                Map.of("futureTransaction", "keep"));

        EggEscrowTransaction decoded = codec.decode(new String(codec.encode(transaction), java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(transaction, decoded);
        assertEquals("f".repeat(64), decoded.item().fingerprint());
    }
}
