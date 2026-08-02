package io.github.salyvn.omnipet.core.studio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;
import io.github.salyvn.omnipet.core.persistence.SafeRepositoryPaths;

/** Durable one-file-per-token Studio audit journal. */
public final class FileStudioAuditSink implements StudioAuditSink {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_FILE_BYTES = 16 * 1024;
    private static final int MAX_DEFINITION_BYTES = 1024;
    private static final int MAX_MESSAGE_BYTES = 8 * 1024;

    private final SafeRepositoryPaths paths;
    private final AtomicFileStore files;

    public FileStudioAuditSink(Path root) {
        this.paths = new SafeRepositoryPaths(root);
        this.files = new AtomicFileStore();
    }

    @Override
    public synchronized StudioAuditReceipt append(StudioAuditEntry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        byte[] content = serialize(entry);
        Path target = paths.resolveUuid(entry.idempotencyKey(), ".yml");
        Path backup = paths.requireSafe(AtomicFileStore.backupPath(target));
        byte[] previous = readBounded(target);
        byte[] previousBackup = readBounded(backup);

        if (previous != null) {
            if (Arrays.equals(previous, content)) return StudioAuditReceipt.noop();
            throw new IOException("audit token already exists with different content: " + entry.idempotencyKey());
        }

        files.write(target, content);
        return new FileReceipt(this, target, content, previous, previousBackup);
    }

    private synchronized void rollback(
            Path target,
            byte[] written,
            byte[] previous,
            byte[] previousBackup) throws IOException {
        Path safeTarget = paths.requireSafe(target);
        paths.requireSafe(AtomicFileStore.backupPath(safeTarget));
        byte[] current = readBounded(safeTarget);
        if (!Arrays.equals(current, written)) {
            throw new IOException("audit record changed before rollback: " + safeTarget.getFileName());
        }
        files.restore(safeTarget, previous, previousBackup);
    }

    private static byte[] serialize(StudioAuditEntry entry) {
        requireBounded("definition", entry.definitionId(), MAX_DEFINITION_BYTES);
        requireBounded("message", entry.message(), MAX_MESSAGE_BYTES);
        if (entry.registryGeneration() < 0) throw new IllegalArgumentException("audit generation cannot be negative");

        String text = "schema: " + SCHEMA_VERSION + "\n"
                + "timestamp: " + quote(entry.timestamp().toString()) + "\n"
                + "token: " + quote(entry.idempotencyKey().toString()) + "\n"
                + "operation: " + quote(entry.operation().name()) + "\n"
                + "definition: " + quote(entry.definitionId()) + "\n"
                + "generation: " + entry.registryGeneration() + "\n"
                + "success: " + entry.success() + "\n"
                + "message: " + quote(entry.message()) + "\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("audit record is too large");
        return bytes;
    }

    private static void requireBounded(String field, String value, int maximumBytes) {
        if (value == null) throw new IllegalArgumentException("audit " + field + " is required");
        if (value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
            throw new IllegalArgumentException("audit " + field + " is too large");
        }
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20 || character == 0x7f) appendUnicodeEscape(result, character);
                    else result.append(character);
                }
            }
        }
        return result.append('"').toString();
    }

    private static void appendUnicodeEscape(StringBuilder target, char character) {
        target.append("\\u");
        String hex = Integer.toHexString(character);
        target.append("0".repeat(4 - hex.length())).append(hex);
    }

    private static byte[] readBounded(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("audit path is not a regular file: " + path);
        }
        long size = Files.size(path);
        if (size > MAX_FILE_BYTES) throw new IOException("audit file exceeds size limit: " + path.getFileName());
        return Files.readAllBytes(path);
    }

    private static final class FileReceipt implements StudioAuditReceipt {
        private final FileStudioAuditSink owner;
        private final Path target;
        private final byte[] written;
        private final byte[] previous;
        private final byte[] previousBackup;
        private boolean rolledBack;

        private FileReceipt(
                FileStudioAuditSink owner,
                Path target,
                byte[] written,
                byte[] previous,
                byte[] previousBackup) {
            this.owner = owner;
            this.target = target;
            this.written = written.clone();
            this.previous = previous == null ? null : previous.clone();
            this.previousBackup = previousBackup == null ? null : previousBackup.clone();
        }

        @Override
        public synchronized void rollback() throws IOException {
            if (rolledBack) return;
            owner.rollback(target, written, previous, previousBackup);
            rolledBack = true;
        }
    }
}
