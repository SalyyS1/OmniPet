package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.StableId;

public final class SafeRepositoryPaths {
    private final Path root;

    public SafeRepositoryPaths(Path root) {
        if (root == null) throw new IllegalArgumentException("repository root is required");
        this.root = root.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(this.root)) throw new IllegalArgumentException("repository root cannot be a symbolic link");
    }

    public Path root() {
        return root;
    }

    public Path resolveId(String id, String suffix) throws IOException {
        String validId = StableId.requireValid(id);
        if (suffix == null || suffix.contains("/") || suffix.contains("\\")) {
            throw new IllegalArgumentException("invalid repository suffix");
        }
        return requireSafe(root.resolve(validId + suffix));
    }

    public Path resolveUuid(UUID id, String suffix) throws IOException {
        if (id == null) throw new IllegalArgumentException("UUID is required");
        return requireSafe(root.resolve(id + suffix));
    }

    public Path resolveQuarantine(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("invalid quarantine filename");
        }
        Path directory = root.resolve("quarantine");
        requireSafe(directory);
        return requireSafe(directory.resolve(fileName));
    }

    public Path resolveArchive(String id, String token) throws IOException {
        String validId = StableId.requireValid(id);
        if (token == null || token.isBlank() || token.contains("/") || token.contains("\\")) {
            throw new IllegalArgumentException("invalid archive token");
        }
        Path directory = root.resolve("archive");
        requireSafe(directory);
        return requireSafe(directory.resolve(validId + "-" + token + ".yml"));
    }

    public Path requireSafe(Path candidate) throws IOException {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IllegalArgumentException("repository target escapes its root: " + candidate);
        }
        ensureNoSymbolicLinks(normalized);
        return normalized;
    }

    public void validateCaseUnique(Collection<String> ids) {
        Set<String> folded = new HashSet<>();
        for (String id : ids) {
            String key = StableId.requireValid(id).toLowerCase(Locale.ROOT);
            if (!folded.add(key)) throw new IllegalArgumentException("case-folded duplicate stable ID: " + id);
        }
    }

    private void ensureNoSymbolicLinks(Path target) throws IOException {
        Path cursor = root;
        if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
            throw new IOException("symbolic link repository root is not allowed: " + cursor);
        }
        Path relative = root.relativize(target);
        for (Path part : relative) {
            cursor = cursor.resolve(part);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                throw new IOException("symbolic link repository target is not allowed: " + cursor);
            }
        }
    }
}
