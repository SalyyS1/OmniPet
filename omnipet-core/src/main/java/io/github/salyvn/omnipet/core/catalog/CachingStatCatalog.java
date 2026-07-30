package io.github.salyvn.omnipet.core.catalog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small cache seam: providers are queried once per registry generation and fingerprint. */
public final class CachingStatCatalog implements StatCatalog {
    private final StatCatalogSource source;
    private final Map<CatalogCacheKey, CatalogSnapshot<StatCatalogEntry>> snapshots = new ConcurrentHashMap<>();

    public CachingStatCatalog(StatCatalogSource source) {
        if (source == null) throw new IllegalArgumentException("stat catalog source is required");
        this.source = source;
    }

    @Override
    public CatalogSnapshot<StatCatalogEntry> snapshot(CatalogCacheKey cacheKey) {
        if (cacheKey == null) throw new IllegalArgumentException("catalog cache key is required");
        return snapshots.computeIfAbsent(cacheKey, key -> {
            CatalogSnapshot<StatCatalogEntry> loaded = source.load(key);
            if (loaded == null || !key.equals(loaded.cacheKey())) {
                throw new IllegalStateException("catalog source returned a missing or mismatched snapshot");
            }
            return loaded;
        });
    }

    public void invalidate() {
        snapshots.clear();
    }
}
