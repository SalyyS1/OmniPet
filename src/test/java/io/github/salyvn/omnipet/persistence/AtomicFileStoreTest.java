package io.github.salyvn.omnipet.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileStoreTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void replacesThroughTemporaryFileAndKeepsPreviousVersionAsBackup() throws IOException {
		Path destination = temporaryDirectory.resolve("nested/player.yml");
		AtomicFileStore.write(destination, "first".getBytes(StandardCharsets.UTF_8));
		AtomicFileStore.write(destination, "second".getBytes(StandardCharsets.UTF_8));

		assertEquals("second", Files.readString(destination));
		assertEquals("first", Files.readString(AtomicFileStore.backupPath(destination)));
		try (var files = Files.list(destination.getParent())) {
			assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
		}
	}

	@Test
	void leavesExistingFileUntouchedWhenTemporaryWriteFails() throws IOException {
		Path destination = temporaryDirectory.resolve("player.yml");
		byte[] original = "recoverable".getBytes(StandardCharsets.UTF_8);
		Files.write(destination, original);

		assertThrows(IOException.class, () -> AtomicFileStore.write(destination, output -> {
			output.write("partial".getBytes(StandardCharsets.UTF_8));
			throw new IOException("simulated write failure");
		}));

		assertArrayEquals(original, Files.readAllBytes(destination));
		assertFalse(Files.exists(AtomicFileStore.backupPath(destination)));
	}

	@Test
	void movesCorruptFileIntoQuarantineAndDetectsIt() throws IOException {
		Path source = temporaryDirectory.resolve("player.yml");
		Path quarantine = temporaryDirectory.resolve("quarantine");
		Files.writeString(source, "invalid: [");

		Path quarantined = AtomicFileStore.quarantine(source, quarantine, "player-id");

		assertFalse(Files.exists(source));
		assertTrue(Files.isRegularFile(quarantined));
		assertTrue(AtomicFileStore.hasQuarantine(quarantine, "player-id"));
		assertEquals("invalid: [", Files.readString(quarantined));
	}
}
