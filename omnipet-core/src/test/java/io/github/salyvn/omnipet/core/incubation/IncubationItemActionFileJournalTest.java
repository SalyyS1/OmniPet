package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncubationItemActionFileJournalTest {
    @TempDir Path temporary;

    @Test
    void persistsExactIdentityAndTransitionsAcrossRestart() throws Exception {
        UUID token = UUID.randomUUID();
        IncubationItemActionTransaction prepared = transaction(token, UUID.randomUUID(), UUID.randomUUID());
        IncubationItemActionService first = new IncubationItemActionService(
                new IncubationItemActionFileJournal(temporary.resolve("actions")));

        first.prepare(prepared);
        first.markItemRemoved(token);

        IncubationItemActionService restarted = new IncubationItemActionService(
                new IncubationItemActionFileJournal(temporary.resolve("actions")));
        IncubationItemActionTransaction loaded = restarted.find(token).orElseThrow();
        assertEquals(prepared.withStage(IncubationItemActionStage.ITEM_REMOVED), loaded);
        assertEquals(IncubationItemActionStage.COMMITTED, restarted.commit(token).stage());
    }

    @Test
    void copiedTokenCannotChangePlayerOrIncubation() throws Exception {
        UUID token = UUID.randomUUID();
        IncubationItemActionService service = new IncubationItemActionService(
                new IncubationItemActionFileJournal(temporary.resolve("actions")));
        service.prepare(transaction(token, UUID.randomUUID(), UUID.randomUUID()));

        assertThrows(java.io.IOException.class,
                () -> service.prepare(transaction(token, UUID.randomUUID(), UUID.randomUUID())));
    }

    private static IncubationItemActionTransaction transaction(UUID token, UUID player, UUID incubation) {
        return new IncubationItemActionTransaction(
                token, player, incubation, 4,
                new EggItemIdentity(2, EggInventoryHand.MAIN_HAND, "minecraft:paper", UUID.randomUUID(),
                        "a".repeat(64), 3, IncubationItemActionItemContract.bind(
                                Map.of("payload", "reducer"), IncubationItemActionType.REDUCE, 1_000)),
                IncubationItemActionType.REDUCE, 1_000, IncubationItemActionStage.PREPARED);
    }
}
