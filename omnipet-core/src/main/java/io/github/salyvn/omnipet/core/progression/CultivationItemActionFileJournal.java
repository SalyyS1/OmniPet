package io.github.salyvn.omnipet.core.progression;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.incubation.EggEscrowStage;
import io.github.salyvn.omnipet.core.incubation.EggEscrowTransaction;
import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;
import io.github.salyvn.omnipet.core.persistence.EggEscrowYamlCodec;

public final class CultivationItemActionFileJournal implements CultivationItemActionJournal {
    public static final long MAX_FILE_BYTES = 16 * 1024;
    private static final String SURROGATE_ID = "cultivation_item_action";
    private static final int SCHEMA_VERSION = 1;

    private final Path root;
    private final EggEscrowYamlCodec codec = new EggEscrowYamlCodec();
    private final AtomicFileStore files = new AtomicFileStore();

    public CultivationItemActionFileJournal(Path root) {
        if (root == null) throw new IllegalArgumentException("cultivation action root is required");
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public synchronized Optional<CultivationItemActionTransaction> find(UUID actionToken) throws IOException {
        return load(actionToken);
    }

    @Override
    public synchronized CultivationItemActionTransaction create(CultivationItemActionTransaction transaction)
            throws IOException {
        if (transaction == null || transaction.stage() != CultivationItemActionStage.PREPARED) {
            throw new IllegalArgumentException("new cultivation action must be PREPARED");
        }
        Optional<CultivationItemActionTransaction> existing = load(transaction.actionToken());
        if (existing.isPresent()) {
            if (!existing.orElseThrow().sameIdentity(transaction)) {
                throw new IOException("cultivation item nonce identity cannot change");
            }
            return existing.orElseThrow();
        }
        write(transaction);
        return transaction;
    }

    @Override
    public synchronized CultivationItemActionTransaction transition(
            UUID actionToken,
            Set<CultivationItemActionStage> expected,
            CultivationItemActionStage target) throws IOException {
        if (actionToken == null || expected == null || expected.isEmpty() || target == null) {
            throw new IllegalArgumentException("cultivation transition fields are required");
        }
        CultivationItemActionTransaction current = load(actionToken)
                .orElseThrow(() -> new IOException("cultivation action does not exist: " + actionToken));
        if (current.stage() == target || !expected.contains(current.stage())) return current;
        CultivationItemActionTransaction next = current.withStage(target);
        write(next);
        return next;
    }

    @Override
    public synchronized List<CultivationItemActionTransaction> scan(
            UUID playerId,
            Set<CultivationItemActionStage> stages,
            int limit) throws IOException {
        if (stages == null || stages.isEmpty() || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("cultivation scan requires stages and a 1-100 limit");
        }
        rejectSymbolicLink(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("cultivation action root is not a directory: " + root);
        }
        ArrayList<Path> paths = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(paths::add);
        }
        ArrayList<CultivationItemActionTransaction> matches = new ArrayList<>();
        for (Path candidate : paths) {
            String file = candidate.getFileName().toString();
            UUID token;
            try {
                token = UUID.fromString(file.substring(0, file.length() - 4));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("invalid cultivation action filename: " + candidate, invalid);
            }
            CultivationItemActionTransaction transaction = load(token).orElseThrow();
            if ((playerId == null || playerId.equals(transaction.playerId()))
                    && stages.contains(transaction.stage())) {
                matches.add(transaction);
                if (matches.size() == limit) break;
            }
        }
        return List.copyOf(matches);
    }

    private Optional<CultivationItemActionTransaction> load(UUID actionToken) throws IOException {
        Path path = path(actionToken);
        rejectSymbolicLink(root);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        rejectSymbolicLink(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_FILE_BYTES) {
            throw new IOException("invalid cultivation action file: " + path);
        }
        try {
            EggEscrowTransaction surrogate = codec.decode(Files.readString(path, StandardCharsets.UTF_8));
            if (!surrogate.transactionId().equals(actionToken) || !SURROGATE_ID.equals(surrogate.eggId())) {
                throw new IllegalArgumentException("cultivation action identity is invalid");
            }
            return Optional.of(fromSurrogate(surrogate));
        } catch (RuntimeException failure) {
            throw new IOException("invalid cultivation action file: " + path, failure);
        }
    }

    private void write(CultivationItemActionTransaction transaction) throws IOException {
        byte[] content = codec.encode(toSurrogate(transaction));
        if (content.length > MAX_FILE_BYTES) throw new IOException("cultivation action exceeds 16 KiB");
        rejectSymbolicLink(root);
        files.write(path(transaction.actionToken()), content);
    }

    private static EggEscrowTransaction toSurrogate(CultivationItemActionTransaction transaction) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cultivationSchema", SCHEMA_VERSION);
        metadata.put("cultivationPetId", transaction.petId().toString());
        metadata.put("cultivationKind", transaction.kind().name());
        metadata.put("cultivationExperience", transaction.experienceAmount());
        metadata.put("cultivationRequiredLevel", transaction.requiredLevel());
        metadata.put("cultivationRequiredEvolution", transaction.requiredEvolution());
        metadata.put("cultivationStage", transaction.stage().name());
        return new EggEscrowTransaction(
                transaction.actionToken(), transaction.playerId(), transaction.actionToken(), SURROGATE_ID,
                transaction.expectedPlayerRevision(), transaction.item(), eggStage(transaction.stage()), metadata);
    }

    private static CultivationItemActionTransaction fromSurrogate(EggEscrowTransaction surrogate) {
        Map<String, Object> metadata = surrogate.extensions();
        if (((Number) metadata.get("cultivationSchema")).intValue() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported cultivation action schema");
        }
        return new CultivationItemActionTransaction(
                surrogate.transactionId(), surrogate.playerId(),
                UUID.fromString(String.valueOf(metadata.get("cultivationPetId"))),
                surrogate.expectedPlayerRevision(), surrogate.item(),
                CultivationItemActionKind.valueOf(String.valueOf(metadata.get("cultivationKind"))),
                ((Number) metadata.get("cultivationExperience")).doubleValue(),
                ((Number) metadata.get("cultivationRequiredLevel")).intValue(),
                ((Number) metadata.get("cultivationRequiredEvolution")).intValue(),
                CultivationItemActionStage.valueOf(String.valueOf(metadata.get("cultivationStage"))));
    }

    private Path path(UUID token) throws IOException {
        if (token == null) throw new IllegalArgumentException("cultivation action token is required");
        Path path = root.resolve(token + ".yml").normalize();
        if (!path.startsWith(root)) throw new IOException("cultivation action path escapes root");
        return path;
    }

    private static EggEscrowStage eggStage(CultivationItemActionStage stage) {
        return switch (stage) {
            case PREPARED -> EggEscrowStage.PREPARED;
            case ITEM_REMOVED -> EggEscrowStage.ITEM_REMOVED;
            case COMMITTED -> EggEscrowStage.COMMITTED;
            case REFUND_PENDING -> EggEscrowStage.REFUND_PENDING;
            case REFUNDED -> EggEscrowStage.REFUNDED;
            case OPERATOR_REVIEW -> EggEscrowStage.FAILED;
        };
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IOException("symbolic link is not allowed: " + path);
        }
    }
}
