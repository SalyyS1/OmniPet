package io.github.salyvn.omnipet.core.economy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/** Shares transaction locks across purchase and operator reconciliation paths. */
public final class PurchaseTransactionCoordinator {
    private static final int LOCK_STRIPES = 64;
    private static final PurchaseTransactionCoordinator SHARED = new PurchaseTransactionCoordinator();

    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    public PurchaseTransactionCoordinator() {
        for (int index = 0; index < locks.length; index++) locks[index] = new ReentrantLock();
    }

    public static PurchaseTransactionCoordinator shared() {
        return SHARED;
    }

    public Lease acquire(UUID transactionId) {
        if (transactionId == null) throw new IllegalArgumentException("transaction id is required");
        ReentrantLock lock = locks[Math.floorMod(transactionId.hashCode(), locks.length)];
        lock.lock();
        return new Lease(lock);
    }

    public static final class Lease implements AutoCloseable {
        private final ReentrantLock lock;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(ReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) lock.unlock();
        }
    }
}
