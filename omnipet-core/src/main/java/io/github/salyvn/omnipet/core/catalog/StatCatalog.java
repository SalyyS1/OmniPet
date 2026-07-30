package io.github.salyvn.omnipet.core.catalog;

public interface StatCatalog {
    CatalogSnapshot<StatCatalogEntry> snapshot(CatalogCacheKey cacheKey);
}
