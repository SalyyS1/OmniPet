package io.github.salyvn.omnipet.core.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;

class FileStudioAuditSinkTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesCanonicalHumanReadableYaml() throws IOException {
        UUID token = UUID.fromString("168f174c-4381-446d-a9d0-2c74da257b48");
        FileStudioAuditSink sink = new FileStudioAuditSink(temporaryDirectory);

        sink.append(entry(token, "wolf", "saved\nsafely"));

        String text = Files.readString(file(token));
        assertEquals("""
                schema: 1
                timestamp: "2026-08-02T04:00:00Z"
                token: "168f174c-4381-446d-a9d0-2c74da257b48"
                operation: "SAVE"
                definition: "wolf"
                generation: 12
                success: true
                message: "saved\\nsafely"
                """, text);
        assertTrue(Files.size(file(token)) <= FileStudioAuditSink.MAX_FILE_BYTES);
    }

    @Test
    void rollbackRemovesNewRecordAndRestoresBackupState() throws IOException {
        UUID token = UUID.randomUUID();
        Path target = file(token);
        Path backup = AtomicFileStore.backupPath(target);
        Files.writeString(backup, "prior-backup");
        FileStudioAuditSink sink = new FileStudioAuditSink(temporaryDirectory);

        StudioAuditReceipt receipt = sink.append(entry(token, "wolf", "saved"));
        receipt.rollback();
        receipt.rollback();

        assertFalse(Files.exists(target));
        assertEquals("prior-backup", Files.readString(backup));
    }

    @Test
    void identicalDuplicateIsNoOpAndDoesNotRemoveOriginal() throws IOException {
        UUID token = UUID.randomUUID();
        StudioAuditEntry entry = entry(token, "wolf", "saved");
        FileStudioAuditSink sink = new FileStudioAuditSink(temporaryDirectory);
        StudioAuditReceipt original = sink.append(entry);
        byte[] bytes = Files.readAllBytes(file(token));

        StudioAuditReceipt duplicate = sink.append(entry);
        duplicate.rollback();

        assertTrue(Files.exists(file(token)));
        assertArrayEquals(bytes, Files.readAllBytes(file(token)));
        original.rollback();
        assertFalse(Files.exists(file(token)));
    }

    @Test
    void rejectsMismatchedRecordForExistingToken() throws IOException {
        UUID token = UUID.randomUUID();
        FileStudioAuditSink sink = new FileStudioAuditSink(temporaryDirectory);
        sink.append(entry(token, "wolf", "saved"));
        String original = Files.readString(file(token));

        assertThrows(IOException.class, () -> sink.append(entry(token, "fox", "different")));

        assertEquals(original, Files.readString(file(token)));
    }

    @Test
    void rejectsSymbolicLinkRecordTarget() throws IOException {
        UUID token = UUID.randomUUID();
        Path outside = temporaryDirectory.resolveSibling(temporaryDirectory.getFileName() + "-outside.yml");
        Files.writeString(outside, "outside");
        try {
            Files.createSymbolicLink(file(token), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "symbolic links unavailable: " + unsupported);
        }
        FileStudioAuditSink sink = new FileStudioAuditSink(temporaryDirectory);

        assertThrows(IOException.class, () -> sink.append(entry(token, "wolf", "saved")));
        assertEquals("outside", Files.readString(outside));
    }

    @Test
    void rejectsOversizedContent() {
        UUID token = UUID.randomUUID();
        FileStudioAuditSink sink = new FileStudioAuditSink(temporaryDirectory);
        StudioAuditEntry oversized = entry(token, "wolf", "x".repeat(9 * 1024));

        assertThrows(IllegalArgumentException.class, () -> sink.append(oversized));
        assertFalse(Files.exists(file(token)));
    }

    private Path file(UUID token) {
        return temporaryDirectory.resolve(token + ".yml");
    }

    private static StudioAuditEntry entry(UUID token, String definition, String message) {
        return new StudioAuditEntry(
                Instant.parse("2026-08-02T04:00:00Z"),
                token,
                StudioAuditEntry.Operation.SAVE,
                definition,
                12,
                true,
                message);
    }
}
