package io.github.salyvn.omnipet.core.incubation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

final class EggEscrowLockRegistry {
    private static final ConcurrentHashMap<Key, Entry> LOCKS = new ConcurrentHashMap<>();

    private EggEscrowLockRegistry() {}

    static <T> T withLock(Path root, UUID transactionId, IoOperation<T> operation) throws IOException {
        if (root == null) throw new IllegalArgumentException("egg escrow root is required");
        if (transactionId == null) throw new IllegalArgumentException("transaction id is required");
        if (operation == null) throw new IllegalArgumentException("egg escrow operation is required");
        Key key = new Key(root.toAbsolutePath().normalize(), transactionId);
        Entry entry = LOCKS.compute(key, (ignored, current) -> {
            Entry result = current == null ? new Entry() : current;
            result.references++;
            return result;
        });
        entry.lock.lock();
        try {
            return operation.run();
        } finally {
            entry.lock.unlock();
            LOCKS.computeIfPresent(key, (ignored, current) -> {
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
    }

    @FunctionalInterface
    interface IoOperation<T> {
        T run() throws IOException;
    }

    private record Key(Path root, UUID transactionId) {}

    private static final class Entry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
