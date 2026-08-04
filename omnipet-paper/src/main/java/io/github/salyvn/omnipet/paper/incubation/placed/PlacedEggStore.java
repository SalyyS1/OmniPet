package io.github.salyvn.omnipet.paper.incubation.placed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;
import io.github.salyvn.omnipet.core.persistence.YamlDocuments;

/**
 * Durable storage for eggs placed in the world, one file per block.
 *
 * <p>Mirrors the conventions the other OmniPet journals already use: atomic writes with a {@code .bak}
 * snapshot, a per-file size cap, and a bounded directory scan that reports rather than silently
 * truncating. A placed egg is an item the player still owns, so losing one of these files loses an item.
 *
 * <p>One file per block rather than one per player, because the block position is what a break or a chunk
 * load has in hand, and it is what makes "one egg per block" enforceable by the filesystem.
 */
public final class PlacedEggStore {
    public static final long MAX_FILE_BYTES = 16 * 1024;
    public static final int MAX_SCAN_FILES = 10_000;
    private static final int SCHEMA_VERSION = 1;

    private final Path root;
    private final AtomicFileStore fileStore = new AtomicFileStore();

    public PlacedEggStore(Path root) {
        this.root = Objects.requireNonNull(root, "placed egg root").toAbsolutePath().normalize();
    }

    public Optional<PlacedEggRecord> read(String key) throws IOException {
        Path file = file(key);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("placed egg record is not a regular file: " + file);
        }
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("placed egg record exceeds 16 KiB: " + key);
        }
        return Optional.of(decode(YamlDocuments.readMap(Files.readString(file, StandardCharsets.UTF_8))));
    }

    public void save(PlacedEggRecord record) throws IOException {
        Objects.requireNonNull(record, "placed egg record");
        byte[] content = YamlDocuments.writeMap(encode(record)).getBytes(StandardCharsets.UTF_8);
        // Checked before writing so an oversized record never lands in a state read() would refuse.
        if (content.length > MAX_FILE_BYTES) {
            throw new IOException("placed egg record exceeds 16 KiB: " + record.key());
        }
        Files.createDirectories(root);
        fileStore.write(file(record.key()), content);
    }

    /** Removes the record, returning whether one existed. Deleting is what consumes a placed egg. */
    public boolean delete(String key) throws IOException {
        return Files.deleteIfExists(file(key));
    }

    /**
     * Every placed egg on disk.
     *
     * <p>Unreadable records are reported rather than skipped silently: each one is a player's item, and an
     * operator needs to know a file could not be loaded.
     */
    public Scan scanAll() throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return new Scan(List.of(), List.of(), false);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("placed egg root is not a directory: " + root);
        }
        List<PlacedEggRecord> records = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        boolean truncated = false;
        int scanned = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root, "*.yml")) {
            for (Path file : files) {
                if (++scanned > MAX_SCAN_FILES) {
                    truncated = true;
                    break;
                }
                String name = file.getFileName().toString();
                try {
                    read(name.substring(0, name.length() - 4)).ifPresent(records::add);
                } catch (IOException | RuntimeException failure) {
                    unreadable.add(name + ": " + failure.getMessage());
                }
            }
        }
        return new Scan(List.copyOf(records), List.copyOf(unreadable), truncated);
    }

    private Path file(String key) throws IOException {
        String safe = Objects.requireNonNull(key, "record key");
        // The key is composed from a world name and three integers, so anything that could escape the
        // directory means the caller built it wrong rather than the operator typing it.
        if (safe.isBlank() || safe.contains("/") || safe.contains("\\") || safe.contains("..")) {
            throw new IOException("unsafe placed egg key: " + key);
        }
        Path file = root.resolve(safe + ".yml").toAbsolutePath().normalize();
        if (!file.startsWith(root)) throw new IOException("placed egg key escapes its directory: " + key);
        return file;
    }

    private static Map<String, Object> encode(PlacedEggRecord record) {
        LinkedHashMap<String, Object> node = new LinkedHashMap<>();
        node.put("schemaVersion", SCHEMA_VERSION);
        node.put("eggId", record.eggId());
        node.put("itemNonce", record.itemNonce().toString());
        node.put("ownerId", record.ownerId().toString());
        node.put("world", record.world());
        node.put("x", record.blockX());
        node.put("y", record.blockY());
        node.put("z", record.blockZ());
        node.put("remainingMillis", record.remainingMillis());
        node.put("totalMillis", record.totalMillis());
        node.put("itemSnapshot", record.itemSnapshot());
        if (!record.extensions().isEmpty()) node.put("extensions", record.extensions());
        return node;
    }

    private static PlacedEggRecord decode(Map<String, Object> node) {
        int schema = integer(node.get("schemaVersion"), "schemaVersion");
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported placed egg schema: " + schema);
        }
        return new PlacedEggRecord(
                text(node.get("eggId"), "eggId"),
                uuid(node.get("itemNonce"), "itemNonce"),
                uuid(node.get("ownerId"), "ownerId"),
                text(node.get("world"), "world"),
                integer(node.get("x"), "x"),
                integer(node.get("y"), "y"),
                integer(node.get("z"), "z"),
                longValue(node.get("remainingMillis"), "remainingMillis"),
                longValue(node.get("totalMillis"), "totalMillis"),
                text(node.get("itemSnapshot"), "itemSnapshot"),
                node.get("extensions") instanceof Map<?, ?> map ? copy(map) : Map.of());
    }

    private static Map<String, Object> copy(Map<?, ?> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return Map.copyOf(result);
    }

    private static String text(Object value, String field) {
        if (!(value instanceof String result) || result.isBlank()) {
            throw new IllegalArgumentException("placed egg " + field + " must be text");
        }
        return result;
    }

    private static UUID uuid(Object value, String field) {
        try {
            return UUID.fromString(text(value, field));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("placed egg " + field + " is not a UUID", invalid);
        }
    }

    private static int integer(Object value, String field) {
        return Math.toIntExact(longValue(value, field));
    }

    private static long longValue(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("placed egg " + field + " must be a number");
        }
        return number.longValue();
    }

    /** Everything one scan found, including what it could not read. */
    public record Scan(List<PlacedEggRecord> records, List<String> unreadable, boolean truncated) {
        public Scan {
            records = List.copyOf(records);
            unreadable = List.copyOf(unreadable);
        }
    }
}
