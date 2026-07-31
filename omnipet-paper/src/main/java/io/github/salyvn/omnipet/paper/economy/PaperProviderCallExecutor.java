package io.github.salyvn.omnipet.paper.economy;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs vendor calls on Paper's primary thread while durable storage remains asynchronous. */
final class PaperProviderCallExecutor implements ProviderCallExecutor {
    private static final long TIMEOUT_SECONDS = 10;

    private final SyncCallScheduler scheduler;
    private final Object lifecycle = new Object();
    private final Set<Future<?>> active = new HashSet<>();
    private boolean shuttingDown;

    PaperProviderCallExecutor(JavaPlugin plugin) {
        this(paperScheduler(plugin));
    }

    PaperProviderCallExecutor(SyncCallScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "sync call scheduler");
    }

    @Override
    public Object call(Callable<?> operation) throws Exception {
        Objects.requireNonNull(operation, "provider operation");
        if (scheduler.isPrimaryThread()) {
            synchronized (lifecycle) {
                ensureAvailable();
            }
            return operation.call();
        }
        Future<?> future;
        synchronized (lifecycle) {
            ensureAvailable();
            future = Objects.requireNonNull(scheduler.schedule(operation), "scheduled provider future");
            active.add(future);
        }
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw failure;
        } finally {
            synchronized (lifecycle) {
                active.remove(future);
            }
        }
    }

    void shutdown() {
        synchronized (lifecycle) {
            shuttingDown = true;
            active.forEach(future -> future.cancel(true));
            active.clear();
        }
    }

    private void ensureAvailable() {
        if (shuttingDown) throw new IllegalStateException("provider calls are shutting down");
    }

    private static SyncCallScheduler paperScheduler(JavaPlugin plugin) {
        JavaPlugin checked = Objects.requireNonNull(plugin, "plugin");
        return new SyncCallScheduler() {
            @Override
            public boolean isPrimaryThread() {
                return Bukkit.isPrimaryThread();
            }

            @Override
            public Future<?> schedule(Callable<?> operation) {
                return checked.getServer().getScheduler().callSyncMethod(checked, operation);
            }
        };
    }

    interface SyncCallScheduler {
        boolean isPrimaryThread();

        Future<?> schedule(Callable<?> operation);
    }
}
