package io.github.salyvn.omnipet.paper.economy;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.economy.EconomyPort;
import io.github.salyvn.omnipet.core.economy.EconomyBalanceResult;
import io.github.salyvn.omnipet.core.economy.EconomyPortResolver;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;

/** Atomically publishes only adapters whose plugin, service, and ABI probes all pass. */
public final class PaperEconomyProviderRegistry implements EconomyPortResolver {
    private final JavaPlugin plugin;
    private final PaperProviderCallExecutor calls;
    private volatile Map<EconomyProvider, EconomyPort> adapters = Map.of();
    private volatile Map<EconomyProvider, String> diagnostics = Map.of();
    private volatile EconomyProviderPluginDependencies pluginDependencies =
            EconomyProviderPluginDependencies.empty();

    public PaperEconomyProviderRegistry(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.calls = new PaperProviderCallExecutor(plugin);
    }

    public synchronized void refresh() {
        EnumMap<EconomyProvider, EconomyPort> next = new EnumMap<>(EconomyProvider.class);
        EnumMap<EconomyProvider, String> nextDiagnostics = new EnumMap<>(EconomyProvider.class);
        EnumMap<EconomyProvider, Set<String>> nextDependencies = new EnumMap<>(EconomyProvider.class);
        loadVault(next, nextDiagnostics, nextDependencies);
        loadPlayerPoints(next, nextDiagnostics, nextDependencies);
        adapters = Map.copyOf(next);
        diagnostics = Map.copyOf(nextDiagnostics);
        pluginDependencies = new EconomyProviderPluginDependencies(nextDependencies);
    }

    public synchronized void invalidate() {
        adapters = Map.of();
        diagnostics = Map.of(
                EconomyProvider.VAULT, "provider registry refresh pending",
                EconomyProvider.PLAYER_POINTS, "provider registry refresh pending");
        pluginDependencies = EconomyProviderPluginDependencies.empty();
    }

    public synchronized boolean invalidate(String pluginName) {
        Set<EconomyProvider> affected = pluginDependencies.affectedBy(pluginName);
        if (affected.isEmpty()) return false;
        EnumMap<EconomyProvider, EconomyPort> next = new EnumMap<>(EconomyProvider.class);
        next.putAll(adapters);
        EnumMap<EconomyProvider, String> nextDiagnostics = new EnumMap<>(EconomyProvider.class);
        nextDiagnostics.putAll(diagnostics);
        affected.forEach(provider -> {
            next.remove(provider);
            nextDiagnostics.put(provider, "provider registry refresh pending");
        });
        adapters = Map.copyOf(next);
        diagnostics = Map.copyOf(nextDiagnostics);
        return true;
    }

    public synchronized void close() {
        invalidate();
        calls.shutdown();
    }

    @Override
    public Optional<EconomyPort> find(EconomyProvider provider) {
        return Optional.ofNullable(adapters.get(provider));
    }

    public String diagnostic(EconomyProvider provider) {
        return diagnostics.getOrDefault(provider, "provider available");
    }

    public EconomyBalanceResult balance(java.util.UUID playerId, EconomyProvider provider) {
        EconomyPort port = adapters.get(provider);
        return port == null
                ? EconomyBalanceResult.unavailable(diagnostic(provider))
                : port.balance(playerId);
    }

    private void loadVault(
            Map<EconomyProvider, EconomyPort> next,
            Map<EconomyProvider, String> nextDiagnostics,
            Map<EconomyProvider, Set<String>> nextDependencies) {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        dependencies.add("Vault");
        nextDependencies.put(EconomyProvider.VAULT, dependencies);
        Plugin vault = enabledPlugin("Vault");
        if (vault == null) {
            nextDiagnostics.put(EconomyProvider.VAULT, "Vault plugin is not enabled");
            return;
        }
        try {
            Class<?> economyClass = Class.forName(
                    "net.milkbowl.vault.economy.Economy",
                    false,
                    vault.getClass().getClassLoader());
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager()
                    .getRegistration((Class) economyClass);
            if (registration == null || registration.getProvider() == null) {
                nextDiagnostics.put(EconomyProvider.VAULT, "Vault has no registered economy service");
                return;
            }
            if (registration.getPlugin() != null) dependencies.add(registration.getPlugin().getName());
            next.put(EconomyProvider.VAULT, new ReflectiveVaultEconomyPort(
                    registration.getProvider(),
                    economyClass,
                    calls,
                    playerId -> Bukkit.getOfflinePlayer(playerId),
                    org.bukkit.OfflinePlayer.class));
            nextDiagnostics.put(EconomyProvider.VAULT, "Vault economy service available");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            nextDiagnostics.put(EconomyProvider.VAULT, detail("Vault adapter incompatible", failure));
        }
    }

    private void loadPlayerPoints(
            Map<EconomyProvider, EconomyPort> next,
            Map<EconomyProvider, String> nextDiagnostics,
            Map<EconomyProvider, Set<String>> nextDependencies) {
        nextDependencies.put(EconomyProvider.PLAYER_POINTS, Set.of("PlayerPoints"));
        Plugin playerPoints = enabledPlugin("PlayerPoints");
        if (playerPoints == null) {
            nextDiagnostics.put(EconomyProvider.PLAYER_POINTS, "PlayerPoints plugin is not enabled");
            return;
        }
        try {
            Method getApi = playerPoints.getClass().getMethod("getAPI");
            Object api = ReflectiveEconomySupport.invoke(getApi, playerPoints);
            next.put(EconomyProvider.PLAYER_POINTS, new ReflectivePlayerPointsEconomyPort(api, calls));
            nextDiagnostics.put(EconomyProvider.PLAYER_POINTS, "PlayerPoints API available");
        } catch (Exception | LinkageError failure) {
            nextDiagnostics.put(EconomyProvider.PLAYER_POINTS, detail("PlayerPoints adapter incompatible", failure));
        }
    }

    private Plugin enabledPlugin(String name) {
        Plugin candidate = plugin.getServer().getPluginManager().getPlugin(name);
        return candidate != null && candidate.isEnabled() ? candidate : null;
    }

    private static String detail(String prefix, Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? prefix : prefix + ": " + message;
    }
}
