package io.github.salyvn.omnipet.paper.catalog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import io.github.salyvn.omnipet.core.catalog.CatalogCacheKey;
import io.github.salyvn.omnipet.core.catalog.CatalogHealth;
import io.github.salyvn.omnipet.core.catalog.CatalogSnapshot;
import io.github.salyvn.omnipet.core.catalog.StatCatalogEntry;
import io.github.salyvn.omnipet.core.catalog.StatCatalogSource;
import io.github.salyvn.omnipet.core.studio.StatModifierType;

/** MythicLib adapter that never links the optional vendor classes at load time. */
public final class ReflectiveMythicLibStatCatalogSource implements StatCatalogSource {
    private static final String MYTHIC_LIB = "MythicLib";
    private static final String MMO_ITEMS = "MMOItems";
    private final Supplier<ProviderView> mythicLib;
    private final Supplier<ProviderView> mmoItems;

    public ReflectiveMythicLibStatCatalogSource(PluginManager pluginManager) {
        if (pluginManager == null) throw new IllegalArgumentException("plugin manager is required");
        this.mythicLib = () -> view(pluginManager.getPlugin(MYTHIC_LIB));
        this.mmoItems = () -> view(pluginManager.getPlugin(MMO_ITEMS));
    }

    ReflectiveMythicLibStatCatalogSource(Supplier<ProviderView> mythicLib, Supplier<ProviderView> mmoItems) {
        this.mythicLib = mythicLib;
        this.mmoItems = mmoItems == null ? () -> ProviderView.missing() : mmoItems;
    }

    @Override
    public String provider() {
        return MYTHIC_LIB;
    }

    @Override
    public String fingerprint() {
        return fingerprint(MYTHIC_LIB, safeView(mythicLib)) + "|" + fingerprint(MMO_ITEMS, safeView(mmoItems));
    }

    @Override
    public CatalogSnapshot<StatCatalogEntry> load(CatalogCacheKey cacheKey) {
        ProviderView provider = safeView(mythicLib);
        if (!provider.present()) return CatalogSnapshot.unavailable(cacheKey, "MythicLib is not installed");
        if (provider.failed()) return CatalogSnapshot.incompatible(cacheKey, "MythicLib provider state is incompatible");
        if (!provider.enabled()) return CatalogSnapshot.disabled(cacheKey, "MythicLib is disabled");
        if (provider.classLoader() == null) return CatalogSnapshot.incompatible(cacheKey, "MythicLib classloader is unavailable");
        try {
            Class<?> mythicLibType = Class.forName("io.lumine.mythic.lib.MythicLib", false, provider.classLoader());
            Object instance = invokeStatic(mythicLibType, "inst");
            Object statManager = invoke(instance, "getStats", new Class<?>[0]);
            Object registered = invoke(statManager, "getRegisteredStats", new Class<?>[0]);
            if (!(registered instanceof Iterable<?> values)) {
                return CatalogSnapshot.incompatible(cacheKey, "MythicLib stat registry returned an unsupported type");
            }

            Map<String, StatCatalogEntry> entriesById = new LinkedHashMap<>();
            for (Object raw : values) {
                if (!(raw instanceof String stat) || stat.isBlank()) continue;
                String vendorId = stat;
                try {
                    vendorId = handlerStat(statManager, stat).orElse(stat);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    // Registered IDs remain authoritative when optional handler metadata changes ABI.
                }
                String id = "mythiclib:" + vendorId.toLowerCase(Locale.ROOT);
                entriesById.putIfAbsent(id, new StatCatalogEntry(id, displayName(vendorId),
                        Set.of(StatModifierType.FLAT, StatModifierType.RELATIVE, StatModifierType.ADDITIVE_MULTIPLIER),
                        MYTHIC_LIB, CatalogHealth.AVAILABLE, cacheKey.registryGeneration(),
                        Map.of("vendorStatId", vendorId)));
            }
            List<StatCatalogEntry> entries = new ArrayList<>(entriesById.values());
            entries.sort(Comparator.comparing(StatCatalogEntry::id));
            return CatalogSnapshot.available(cacheKey, entries);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return CatalogSnapshot.incompatible(cacheKey, "MythicLib stat registry is incompatible");
        }
    }

    private static Optional<String> handlerStat(Object statManager, String stat) throws ReflectiveOperationException {
        Object result = invoke(statManager, "getHandler", new Class<?>[] {String.class}, stat);
        if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return Optional.empty();
        Object handler = optional.get();
        Object canonical = invoke(handler, "getStat", new Class<?>[0]);
        return canonical instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }

    private static Object invokeStatic(Class<?> type, String method) throws ReflectiveOperationException {
        return type.getMethod(method).invoke(null);
    }

    private static Object invoke(Object target, String method, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        Method reflected = target.getClass().getMethod(method, parameterTypes);
        return reflected.invoke(target, arguments);
    }

    private static ProviderView view(Plugin plugin) {
        if (plugin == null) return ProviderView.missing();
        String version;
        try {
            version = plugin.getPluginMeta().getVersion();
        } catch (RuntimeException error) {
            version = "unknown";
        }
        return new ProviderView(true, plugin.isEnabled(), false, version, plugin.getClass().getClassLoader());
    }

    private static String fingerprint(String name, ProviderView view) {
        return name + ':' + view.present() + ':' + view.enabled() + ':' + view.failed() + ':' + view.version();
    }

    private static ProviderView safeView(Supplier<ProviderView> supplier) {
        try {
            ProviderView view = supplier.get();
            return view == null ? ProviderView.failure() : view;
        } catch (RuntimeException | LinkageError error) {
            return ProviderView.failure();
        }
    }

    private static String displayName(String vendorId) {
        String[] words = vendorId.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.length() == 0 ? vendorId : result.toString();
    }

    record ProviderView(boolean present, boolean enabled, boolean failed, String version, ClassLoader classLoader) {
        ProviderView {
            version = version == null || version.isBlank() ? "unknown" : version;
        }

        static ProviderView missing() {
            return new ProviderView(false, false, false, "missing", null);
        }

        static ProviderView available(ClassLoader loader) {
            return new ProviderView(true, true, false, "test", loader);
        }

        static ProviderView failure() {
            return new ProviderView(true, false, true, "error", null);
        }
    }
}
