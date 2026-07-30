package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.PlayerStateEnvelope;

public final class FilePlayerStateRepository implements PlayerStateRepository {
    private final SafeRepositoryPaths paths;
    private final PlayerStateYamlCodec codec;
    private final AtomicFileStore fileStore;
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public FilePlayerStateRepository(Path root) {
        this(root, new PlayerStateYamlCodec(), new AtomicFileStore());
    }

    FilePlayerStateRepository(Path root, PlayerStateYamlCodec codec, AtomicFileStore fileStore) {
        this.paths = new SafeRepositoryPaths(root);
        this.codec = codec;
        this.fileStore = fileStore;
    }

    @Override
    public PlayerState snapshot(UUID playerId) throws IOException {
        ReentrantLock lock = locks.computeIfAbsent(playerId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return load(playerId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PlayerState withLocked(UUID playerId, long expectedRevision, UnaryOperator<PlayerState> mutation) throws IOException {
        if (mutation == null) throw new IllegalArgumentException("mutation is required");
        ReentrantLock lock = locks.computeIfAbsent(playerId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            PlayerState current = load(playerId);
            if (current.revision() != expectedRevision) throw new StaleRevisionException(expectedRevision, current.revision());
            PlayerState candidate = mutation.apply(current);
            if (candidate == null) throw new IllegalArgumentException("mutation returned null");
            if (!candidate.playerId().equals(playerId)) throw new IllegalArgumentException("mutation changed player UUID");
            if (candidate.revision() != current.revision()) throw new IllegalArgumentException("mutation changed revision directly");
            PlayerState saved = candidate.withRevision(current.revision() + 1);
            Path path = paths.resolveUuid(playerId, ".yml");
            fileStore.write(path, codec.encodeBytes(new PlayerStateEnvelope(PlayerStateEnvelope.CURRENT_SCHEMA_VERSION, saved)));
            return saved;
        } finally {
            lock.unlock();
        }
    }

    private PlayerState load(UUID playerId) throws IOException {
        Path path = paths.resolveUuid(playerId, ".yml");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (hasQuarantinedState(playerId)) {
                throw new IOException("player state is quarantined; explicit recovery is required for " + playerId);
            }
            return new PlayerState(playerId, 0, List.of(), null, Map.of(), null, Map.of());
        }
        String yaml = Files.readString(path, StandardCharsets.UTF_8);
        PlayerMigrationResult result;
        try {
            result = codec.decodeWithReport(yaml);
            PlayerState state = result.envelope().state();
            if (!state.playerId().equals(playerId)) {
                throw new IllegalArgumentException("embedded UUID does not match repository target");
            }
        } catch (RuntimeException failure) {
            String quarantineName = playerId + "-" + UUID.randomUUID() + ".yml";
            Path quarantine = paths.resolveQuarantine(quarantineName);
            fileStore.quarantine(path, quarantine.getParent(), quarantine.getFileName().toString());
            throw new IOException("invalid player state quarantined at " + quarantine, failure);
        }
        PlayerState state = result.envelope().state();
        if (result.report().migrated()) {
            fileStore.write(path, codec.encodeBytes(result.envelope()));
        }
        return state;
    }

    private boolean hasQuarantinedState(UUID playerId) throws IOException {
        Path quarantineDirectory = paths.resolveQuarantine(playerId + "-probe.yml").getParent();
        if (!Files.isDirectory(quarantineDirectory, LinkOption.NOFOLLOW_LINKS)) return false;
        String prefix = playerId + "-";
        try (DirectoryStream<Path> files = Files.newDirectoryStream(quarantineDirectory)) {
            for (Path candidate : files) {
                String name = candidate.getFileName().toString();
                if (name.startsWith(prefix)
                        && name.endsWith(".yml")
                        && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    return true;
                }
            }
        }
        return false;
    }
}
