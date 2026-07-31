package io.github.salyvn.omnipet.core.incubation;

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
import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;
import io.github.salyvn.omnipet.core.persistence.EggEscrowYamlCodec;

public final class FileEggEscrowJournal implements EggEscrowJournal {
    public static final long MAX_FILE_BYTES = 16 * 1024;
    public static final int MAX_SCAN_FILES = 10_000;
    private final Path root;
    private final EggEscrowYamlCodec codec = new EggEscrowYamlCodec();
    private final AtomicFileStore fileStore = new AtomicFileStore();

    public FileEggEscrowJournal(Path root) {
        if (root == null) throw new IllegalArgumentException("egg escrow root is required");
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Optional<EggEscrowTransaction> find(UUID transactionId) throws IOException {
        return EggEscrowLockRegistry.withLock(root, transactionId, () -> load(transactionId));
    }

    @Override
    public EggEscrowCreateResult create(EggEscrowTransaction transaction) throws IOException {
        requireTransaction(transaction);
        return EggEscrowLockRegistry.withLock(root, transaction.transactionId(), () -> {
            Optional<EggEscrowTransaction> existing = load(transaction.transactionId());
            if (existing.isPresent()) {
                requireSameIdentity(existing.orElseThrow(), transaction);
                return new EggEscrowCreateResult(
                        EggEscrowCreateResult.Status.ALREADY_EXISTS, existing.orElseThrow());
            }
            write(transaction);
            return new EggEscrowCreateResult(EggEscrowCreateResult.Status.CREATED, transaction);
        });
    }

    @Override
    public EggEscrowTransitionResult transition(
            UUID transactionId,
            Set<EggEscrowStage> expectedStages,
            EggEscrowStage targetStage) throws IOException {
        Set<EggEscrowStage> expected = requireTransition(expectedStages, targetStage);
        return EggEscrowLockRegistry.withLock(root, transactionId, () -> {
            EggEscrowTransaction current = load(transactionId)
                    .orElseThrow(() -> new IOException("egg escrow transaction does not exist: " + transactionId));
            if (current.stage() == targetStage) {
                return new EggEscrowTransitionResult(
                        EggEscrowTransitionResult.Status.ALREADY_AT_TARGET, current);
            }
            if (!expected.contains(current.stage())) {
                return new EggEscrowTransitionResult(EggEscrowTransitionResult.Status.REJECTED, current);
            }
            EggEscrowTransaction next = current.withStage(targetStage);
            write(next);
            return new EggEscrowTransitionResult(EggEscrowTransitionResult.Status.APPLIED, next);
        });
    }

    @Override
    public List<EggEscrowTransaction> findByPlayer(
            UUID playerId,
            Set<EggEscrowStage> stages,
            int limit) throws IOException {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        if (stages == null || stages.isEmpty()) throw new IllegalArgumentException("escrow stages are required");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("escrow scan limit is outside 1..100");
        rejectSymbolicLink(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        List<Path> files = new ArrayList<>();
        int scanned = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.yml")) {
            for (Path path : stream) {
                if (++scanned > MAX_SCAN_FILES) throw new IOException("egg escrow journal exceeds scan bound");
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) files.add(path);
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        List<EggEscrowTransaction> result = new ArrayList<>();
        for (Path file : files) {
            EggEscrowTransaction transaction = read(file);
            if (transaction.playerId().equals(playerId) && stages.contains(transaction.stage())) {
                result.add(transaction);
                if (result.size() == limit) break;
            }
        }
        return List.copyOf(result);
    }

    private Optional<EggEscrowTransaction> load(UUID transactionId) throws IOException {
        Path path = path(transactionId);
        rejectSymbolicLink(root);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.of(read(path));
    }

    private EggEscrowTransaction read(Path path) throws IOException {
        rejectSymbolicLink(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("egg escrow entry is not a regular file: " + path);
        }
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("egg escrow entry exceeds 16 KiB: " + path);
        try {
            EggEscrowTransaction transaction = codec.decode(Files.readString(path, StandardCharsets.UTF_8));
            String name = path.getFileName().toString();
            String canonical = transaction.transactionId() + ".yml";
            if (!name.equals(canonical)) {
                throw new IllegalArgumentException("egg escrow filename is not canonical");
            }
            return transaction;
        } catch (RuntimeException failure) {
            throw new IOException("invalid egg escrow entry: " + path, failure);
        }
    }

    private void write(EggEscrowTransaction transaction) throws IOException {
        rejectSymbolicLink(root);
        byte[] encoded = codec.encode(transaction);
        if (encoded.length > MAX_FILE_BYTES) {
            throw new IOException("egg escrow entry exceeds 16 KiB: " + transaction.transactionId());
        }
        fileStore.write(path(transaction.transactionId()), encoded);
    }

    private Path path(UUID transactionId) throws IOException {
        if (transactionId == null) throw new IllegalArgumentException("transaction id is required");
        Path path = root.resolve(transactionId + ".yml").normalize();
        if (!path.startsWith(root)) throw new IOException("egg escrow path escapes its root");
        return path;
    }

    private static void requireSameIdentity(
            EggEscrowTransaction existing,
            EggEscrowTransaction replacement) throws IOException {
        if (!existing.transactionId().equals(replacement.transactionId())
                || !existing.playerId().equals(replacement.playerId())
                || !existing.incubationId().equals(replacement.incubationId())
                || !existing.eggId().equals(replacement.eggId())
                || existing.expectedPlayerRevision() != replacement.expectedPlayerRevision()
                || !existing.item().equals(replacement.item())) {
            throw new IOException("egg escrow transaction identity cannot change");
        }
    }

    private static void requireTransaction(EggEscrowTransaction transaction) {
        if (transaction == null) throw new IllegalArgumentException("egg escrow transaction is required");
    }

    private static Set<EggEscrowStage> requireTransition(
            Set<EggEscrowStage> expectedStages,
            EggEscrowStage targetStage) {
        if (expectedStages == null || expectedStages.isEmpty()
                || expectedStages.stream().anyMatch(stage -> stage == null)) {
            throw new IllegalArgumentException("expected egg escrow stages are required");
        }
        if (targetStage == null) throw new IllegalArgumentException("target egg escrow stage is required");
        return Set.copyOf(expectedStages);
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IOException("symbolic link is not allowed: " + path);
        }
    }
}
