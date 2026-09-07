package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedFilesTest {
    @TempDir
    Path directory;

    @Test
    void readsAFileThatFitsWithinTheBound() throws Exception {
        Path file = directory.resolve("small.yml");
        Files.writeString(file, "uuid: abc\n");

        assertEquals("uuid: abc\n", BoundedFiles.readString(file, 1024));
    }

    @Test
    void readsAFileExactlyAtTheBound() throws Exception {
        Path file = directory.resolve("exact.yml");
        String content = "0123456789";
        Files.writeString(file, content);

        assertEquals(content, BoundedFiles.readString(file, content.length()));
    }

    @Test
    void refusesAFileOverTheBoundAndSaysBothNumbers() throws Exception {
        Path file = directory.resolve("large.yml");
        Files.writeString(file, "0123456789ab");

        IOException failure = assertThrows(IOException.class, () -> BoundedFiles.readString(file, 10));

        assertTrue(failure.getMessage().contains("large.yml"), failure.getMessage());
        assertTrue(failure.getMessage().contains("12"), failure.getMessage());
        assertTrue(failure.getMessage().contains("10"), failure.getMessage());
    }

    @Test
    void decodesUtf8RatherThanThePlatformCharset() throws Exception {
        Path file = directory.resolve("utf8.yml");
        String content = "name: Bé Mèo ⚔\n";
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));

        assertEquals(content, BoundedFiles.readString(file, 1024));
    }

    @Test
    void rejectsAnInvalidBound() throws Exception {
        Path file = directory.resolve("any.yml");
        Files.writeString(file, "x");

        assertThrows(IllegalArgumentException.class, () -> BoundedFiles.readString(file, 0));
        assertThrows(IllegalArgumentException.class, () -> BoundedFiles.readString(null, 10));
    }
}
