package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.incubation.EggEscrowStage;
import io.github.salyvn.omnipet.core.incubation.EggEscrowTransaction;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;

class PaperIncubationServicesTest {
    @TempDir
    Path temporary;

    @Test
    void bindsCanonicalEggAndEscrowPathsAndScansPetReferences() throws Exception {
        Path dataRoot = temporary.resolve("OmniPet");
        Files.createDirectories(dataRoot.resolve("eggs"));
        Files.writeString(dataRoot.resolve("eggs/tier_d_egg.yml"), """
                schemaVersion: 1
                eggId: tier_d_egg
                tier: D
                baseDuration: 1h
                candidates:
                  - { definitionId: ember_fox, weight: 1 }
                """);
        FilePlayerStateRepository players = new FilePlayerStateRepository(dataRoot.resolve("data/players"));

        PaperIncubationServices services = PaperIncubationServices.open(dataRoot, players);
        Files.delete(dataRoot.resolve("eggs/tier_d_egg.yml"));

        assertEquals(1, services.eggDefinitionCount());
        assertTrue(Files.isDirectory(dataRoot.resolve("data/egg-escrow")));
        assertEquals(java.util.Set.of("egg:tier_d_egg"), services.petReferences().references("ember_fox"));
        assertTrue(services.petReferences().references("other_pet").isEmpty());

        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        players.withLocked(id, 0, state -> state);
        EggItemIdentity item = new EggItemIdentity(
                0,
                EggInventoryHand.MAIN_HAND,
                "minecraft:player_head",
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "a".repeat(64),
                1,
                PaperEggItemSnapshot.extensions(new byte[] {1}));
        services.itemEscrow().prepare(new EggEscrowTransaction(
                id, id, id, "tier_d_egg", 0, item, EggEscrowStage.PREPARED, java.util.Map.of()));

        assertTrue(Files.isRegularFile(dataRoot.resolve("data/egg-escrow/" + id + ".yml")));
        assertEquals(id, services.hatches().snapshot(id).playerId());
    }

    @Test
    void rejectsAnInvalidEscrowPathDuringBootstrap() throws Exception {
        Path dataRoot = temporary.resolve("BadOmniPet");
        Files.createDirectories(dataRoot.resolve("eggs"));
        Files.createDirectories(dataRoot.resolve("data"));
        Files.writeString(dataRoot.resolve("data/egg-escrow"), "not-a-directory");

        assertThrows(java.io.IOException.class, () -> PaperIncubationServices.open(
                dataRoot,
                new FilePlayerStateRepository(dataRoot.resolve("data/players"))));
    }
}
