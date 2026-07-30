package io.github.salyvn.omnipet.persistence;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;

public final class AtomicFileStore {
	private AtomicFileStore() {}

	public static void write(Path destination, byte[] content) throws IOException {
		write(destination, output -> output.write(content));
	}

	static void write(Path destination, OutputWriter writer) throws IOException {
		Path absoluteDestination = destination.toAbsolutePath();
		Path parent = absoluteDestination.getParent();
		if (parent == null) throw new IOException("Destination has no parent: " + destination);
		Files.createDirectories(parent);

		Path temporary = Files.createTempFile(parent, temporaryPrefix(absoluteDestination), ".tmp");
		Path backupTemporary = null;
		try {
			writeAndSync(temporary, writer);

			if (Files.isRegularFile(absoluteDestination)) {
				backupTemporary = Files.createTempFile(parent, temporaryPrefix(absoluteDestination) + "bak-", ".tmp");
				Files.copy(absoluteDestination, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
				sync(backupTemporary);
				moveReplacing(backupTemporary, backupPath(absoluteDestination));
				backupTemporary = null;
			}

			moveReplacing(temporary, absoluteDestination);
		} finally {
			Files.deleteIfExists(temporary);
			if (backupTemporary != null) Files.deleteIfExists(backupTemporary);
		}
	}

	public static Path quarantine(Path source, Path quarantineDirectory, String id) throws IOException {
		Files.createDirectories(quarantineDirectory);
		Path destination;
		do {
			destination = quarantineDirectory.resolve("%s-%d-%s.yml".formatted(
					id, System.currentTimeMillis(), UUID.randomUUID().toString().substring(0, 8)));
		} while (Files.exists(destination));

		moveWithoutReplacing(source.toAbsolutePath(), destination.toAbsolutePath());
		return destination;
	}

	public static boolean hasQuarantine(Path quarantineDirectory, String id) throws IOException {
		if (!Files.isDirectory(quarantineDirectory)) return false;
		String prefix = id + "-";
		try (Stream<Path> entries = Files.list(quarantineDirectory)) {
			return entries.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().startsWith(prefix));
		}
	}

	public static Path backupPath(Path destination) {
		return destination.resolveSibling(destination.getFileName() + ".bak");
	}

	private static void writeAndSync(Path path, OutputWriter writer) throws IOException {
		try (FileOutputStream output = new FileOutputStream(path.toFile())) {
			writer.write(output);
			output.flush();
			output.getFD().sync();
		}
	}

	private static void sync(Path path) throws IOException {
		try (FileOutputStream output = new FileOutputStream(path.toFile(), true)) {
			output.getFD().sync();
		}
	}

	private static void moveReplacing(Path source, Path destination) throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void moveWithoutReplacing(Path source, Path destination) throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, destination);
		}
	}

	private static String temporaryPrefix(Path destination) {
		String prefix = destination.getFileName() + ".";
		return prefix.length() >= 3 ? prefix : "save-";
	}

	@FunctionalInterface
	interface OutputWriter {
		void write(OutputStream output) throws IOException;
	}
}
