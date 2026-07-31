package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class PurchaseJournalFileReader {
    /** Schema v2 has three 512-character text fields; 16 KiB leaves ample YAML and legacy headroom. */
    static final int MAX_ENTRY_BYTES = 16 * 1024;

    private PurchaseJournalFileReader() {}

    static String readUtf8(Path path) throws IOException {
        long size = Files.size(path);
        if (size > MAX_ENTRY_BYTES) throw oversized(path);
        try (InputStream input = Files.newInputStream(
                path,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_ENTRY_BYTES + 1);
            if (bytes.length > MAX_ENTRY_BYTES) throw oversized(path);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static IOException oversized(Path path) {
        return new IOException("purchase journal entry exceeds " + MAX_ENTRY_BYTES + " bytes: " + path);
    }
}
