package io.github.salyvn.omnipet.core.migration.legacy;

import java.util.Set;

public final class LegacyItemKeys {
    public static final String NAMESPACE = "passivepet";
    public static final Set<String> SUPPORTED_KEYS = Set.of("pet", "egg", "food", "hatcher", "evolver");

    private LegacyItemKeys() {}
}
