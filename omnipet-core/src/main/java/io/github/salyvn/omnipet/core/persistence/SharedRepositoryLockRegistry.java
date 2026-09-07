package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Combines classloader-local coordination with a process-wide player file lock.
 *
 * <p>The local lock is deliberately NOT reentrant: a same-thread nested acquire used to sail through
 * the JVM lock and then spin forever on the file lock, because a JVM file lock cannot be re-acquired
 * by a second channel in the same process. Nesting now fails fast, which turns the latent deadlock
 * into a bug that says its own name.
 */
final class SharedRepositoryLockRegistry {
    private static final ConcurrentHashMap<Path, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final long RETRY_NANOS = 1_000_000L;
    private static final long FILE_LOCK_WAIT_NANOS = 30_000_000_000L;

    private SharedRepositoryLockRegistry() {}

    static Lease acquire(Path lockFile) throws IOException {
        if (lockFile == null) throw new IllegalArgumentException("lock file is required");
        Path key = lockFile.toAbsolutePath().normalize();
        Entry entry = ENTRIES.compute(key, (ignored, current) -> {
            Entry selected = current == null ? new Entry() : current;
            selected.references++;
            return selected;
        });
        if (entry.lock.isHeldByCurrentThread()) {
            dereference(key, entry);
            throw new IllegalStateException(
                    "player lock already held by this thread; nested acquire is not supported: " + key);
        }
        entry.lock.lock();
        entry.owner = Thread.currentThread();
        FileChannel channel = null;
        try {
            channel = FileChannel.open(
                    key,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
            FileLock fileLock = acquireFileLock(channel, key);
            return new Lease(key, entry, channel, fileLock);
        } catch (IOException | RuntimeException failure) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            releaseLocal(key, entry);
            throw failure;
        }
    }

    /** Whether any thread in this classloader currently holds the local half of the lock. */
    static boolean isLocallyHeld(Path lockFile) {
        Entry entry = ENTRIES.get(lockFile.toAbsolutePath().normalize());
        return entry != null && entry.owner != null;
    }

    /** The thread holding the local half of the lock, or null. Tests assert "nobody holds this". */
    static Thread ownerThread(Path lockFile) {
        Entry entry = ENTRIES.get(lockFile.toAbsolutePath().normalize());
        return entry == null ? null : entry.owner;
    }

    private static FileLock acquireFileLock(FileChannel channel, Path lockFile) throws IOException {
        long started = System.nanoTime();
        while (true) {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) return lock;
            } catch (OverlappingFileLockException ignored) {
                // Another classloader in this JVM owns the same durable player lock.
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("interrupted while waiting for player lock: " + lockFile);
            }
            if (System.nanoTime() - started > FILE_LOCK_WAIT_NANOS) {
                throw new IOException("timed out waiting for player lock: " + lockFile);
            }
            LockSupport.parkNanos(RETRY_NANOS);
        }
    }

    static final class Lease implements AutoCloseable {
        private final Path key;
        private final Entry entry;
        private final FileChannel channel;
        private final FileLock fileLock;
        private boolean closed;

        private Lease(Path key, Entry entry, FileChannel channel, FileLock fileLock) {
            this.key = key;
            this.entry = entry;
            this.channel = channel;
            this.fileLock = fileLock;
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            IOException closeFailure = null;
            try {
                fileLock.release();
            } catch (IOException failure) {
                closeFailure = failure;
            }
            try {
                channel.close();
            } catch (IOException failure) {
                if (closeFailure == null) closeFailure = failure;
                else closeFailure.addSuppressed(failure);
            } finally {
                releaseLocal(key, entry);
            }
            if (closeFailure != null) throw closeFailure;
        }
    }

    private static void releaseLocal(Path key, Entry entry) {
        entry.owner = null;
        entry.lock.unlock();
        dereference(key, entry);
    }

    private static void dereference(Path key, Entry entry) {
        ENTRIES.computeIfPresent(key, (ignored, current) -> {
            if (current != entry) return current;
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    private static final class Entry {
        private final ReentrantLock lock = new ReentrantLock();
        private volatile Thread owner;
        private int references;
    }
}
