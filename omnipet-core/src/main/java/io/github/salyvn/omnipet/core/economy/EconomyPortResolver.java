package io.github.salyvn.omnipet.core.economy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Resolves the currently live provider adapter at the moment of each operation. */
@FunctionalInterface
public interface EconomyPortResolver {
    Optional<EconomyPort> find(EconomyProvider provider);

    static EconomyPortResolver fixed(Map<EconomyProvider, EconomyPort> ports) {
        EnumMap<EconomyProvider, EconomyPort> validated = new EnumMap<>(EconomyProvider.class);
        if (ports != null) {
            ports.forEach((provider, port) -> {
                if (provider == null || port == null || provider != port.provider()) {
                    throw new IllegalArgumentException("economy port key must match its provider");
                }
                validated.put(provider, port);
            });
        }
        Map<EconomyProvider, EconomyPort> snapshot = Map.copyOf(validated);
        return provider -> Optional.ofNullable(snapshot.get(provider));
    }
}
