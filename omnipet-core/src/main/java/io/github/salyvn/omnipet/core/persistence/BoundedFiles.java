package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Reads a repository file only while it is still small enough to be one.
 *
 * <p>Every repository here reads a whole document into memory before decoding it, which is right for files
 * that hold one player or one definition and wrong for a file that has grown — by corruption, by an
 * unrelated program, or by someone dropping a large file into the data directory — into hundreds of
 * megabytes. Without a bound the first read of such a file is an out-of-memory error on the server thread
 * that asked for it, and an out-of-memory error is not something a plugin recovers from gracefully.
 *
 * <p>The bound is checked twice: once against the file size, and again against the bytes actually read, so
 * a file that grows between the two cannot slip past. The failure is an {@link IOException} naming the file
 * and both numbers, because the operator has to find and deal with that file and "out of memory" tells them
 * nothing about which one it was.
 */
public final class BoundedFiles {
    private BoundedFiles() {}

    /**
     * @param maxBytes the largest document this caller is willing to hold in memory, in bytes
     * @throws IOException when the file is larger than {@code maxBytes}, or cannot be read
     */
    public static String readString(Path path, int maxBytes) throws IOException {
        if (path == null) throw new IllegalArgumentException("path is required");
        if (maxBytes < 1) throw new IllegalArgumentException("byte bound must be positive");
        long size = Files.size(path);
        if (size > maxBytes) throw new IOException(tooLarge(path, size, maxBytes));
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            // One byte past the bound: enough to prove the file outgrew it without reading all of whatever
            // it became.
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new IOException(tooLarge(path, bytes.length, maxBytes));
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String tooLarge(Path path, long size, int maxBytes) {
        return "file is too large to read safely: " + path + " is " + size + " bytes, limit is " + maxBytes;
    }
}
