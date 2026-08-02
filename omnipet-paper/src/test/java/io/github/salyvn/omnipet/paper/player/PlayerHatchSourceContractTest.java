package io.github.salyvn.omnipet.paper.player;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PlayerHatchSourceContractTest {
    @Test
    void keepsClaimPaymentAndStaleViewGuards() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/salyvn/omnipet/paper/player/PlayerHatchController.java"));

        assertTrue(source.contains("escrow.stage() != EggEscrowStage.COMMITTED"));
        assertTrue(source.contains("mutationRequests.isCurrent(playerId, actionRequest)"));
        assertTrue(source.contains("player.getOpenInventory().getTopInventory() != expectedTop"));
        assertTrue(source.contains("player.getOpenInventory().getTopInventory() == expectedTop"));
    }
}
