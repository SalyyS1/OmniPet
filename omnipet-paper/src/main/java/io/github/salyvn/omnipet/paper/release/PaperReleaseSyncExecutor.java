package io.github.salyvn.omnipet.paper.release;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperReleaseSyncExecutor implements ReleaseSyncExecutor {
    private static final long TIMEOUT_SECONDS = 10;
    private final JavaPlugin plugin;

    public PaperReleaseSyncExecutor(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public <T> T call(Callable<T> operation) throws Exception {
        Objects.requireNonNull(operation, "release sync operation");
        if (Bukkit.isPrimaryThread()) return operation.call();
        try {
            return plugin.getServer().getScheduler().callSyncMethod(plugin, operation)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw failure;
        }
    }
}
