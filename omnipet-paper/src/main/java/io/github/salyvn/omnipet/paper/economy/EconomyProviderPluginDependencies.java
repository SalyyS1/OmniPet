package io.github.salyvn.omnipet.paper.economy;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.salyvn.omnipet.core.economy.EconomyProvider;

final class EconomyProviderPluginDependencies {
    private final Map<EconomyProvider, Set<String>> dependencies;

    EconomyProviderPluginDependencies(Map<EconomyProvider, ? extends Set<String>> dependencies) {
        EnumMap<EconomyProvider, Set<String>> normalized = new EnumMap<>(EconomyProvider.class);
        if (dependencies != null) {
            dependencies.forEach((provider, names) -> {
                if (provider == null) return;
                LinkedHashSet<String> values = new LinkedHashSet<>();
                if (names != null) names.forEach(name -> values.add(normalize(name)));
                values.remove("");
                normalized.put(provider, Set.copyOf(values));
            });
        }
        this.dependencies = Map.copyOf(normalized);
    }

    static EconomyProviderPluginDependencies empty() {
        return new EconomyProviderPluginDependencies(Map.of());
    }

    Set<EconomyProvider> affectedBy(String pluginName) {
        String normalizedName = normalize(pluginName);
        if (normalizedName.isEmpty()) return Set.of();
        EnumSet<EconomyProvider> affected = EnumSet.noneOf(EconomyProvider.class);
        dependencies.forEach((provider, names) -> {
            if (names.contains(normalizedName)) affected.add(provider);
        });
        return Set.copyOf(affected);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
