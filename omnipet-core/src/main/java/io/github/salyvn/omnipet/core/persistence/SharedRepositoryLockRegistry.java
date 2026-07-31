package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.LockSupport;

/** Combines classloader-local coordination with a process-wide player file lock. */
final class SharedRepositoryLockRegistry {
    private static final ConcurrentHashMap<Path, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final long RETRY_NANOS = 1_000_000L;

    private SharedRepositoryLockRegistry() {}

    static Lease acquire(Path lockFile) throws IOException {
        if (lockFile == null) throw new IllegalArgumentException("lock file is required");
        Path key = lockFile.toAbsolutePath().normalize();
        Entry entry = ENTRIES.compute(key, (ignored, current) -> {
            Entry selected = current == null ? new Entry() : current;
            selected.references++;
            return selected;
        });
        entry.lock.lock();
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

    private static FileLock acquireFileLock(FileChannel channel, Path lockFile) throws IOException {
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
        entry.lock.unlock();
        ENTRIES.computeIfPresent(key, (ignored, current) -> {
            if (current != entry) return current;
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    private static final class Entry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
