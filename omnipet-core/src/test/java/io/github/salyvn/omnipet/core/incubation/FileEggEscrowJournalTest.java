package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.persistence.EggEscrowYamlCodec;

class FileEggEscrowJournalTest {
    @TempDir
    Path temporary;

    @Test
    void createSaveReloadAndPlayerScanPreserveIdentity() throws Exception {
        UUID playerId = UUID.randomUUID();
        EggEscrowTransaction prepared = EggEscrowTestFixtures.transaction(playerId, UUID.randomUUID());
        FileEggEscrowJournal journal = new FileEggEscrowJournal(temporary.resolve("escrow"));

        assertEquals(EggEscrowCreateResult.Status.CREATED, journal.create(prepared).status());
        assertEquals(EggEscrowTransitionResult.Status.APPLIED,
                journal.transition(prepared.transactionId(), Set.of(EggEscrowStage.PREPARED),
                        EggEscrowStage.ITEM_REMOVED).status());

        assertEquals(EggEscrowStage.ITEM_REMOVED,
                journal.find(prepared.transactionId()).orElseThrow().stage());
        assertEquals(1, journal.findByPlayer(playerId, Set.of(EggEscrowStage.ITEM_REMOVED), 10).size());
        assertEquals(0, journal.findByPlayer(UUID.randomUUID(), Set.of(EggEscrowStage.ITEM_REMOVED), 10).size());
    }

    @Test
    void rejectsIdentityMutationAndOversizedEntries() throws Exception {
        UUID id = UUID.randomUUID();
        EggEscrowTransaction prepared = EggEscrowTestFixtures.transaction(UUID.randomUUID(), id);
        Path root = temporary.resolve("escrow");
        FileEggEscrowJournal journal = new FileEggEscrowJournal(root);
        journal.create(prepared);
        EggEscrowTransaction different = EggEscrowTestFixtures.transaction(UUID.randomUUID(), id);

        assertThrows(IOException.class, () -> journal.create(different));

        UUID oversizedId = UUID.randomUUID();
        Files.writeString(root.resolve(oversizedId + ".yml"), "#".repeat((int) FileEggEscrowJournal.MAX_FILE_BYTES + 1));
        assertThrows(IOException.class, () -> journal.find(oversizedId));
    }

    @Test
    void rejectsOversizedEncodedWritesBeforeCreatingAnUnreadableEntry() throws Exception {
        Path root = temporary.resolve("escrow");
        EggEscrowTransaction original = EggEscrowTestFixtures.transaction(UUID.randomUUID(), UUID.randomUUID());
        EggEscrowTransaction oversized = new EggEscrowTransaction(
                original.transactionId(), original.playerId(), original.incubationId(), original.eggId(),
                original.expectedPlayerRevision(), original.item(), original.stage(), Map.of("padding", "e".repeat(20_000)));
        FileEggEscrowJournal journal = new FileEggEscrowJournal(root);

        assertThrows(IOException.class, () -> journal.create(oversized));
        assertFalse(Files.exists(root.resolve(oversized.transactionId() + ".yml")));
    }

    @Test
    void rejectsNonCanonicalUuidFilenameAliasesDuringScan() throws Exception {
        Path root = temporary.resolve("escrow");
        Files.createDirectories(root);
        UUID transactionId = UUID.fromString("abcdefab-cdef-abcd-efab-cdefabcdefab");
        EggEscrowTransaction transaction = EggEscrowTestFixtures.transaction(UUID.randomUUID(), transactionId);
        String alias = transactionId.toString().toUpperCase(java.util.Locale.ROOT) + ".yml";
        Files.write(root.resolve(alias), new EggEscrowYamlCodec().encode(transaction));

        assertThrows(IOException.class, () -> new FileEggEscrowJournal(root)
                .findByPlayer(transaction.playerId(), Set.of(EggEscrowStage.PREPARED), 10));
    }

    @Test
    void rejectsOversizedTransitionWithoutReplacingTheLastReadableStage() throws Exception {
        EggEscrowTransaction original = EggEscrowTestFixtures.transaction(UUID.randomUUID(), UUID.randomUUID());
        EggEscrowYamlCodec codec = new EggEscrowYamlCodec();
        EggEscrowTransaction sample = withPadding(original, 1_000);
        int exactPadding = 1_000 + (int) FileEggEscrowJournal.MAX_FILE_BYTES - codec.encode(sample).length;
        EggEscrowTransaction prepared = withPadding(original, exactPadding);
        FileEggEscrowJournal journal = new FileEggEscrowJournal(temporary.resolve("escrow"));

        assertTrue(codec.encode(prepared).length <= FileEggEscrowJournal.MAX_FILE_BYTES);
        journal.create(prepared);
        assertThrows(IOException.class, () -> journal.transition(
                prepared.transactionId(), Set.of(EggEscrowStage.PREPARED), EggEscrowStage.REFUND_PENDING));
        assertEquals(EggEscrowStage.PREPARED, journal.find(prepared.transactionId()).orElseThrow().stage());
    }

    private static EggEscrowTransaction withPadding(EggEscrowTransaction original, int paddingLength) {
        return new EggEscrowTransaction(
                original.transactionId(), original.playerId(), original.incubationId(), original.eggId(),
                original.expectedPlayerRevision(), original.item(), EggEscrowStage.PREPARED,
                Map.of("padding", "x".repeat(paddingLength)));
    }
}
