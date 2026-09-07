package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.UnaryOperator;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.PlayerStateEnvelope;

public final class FilePlayerStateRepository implements PlayerStateRepository {
    /**
     * The largest player file this repository will read into memory.
     *
     * <p>A constant rather than a constructor parameter: there are dozens of construction sites, and a
     * bound that every caller may set differently is a bound nobody can reason about. Four mebibytes is
     * orders of magnitude above a real player file and far below a heap problem.
     */
    private static final int MAX_PLAYER_STATE_BYTES = 4 * 1024 * 1024;

    private final SafeRepositoryPaths paths;
    private final PlayerStateYamlCodec codec;
    private final AtomicFileStore fileStore;

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
        try (var ignored = SharedRepositoryLockRegistry.acquire(paths.resolvePlayerLock(playerId))) {
            return load(playerId);
        }
    }

    @Override
    public PlayerState withLocked(UUID playerId, long expectedRevision, UnaryOperator<PlayerState> mutation) throws IOException {
        if (mutation == null) throw new IllegalArgumentException("mutation is required");
        try (var ignored = SharedRepositoryLockRegistry.acquire(paths.resolvePlayerLock(playerId))) {
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
        }
    }

    @Override
    public Set<String> referenceScan(String definitionId) throws IOException {
        String expected = io.github.salyvn.omnipet.core.domain.StableId.requireValid(definitionId);
        Set<String> references = new HashSet<>();
        if (!Files.isDirectory(paths.root(), LinkOption.NOFOLLOW_LINKS)) return Set.of();
        Path quarantineDirectory = paths.root().resolve("quarantine");
        if (Files.isDirectory(quarantineDirectory, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> quarantined = Files.newDirectoryStream(quarantineDirectory, "*.yml")) {
                for (Path ignored : quarantined) {
                    throw new IOException("quarantined player state prevents a safe definition reference scan");
                }
            }
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(paths.root(), "*.yml")) {
            for (Path file : files) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String fileName = file.getFileName().toString();
                String uuidText = fileName.substring(0, fileName.length() - 4);
                UUID playerId;
                try { playerId = UUID.fromString(uuidText); }
                catch (IllegalArgumentException error) { throw new IOException("invalid player state filename: " + fileName, error); }
                PlayerState state = snapshot(playerId);
                boolean found = state.pets().stream().anyMatch(pet -> expected.equals(pet.definitionId()))
                        || (state.incubation() != null
                                && expected.equals(state.incubation().outcome().definitionId()))
                        || containsString(state.legacyCurrentEgg(), expected)
                        || containsString(state.extensions(), expected);
                if (found) references.add("player:" + playerId);
            }
        }
        return Set.copyOf(references);
    }

    @Override
    public List<UUID> playerIds(int limit) throws IOException {
        if (limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("player scan limit must be 1..10000");
        }
        if (!Files.isDirectory(paths.root(), LinkOption.NOFOLLOW_LINKS)) return List.of();
        ArrayList<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.root(), "*.yml")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) files.add(file);
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        ArrayList<UUID> ids = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                ids.add(UUID.fromString(name.substring(0, name.length() - 4)));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("invalid player state filename: " + name, invalid);
            }
            if (ids.size() == limit) break;
        }
        return List.copyOf(ids);
    }

    private PlayerState load(UUID playerId) throws IOException {
        Path path = paths.resolveUuid(playerId, ".yml");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (hasQuarantinedState(playerId)) {
                throw new IOException("player state is quarantined; explicit recovery is required for " + playerId);
            }
            return PlayerState.empty(playerId);
        }
        String yaml = BoundedFiles.readString(path, MAX_PLAYER_STATE_BYTES);
        PlayerMigrationResult result;
        try {
            result = codec.decodeWithReport(yaml);
            PlayerState state = result.envelope().state();
            if (!state.playerId().equals(playerId)) {
                throw new IllegalArgumentException("embedded UUID does not match repository target");
            }
        } catch (RuntimeException failure) {
            // Quarantine is for a file whose bytes are not a valid player document. Every other fault —
            // a disk hiccup, an interrupted read, a bug in this code — is transient or ours, and moving a
            // player's data aside for one of those turns a momentary failure into a permanent lockout:
            // the next load finds the quarantine file and refuses to start the player at all. So anything
            // the codec did not classify as bad content is rethrown untouched.
            if (!isDecodeFailure(failure)) throw failure;
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

    /**
     * Whether this failure says the file's content is wrong, rather than that reading it went wrong.
     *
     * <p>Malformed YAML arrives as {@code YAMLException}; a document that parses but does not describe a
     * player arrives as {@link IllegalArgumentException}, including the embedded-UUID mismatch above. Both
     * mean the bytes on disk cannot become a player and will not become one on a retry, which is exactly
     * when setting the file aside is the kind thing to do. Anything else — including a bare
     * {@code NullPointerException} or {@code ClassCastException} out of the codec, which is a defect in
     * this code rather than a verdict about the file — is left to propagate.
     */
    private static boolean isDecodeFailure(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.yaml.snakeyaml.error.YAMLException) return true;
            if (cause instanceof IllegalArgumentException) return true;
            if (cause.getCause() == cause) break;
        }
        return false;
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

    private static boolean containsString(Object value, String expected) {
        if (expected.equals(value)) return true;
        if (value instanceof Map<?, ?> map) return map.values().stream().anyMatch(nested -> containsString(nested, expected));
        if (value instanceof List<?> list) return list.stream().anyMatch(nested -> containsString(nested, expected));
        return false;
    }
}
