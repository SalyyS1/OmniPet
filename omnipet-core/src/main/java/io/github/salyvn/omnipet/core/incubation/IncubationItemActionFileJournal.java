package io.github.salyvn.omnipet.core.incubation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;
import io.github.salyvn.omnipet.core.persistence.EggEscrowYamlCodec;

public final class IncubationItemActionFileJournal implements IncubationItemActionJournal {
    public static final long MAX_FILE_BYTES = 16 * 1024;
    private static final String SURROGATE_ID = "incubation_item_action";
    private static final int SCHEMA_VERSION = 1;

    private final Path root;
    private final EggEscrowYamlCodec codec = new EggEscrowYamlCodec();
    private final AtomicFileStore files = new AtomicFileStore();

    public IncubationItemActionFileJournal(Path root) {
        if (root == null) throw new IllegalArgumentException("incubation item action root is required");
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Optional<IncubationItemActionTransaction> find(UUID actionToken) throws IOException {
        return EggEscrowLockRegistry.withLock(root, actionToken, () -> load(actionToken));
    }

    @Override
    public IncubationItemActionTransaction create(IncubationItemActionTransaction transaction) throws IOException {
        if (transaction == null) throw new IllegalArgumentException("incubation item action is required");
        if (transaction.stage() != IncubationItemActionStage.PREPARED) {
            throw new IllegalArgumentException("new incubation item action must be PREPARED");
        }
        return EggEscrowLockRegistry.withLock(root, transaction.actionToken(), () -> {
            Optional<IncubationItemActionTransaction> existing = load(transaction.actionToken());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().sameIdentity(transaction)) {
                    throw new IOException("incubation item action token identity cannot change");
                }
                return existing.orElseThrow();
            }
            write(transaction);
            return transaction;
        });
    }

    @Override
    public IncubationItemActionTransaction transition(
            UUID actionToken,
            Set<IncubationItemActionStage> expected,
            IncubationItemActionStage target) throws IOException {
        if (actionToken == null || expected == null || expected.isEmpty() || target == null) {
            throw new IllegalArgumentException("action transition token, expected stages, and target are required");
        }
        return EggEscrowLockRegistry.withLock(root, actionToken, () -> {
            IncubationItemActionTransaction current = load(actionToken)
                    .orElseThrow(() -> new IOException("incubation item action does not exist: " + actionToken));
            if (current.stage() == target || !expected.contains(current.stage())) return current;
            IncubationItemActionTransaction next = current.withStage(target);
            write(next);
            return next;
        });
    }

    private Optional<IncubationItemActionTransaction> load(UUID actionToken) throws IOException {
        Path path = path(actionToken);
        rejectSymbolicLink(root);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        rejectSymbolicLink(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_FILE_BYTES) {
            throw new IOException("invalid incubation item action file: " + path);
        }
        try {
            EggEscrowTransaction surrogate = codec.decode(Files.readString(path, StandardCharsets.UTF_8));
            if (!surrogate.transactionId().equals(actionToken) || !SURROGATE_ID.equals(surrogate.eggId())) {
                throw new IllegalArgumentException("incubation item action file identity is invalid");
            }
            return Optional.of(fromSurrogate(surrogate));
        } catch (RuntimeException failure) {
            throw new IOException("invalid incubation item action file: " + path, failure);
        }
    }

    private void write(IncubationItemActionTransaction transaction) throws IOException {
        byte[] content = codec.encode(toSurrogate(transaction));
        if (content.length > MAX_FILE_BYTES) throw new IOException("incubation item action exceeds 16 KiB");
        rejectSymbolicLink(root);
        files.write(path(transaction.actionToken()), content);
    }

    private EggEscrowTransaction toSurrogate(IncubationItemActionTransaction transaction) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actionSchema", SCHEMA_VERSION);
        metadata.put("actionIncubationId", transaction.incubationId().toString());
        metadata.put("actionType", transaction.type().name());
        metadata.put("actionEffectMillis", transaction.effectMillis());
        metadata.put("actionStage", transaction.stage().name());
        return new EggEscrowTransaction(
                transaction.actionToken(),
                transaction.playerId(),
                transaction.actionToken(),
                SURROGATE_ID,
                transaction.expectedPlayerRevision(),
                transaction.item(),
                eggStage(transaction.stage()),
                metadata);
    }

    private static IncubationItemActionTransaction fromSurrogate(EggEscrowTransaction surrogate) {
        Map<String, Object> metadata = surrogate.extensions();
        int schema = ((Number) metadata.get("actionSchema")).intValue();
        if (schema != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported incubation item action schema");
        return new IncubationItemActionTransaction(
                surrogate.transactionId(),
                surrogate.playerId(),
                UUID.fromString(String.valueOf(metadata.get("actionIncubationId"))),
                surrogate.expectedPlayerRevision(),
                surrogate.item(),
                IncubationItemActionType.valueOf(String.valueOf(metadata.get("actionType"))),
                ((Number) metadata.get("actionEffectMillis")).longValue(),
                IncubationItemActionStage.valueOf(String.valueOf(metadata.get("actionStage"))));
    }

    private Path path(UUID token) throws IOException {
        if (token == null) throw new IllegalArgumentException("action token is required");
        Path path = root.resolve(token + ".yml").normalize();
        if (!path.startsWith(root)) throw new IOException("incubation item action path escapes root");
        return path;
    }

    private static EggEscrowStage eggStage(IncubationItemActionStage stage) {
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
