package io.github.salyvn.omnipet.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyDataMigratorTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void copiesLegacyTreeIntoMissingTargetAndRetainsSource() throws IOException {
		Path source = temporaryDirectory.resolve("legacy");
		Path target = temporaryDirectory.resolve("current");
		Files.createDirectories(source.resolve("data/players"));
		Files.writeString(source.resolve("config.yml"), "slots: 10");
		Files.writeString(source.resolve("data/players/player.yml"), "uuid: player");

		assertTrue(LegacyDataMigrator.copyIfTargetEmpty(source, target));
		assertEquals("slots: 10", Files.readString(target.resolve("config.yml")));
		assertEquals("uuid: player", Files.readString(target.resolve("data/players/player.yml")));
		assertEquals("slots: 10", Files.readString(source.resolve("config.yml")));
	}

	@Test
	void copiesIntoExistingEmptyTarget() throws IOException {
		Path source = temporaryDirectory.resolve("legacy");
		Path target = temporaryDirectory.resolve("current");
		Files.createDirectories(source);
		Files.createDirectories(target);
		Files.writeString(source.resolve("config.yml"), "legacy");

		assertTrue(LegacyDataMigrator.copyIfTargetEmpty(source, target));
		assertEquals("legacy", Files.readString(target.resolve("config.yml")));
	}

	@Test
	void refusesToOverwriteNonEmptyTarget() throws IOException {
		Path source = temporaryDirectory.resolve("legacy");
		Path target = temporaryDirectory.resolve("current");
		Files.createDirectories(source);
		Files.createDirectories(target);
		Files.writeString(source.resolve("config.yml"), "legacy");
		Files.writeString(target.resolve("config.yml"), "current");

		assertFalse(LegacyDataMigrator.copyIfTargetEmpty(source, target));
		assertEquals("current", Files.readString(target.resolve("config.yml")));
		assertEquals("legacy", Files.readString(source.resolve("config.yml")));
	}
}
