package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-key locks that disappear once nobody is holding them.
 *
 * <p>The obvious way to lock per transaction is a {@code ConcurrentHashMap<UUID, ReentrantLock>} and a
 * {@code computeIfAbsent}. It works, and it never forgets: on a busy server every transaction that has
 * ever run leaves a lock behind, so the map grows for as long as the process lives. Nothing here is
 * large, but nothing here is ever released either, and "small leak, unbounded duration" is how a server
 * that has been up for a month ends up different from one that just started.
 *
 * <p>So each entry counts its holders and is removed when the count reaches zero. That is the whole
 * idea. The reference count is guarded by the map's own per-bucket lock rather than by the entry lock,
 * because a thread has to be counted as a holder before it starts waiting, or an entry could be evicted
 * out from under a waiter and two threads would end up holding different locks for the same key.
 *
 * @param <K> the key locks are keyed by; must be a sound hash key
 */
public final class KeyedLockRegistry<K> {
    private final ConcurrentHashMap<K, Entry> entries = new ConcurrentHashMap<>();

    /** Runs {@code operation} while holding this key's lock, releasing and evicting it afterwards. */
    public <T> T withLock(K key, IoOperation<T> operation) throws IOException {
        Objects.requireNonNull(key, "lock key");
        Objects.requireNonNull(operation, "locked operation");
        Entry entry = entries.compute(key, (ignored, current) -> {
            Entry selected = current == null ? new Entry() : current;
            selected.references++;
            return selected;
        });
        entry.lock.lock();
        try {
            return operation.run();
        } finally {
            entry.lock.unlock();
            entries.computeIfPresent(key, (ignored, current) -> {
                if (current != entry) return current;
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
    }

    /** How many keys currently hold a lock entry. Tests assert this returns to zero. */
    public int trackedKeys() {
        return entries.size();
    }

    @FunctionalInterface
    public interface IoOperation<T> {
        T run() throws IOException;
    }

    private static final class Entry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
