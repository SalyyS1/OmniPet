package io.github.salyvn.omnipet.core.studio;

import java.util.Locale;
import java.util.Map;

/** Normalizes legacy and namespaced stat references without rewriting untouched YAML. */
public final class StatLogicalIdentity {
    private static final String MYTHIC_LIB_PREFIX = "mythiclib:";

    private StatLogicalIdentity() {}

    public static String key(StudioStat stat) {
        if (stat == null) throw new IllegalArgumentException("stat is required");
        return key(stat.id(), stat.extensions());
    }

    public static String key(String id, Map<String, Object> extensions) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("stat id is required");
        Object vendorValue = extensions == null ? null : extensions.get("vendorStatId");
        if (vendorValue instanceof String vendorId && !vendorId.isBlank()) {
            return MYTHIC_LIB_PREFIX + vendorId.toUpperCase(Locale.ROOT);
        }
        if (id.regionMatches(true, 0, MYTHIC_LIB_PREFIX, 0, MYTHIC_LIB_PREFIX.length())) {
            return MYTHIC_LIB_PREFIX + id.substring(MYTHIC_LIB_PREFIX.length()).toUpperCase(Locale.ROOT);
        }
        if (!id.contains(":")) return MYTHIC_LIB_PREFIX + id.toUpperCase(Locale.ROOT);
        return id.toLowerCase(Locale.ROOT);
    }
}
