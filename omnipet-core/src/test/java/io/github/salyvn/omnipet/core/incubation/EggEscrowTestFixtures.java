package io.github.salyvn.omnipet.core.incubation;

import java.util.Map;
import java.util.UUID;

final class EggEscrowTestFixtures {
    private EggEscrowTestFixtures() {}

    static EggEscrowTransaction transaction(UUID playerId, UUID transactionId) {
        return new EggEscrowTransaction(
                transactionId,
                playerId,
                transactionId,
                "tier_d_egg",
                0,
                new EggItemIdentity(
                        4,
                        EggInventoryHand.MAIN_HAND,
                        "minecraft:player_head",
                        UUID.randomUUID(),
                        "a".repeat(64),
                        3,
                        Map.of("itemVendor", "keep")),
                EggEscrowStage.PREPARED,
                Map.of("transactionVendor", true));
    }
}
