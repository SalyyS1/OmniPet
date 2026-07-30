package io.github.salyvn.omnipet.core.catalog;

public interface StatCatalogSource {
    String provider();

    CatalogSnapshot<StatCatalogEntry> load(CatalogCacheKey cacheKey);
}
