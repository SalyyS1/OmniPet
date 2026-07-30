package io.github.salyvn.omnipet.core.catalog;

public interface StatCatalogSource {
    String provider();

    /** Fingerprint changes when the optional provider is reloaded or upgraded. */
    default String fingerprint() {
        return provider();
    }

    CatalogSnapshot<StatCatalogEntry> load(CatalogCacheKey cacheKey);
}
