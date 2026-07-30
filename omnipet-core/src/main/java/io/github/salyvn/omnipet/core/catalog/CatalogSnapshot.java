package io.github.salyvn.omnipet.core.catalog;

import java.util.List;

/** Immutable catalog result for one generation/fingerprint pair. */
public record CatalogSnapshot<T>(CatalogCacheKey cacheKey, CatalogHealth health, List<T> entries, String detail) {
    public CatalogSnapshot {
        if (cacheKey == null) throw new IllegalArgumentException("catalog cache key is required");
        if (health == null) throw new IllegalArgumentException("catalog health is required");
        entries = List.copyOf(entries == null ? List.of() : entries);
        detail = detail == null ? "" : detail;
        if (health != CatalogHealth.AVAILABLE && !entries.isEmpty()) {
            throw new IllegalArgumentException("unhealthy catalog snapshots cannot expose entries");
        }
    }

    public static <T> CatalogSnapshot<T> available(CatalogCacheKey key, List<T> entries) {
        return new CatalogSnapshot<>(key, CatalogHealth.AVAILABLE, entries, "");
    }

    public static <T> CatalogSnapshot<T> unavailable(CatalogCacheKey key, String detail) {
        return unhealthy(key, CatalogHealth.UNAVAILABLE, detail);
    }

    public static <T> CatalogSnapshot<T> disabled(CatalogCacheKey key, String detail) {
        return unhealthy(key, CatalogHealth.DISABLED, detail);
    }

    public static <T> CatalogSnapshot<T> incompatible(CatalogCacheKey key, String detail) {
        return unhealthy(key, CatalogHealth.INCOMPATIBLE, detail);
    }

    public long generation() {
        return cacheKey.registryGeneration();
    }

    public String providerFingerprint() {
        return cacheKey.providerFingerprint();
    }

    private static <T> CatalogSnapshot<T> unhealthy(CatalogCacheKey key, CatalogHealth health, String detail) {
        return new CatalogSnapshot<>(key, health, List.of(), detail);
    }
}
