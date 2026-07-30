package io.github.salyvn.omnipet.core.persistence;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicFileStore {
    public void write(Path destination, byte[] content) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("destination has no parent: " + destination);
        rejectSymbolicLink(target);
        Files.createDirectories(parent);
        rejectSymbolicLink(parent);

        Path temporary = Files.createTempFile(parent, target.getFileName() + ".", ".tmp");
        Path backupTemporary = null;
        try {
            writeAndSync(temporary, content);
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                backupTemporary = Files.createTempFile(parent, target.getFileName() + ".bak.", ".tmp");
                Files.copy(target, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
                sync(backupTemporary);
                moveReplacing(backupTemporary, backupPath(target));
                backupTemporary = null;
            }
            moveReplacing(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
            if (backupTemporary != null) Files.deleteIfExists(backupTemporary);
        }
    }

    public Path quarantine(Path source, Path quarantineDirectory, String fileName) throws IOException {
        Path input = source.toAbsolutePath().normalize();
        Path directory = quarantineDirectory.toAbsolutePath().normalize();
        rejectSymbolicLink(input);
        rejectSymbolicLink(directory);
        Files.createDirectories(directory);
        Path destination = directory.resolve(fileName).normalize();
        if (!destination.startsWith(directory)) throw new IOException("quarantine target escapes directory");
        Files.move(input, destination);
        return destination;
    }

    public void moveWithoutReplacing(Path source, Path destination) throws IOException {
        Path input = source.toAbsolutePath().normalize();
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("destination has no parent: " + destination);
        rejectSymbolicLink(input);
        Files.createDirectories(parent);
        rejectSymbolicLink(parent);
        rejectSymbolicLink(target);
        try {
            Files.move(input, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(input, target);
        }
    }

    public static Path backupPath(Path destination) {
        return destination.resolveSibling(destination.getFileName() + ".bak");
    }

    private static void writeAndSync(Path path, byte[] content) throws IOException {
        try (FileOutputStream output = new FileOutputStream(path.toFile())) {
            output.write(content);
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

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IOException("symbolic link is not allowed: " + path);
        }
    }
}
