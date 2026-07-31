package io.github.salyvn.omnipet.paper.economy;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs vendor calls on Paper's primary thread while durable storage remains asynchronous. */
final class PaperProviderCallExecutor implements ProviderCallExecutor {
    private static final long TIMEOUT_SECONDS = 10;

    private final JavaPlugin plugin;
    private final java.util.Set<Future<?>> active = ConcurrentHashMap.newKeySet();
    private volatile boolean shuttingDown;

    PaperProviderCallExecutor(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Object call(Callable<?> operation) throws Exception {
        Objects.requireNonNull(operation, "provider operation");
        if (shuttingDown) throw new IllegalStateException("provider calls are shutting down");
        if (Bukkit.isPrimaryThread()) return operation.call();
        Future<?> future = plugin.getServer().getScheduler().callSyncMethod(plugin, operation);
        active.add(future);
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw failure;
        } finally {
            active.remove(future);
        }
    }

    void shutdown() {
        shuttingDown = true;
        active.forEach(future -> future.cancel(true));
        active.clear();
    }
}
