package io.github.salyvn.omnipet.persistence;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

public final class LegacyDataMigrator {
	private LegacyDataMigrator() {}

	public static boolean copyIfTargetEmpty(Path source, Path target) throws IOException {
		Path absoluteSource = source.toAbsolutePath().normalize();
		Path absoluteTarget = target.toAbsolutePath().normalize();
		if (!Files.isDirectory(absoluteSource) || absoluteSource.equals(absoluteTarget) || !isEmptyOrMissing(absoluteTarget)) return false;

		Path parent = absoluteTarget.getParent();
		if (parent == null) throw new IOException("Target has no parent: " + target);
		Files.createDirectories(parent);
		Path staging = parent.resolve(".%s-migration-%s.tmp".formatted(
				absoluteTarget.getFileName(), UUID.randomUUID().toString().substring(0, 8)));

		try {
			copyTree(absoluteSource, staging);
			if (!isEmptyOrMissing(absoluteTarget)) return false;
			if (Files.exists(absoluteTarget)) Files.delete(absoluteTarget);
			moveDirectory(staging, absoluteTarget);
			return true;
		} finally {
			deleteTreeIfExists(staging);
		}
	}

	private static boolean isEmptyOrMissing(Path directory) throws IOException {
		if (!Files.exists(directory)) return true;
		if (!Files.isDirectory(directory)) return false;
		try (Stream<Path> entries = Files.list(directory)) {
			return entries.findAny().isEmpty();
		}
	}

	private static void copyTree(Path source, Path destination) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
				Files.createDirectories(destination.resolve(source.relativize(directory)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void moveDirectory(Path source, Path destination) throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, destination);
		}
	}

	private static void deleteTreeIfExists(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}
}
