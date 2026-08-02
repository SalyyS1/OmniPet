package io.github.salyvn.omnipet.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

class CultivationItemActionFileJournalTest {
    @TempDir Path temporary;

    @Test
    void restartPreservesStageAndRejectsNonceIdentityMismatch() throws Exception {
        UUID token = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        Path root = temporary.resolve("cultivation-actions");
        CultivationItemActionTransaction prepared = action(
                token, playerId, petId, "a".repeat(64));
        CultivationItemActionService first = new CultivationItemActionService(
                new CultivationItemActionFileJournal(root));

        first.prepare(prepared);
        first.markItemRemoved(token);

        CultivationItemActionService restarted = new CultivationItemActionService(
                new CultivationItemActionFileJournal(root));
        assertEquals(
                prepared.withStage(CultivationItemActionStage.ITEM_REMOVED),
                restarted.find(token).orElseThrow());
        assertThrows(IOException.class, () -> restarted.prepare(
                action(token, playerId, petId, "b".repeat(64))));
    }

    private static CultivationItemActionTransaction action(
            UUID token,
            UUID playerId,
            UUID petId,
            String fingerprint) {
        return new CultivationItemActionTransaction(
                token,
                playerId,
                petId,
                1,
                new EggItemIdentity(
                        4,
                        EggInventoryHand.MAIN_HAND,
                        "minecraft:paper",
                        token,
                        fingerprint,
                        1,
                        Map.of("source", "test")),
                CultivationItemActionKind.EXPERIENCE_CANDY,
                250,
                1,
                0,
                CultivationItemActionStage.PREPARED);
    }
}
