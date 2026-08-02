package io.github.salyvn.omnipet.paper.player;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PlayerPetRuntimeCallbackContractTest {
    @Test
    void persistedTogglePublishesBeforeAnyVaultViewGuardCanDropTheUiCompletion() throws Exception {
        String source = Files.readString(controllerSource());
        int mutation = source.indexOf("PetStorageResult result = action.active()");
        int publish = source.indexOf("publishSnapshot(result.snapshot())", mutation);
        int ui = source.indexOf("completeUi(player, playerId, request, expectedTop, true", mutation);

        assertTrue(mutation >= 0 && publish > mutation && ui > publish);
    }

    @Test
    void reconciliationPublishesBeforeMainThreadPlayerAndInventoryChecks() throws Exception {
        String source = Files.readString(controllerSource());
        int method = source.indexOf("private void reconcileAsync");
        int publish = source.indexOf("publishSnapshot(completed.snapshot())", method);
        int ui = source.indexOf("runMain(() ->", publish);

        assertTrue(method >= 0 && publish > method && ui > publish);
    }

    private static Path controllerSource() {
        Path local = Path.of("src/main/java/io/github/salyvn/omnipet/paper/player/PlayerPetController.java");
        return Files.exists(local) ? local : Path.of("omnipet-paper").resolve(local);
    }
}
