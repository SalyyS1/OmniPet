package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileStoreTest {
    @TempDir
    Path temporary;

    @Test
    void replacesAtomicallyAndKeepsPreviousBackup() throws Exception {
        AtomicFileStore store = new AtomicFileStore();
        Path file = temporary.resolve("players").resolve("player.yml");
        store.write(file, "first".getBytes(StandardCharsets.UTF_8));
        store.write(file, "second".getBytes(StandardCharsets.UTF_8));

        assertEquals("second", Files.readString(file));
        assertEquals("first", Files.readString(AtomicFileStore.backupPath(file)));
    }

    @Test
    void quarantineFilenameCannotEscapeDirectory() throws Exception {
        AtomicFileStore store = new AtomicFileStore();
        Path source = temporary.resolve("source.yml");
        Files.writeString(source, "data");

        assertThrows(IOException.class, () -> store.quarantine(source, temporary.resolve("quarantine"), "../escaped.yml"));
    }
}
