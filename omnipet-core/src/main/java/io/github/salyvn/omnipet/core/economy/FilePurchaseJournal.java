package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;

/** File-per-transaction journal with atomic replacement and restart-safe records. */
public final class FilePurchaseJournal implements PurchaseJournal {
    private final Path root;
    private final PurchaseJournalYamlCodec codec;
    private final AtomicFileStore fileStore;
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public FilePurchaseJournal(Path root) {
        this(root, new PurchaseJournalYamlCodec(), new AtomicFileStore());
    }

    FilePurchaseJournal(Path root, PurchaseJournalYamlCodec codec, AtomicFileStore fileStore) {
        if (root == null) throw new IllegalArgumentException("purchase journal root is required");
        this.root = root.toAbsolutePath().normalize();
        this.codec = codec;
        this.fileStore = fileStore;
    }

    @Override
    public Optional<SlotPurchaseTransaction> find(UUID transactionId) throws IOException {
        ReentrantLock lock = lock(transactionId);
        lock.lock();
        try {
            return load(transactionId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SlotPurchaseTransaction create(SlotPurchaseTransaction transaction) throws IOException {
        if (transaction == null) throw new IllegalArgumentException("purchase transaction is required");
        ReentrantLock lock = lock(transaction.transactionId());
        lock.lock();
        try {
            Optional<SlotPurchaseTransaction> existing = load(transaction.transactionId());
            if (existing.isPresent()) return existing.get();
            write(transaction);
            return transaction;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void save(SlotPurchaseTransaction transaction) throws IOException {
        if (transaction == null) throw new IllegalArgumentException("purchase transaction is required");
        ReentrantLock lock = lock(transaction.transactionId());
        lock.lock();
        try {
            SlotPurchaseTransaction existing = load(transaction.transactionId())
                    .orElseThrow(() -> new IOException("purchase transaction does not exist: " + transaction.transactionId()));
            requireSameIdentity(existing, transaction);
            write(transaction);
        } finally {
            lock.unlock();
        }
    }

    private Optional<SlotPurchaseTransaction> load(UUID transactionId) throws IOException {
        Path path = path(transactionId);
        rejectSymbolicLink(root);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        rejectSymbolicLink(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("purchase journal entry is not a regular file: " + path);
        }
        SlotPurchaseTransaction transaction;
        try {
            transaction = codec.decode(Files.readString(path, StandardCharsets.UTF_8));
        } catch (RuntimeException failure) {
            throw new IOException("invalid purchase journal entry: " + path, failure);
        }
        if (!transaction.transactionId().equals(transactionId)) {
            throw new IOException("purchase journal transaction ID does not match its filename");
        }
        return Optional.of(transaction);
    }

    private void write(SlotPurchaseTransaction transaction) throws IOException {
        rejectSymbolicLink(root);
        fileStore.write(path(transaction.transactionId()), codec.encode(transaction));
    }

    private Path path(UUID transactionId) throws IOException {
        if (transactionId == null) throw new IllegalArgumentException("transaction id is required");
        Path path = root.resolve(transactionId + ".yml").normalize();
        if (!path.startsWith(root)) throw new IOException("purchase journal path escapes its root");
        return path;
    }

    private ReentrantLock lock(UUID transactionId) {
        if (transactionId == null) throw new IllegalArgumentException("transaction id is required");
        return locks.computeIfAbsent(transactionId, ignored -> new ReentrantLock());
    }

    private static void requireSameIdentity(
            SlotPurchaseTransaction existing,
            SlotPurchaseTransaction replacement) throws IOException {
        if (!existing.transactionId().equals(replacement.transactionId())
                || !existing.playerId().equals(replacement.playerId())
                || existing.expectedRevision() != replacement.expectedRevision()
                || existing.slot() != replacement.slot()
                || !existing.amount().equals(replacement.amount())) {
            throw new IOException("purchase transaction identity cannot change");
        }
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IOException("symbolic link is not allowed: " + path);
        }
    }
}
