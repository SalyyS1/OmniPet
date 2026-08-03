package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import io.github.salyvn.omnipet.core.domain.StableId;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinitionEnvelope;

public final class YamlEggDefinitionRepository implements EggDefinitionRepository {
    public static final long MAX_FILE_BYTES = 64 * 1024;
    public static final int MAX_LIST_FILES = 10_000;
    private final SafeRepositoryPaths paths;
    private final EggDefinitionYamlCodec codec;
    private final AtomicFileStore fileStore = new AtomicFileStore();

    public YamlEggDefinitionRepository(Path root) {
        paths = new SafeRepositoryPaths(root);
        codec = new EggDefinitionYamlCodec();
    }

    @Override
    public Optional<EggDefinitionEnvelope> read(String id) throws IOException {
        Path path = paths.resolveId(id, ".yml");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("egg definition is not a regular file: " + path);
        }
        long size = Files.size(path);
        if (size > MAX_FILE_BYTES) throw new IOException("egg definition exceeds 64 KiB: " + id);
        return Optional.of(codec.decode(id, Files.readString(path, StandardCharsets.UTF_8)));
    }

    @Override
    public void save(EggDefinitionEnvelope envelope) throws IOException {
        if (envelope == null) throw new IllegalArgumentException("egg definition envelope is required");
        String id = envelope.definition().id();
        Path path = paths.resolveId(id, ".yml");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("egg definition is not a regular file: " + path);
        }
        byte[] content = codec.encode(envelope).getBytes(StandardCharsets.UTF_8);
        // Checked before writing, so an oversized definition never lands on disk in a state that
        // read() would then refuse to load.
        if (content.length > MAX_FILE_BYTES) throw new IOException("egg definition exceeds 64 KiB: " + id);
        Files.createDirectories(paths.root());
        fileStore.write(path, content);
    }

    @Override
    public List<String> list() throws IOException {
        if (!Files.exists(paths.root(), LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isDirectory(paths.root(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("egg definition root is not a directory");
        }
        List<String> ids = new ArrayList<>();
        int scanned = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(paths.root(), "*.yml")) {
            for (Path file : files) {
                if (++scanned > MAX_LIST_FILES) throw new IOException("egg definition catalog exceeds scan bound");
                paths.requireSafe(file);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String name = file.getFileName().toString();
                ids.add(StableId.requireValid(name.substring(0, name.length() - 4)));
            }
        }
        paths.validateCaseUnique(ids);
        ids.sort(Comparator.comparing(StableId::folded));
        return List.copyOf(ids);
    }
}
