package io.github.salyvn.omnipet.paper.entitlement;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Capability gate for LuckPerms node mutation. */
public final class PaperLuckPermsEntitlementRegistry {
    private final JavaPlugin plugin;
    private volatile LuckPermsSlotEntitlementAdapter adapter;
    private volatile String diagnostic = "LuckPerms registry has not been refreshed";

    public PaperLuckPermsEntitlementRegistry(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized void refresh() {
        adapter = null;
        Plugin luckPerms = plugin.getServer().getPluginManager().getPlugin("LuckPerms");
        if (luckPerms == null || !luckPerms.isEnabled()) {
            diagnostic = "LuckPerms plugin is not enabled";
            return;
        }
        try {
            ClassLoader loader = luckPerms.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("net.luckperms.api.LuckPerms", false, loader);
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager()
                    .getRegistration((Class) apiClass);
            if (registration == null || registration.getProvider() == null) {
                diagnostic = "LuckPerms API service is not registered";
                return;
            }
            adapter = new LuckPermsSlotEntitlementAdapter(registration.getProvider(), loader);
            diagnostic = "LuckPerms entitlement API available";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            String message = failure.getMessage();
            diagnostic = message == null || message.isBlank()
                    ? "LuckPerms entitlement adapter incompatible"
                    : "LuckPerms entitlement adapter incompatible: " + message;
        }
    }

    public synchronized void invalidate() {
        adapter = null;
        diagnostic = "LuckPerms registry refresh pending";
    }

    public synchronized boolean invalidate(String pluginName) {
        if (!"LuckPerms".equalsIgnoreCase(pluginName)) return false;
        invalidate();
        return true;
    }

    public Optional<LuckPermsSlotEntitlementAdapter> adapter() {
        return Optional.ofNullable(adapter);
    }

    public String diagnostic() {
        return diagnostic;
    }
}
