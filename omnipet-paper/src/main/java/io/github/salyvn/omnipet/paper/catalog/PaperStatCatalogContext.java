package io.github.salyvn.omnipet.paper.catalog;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.github.salyvn.omnipet.core.catalog.CatalogCacheKey;
import io.github.salyvn.omnipet.core.catalog.CatalogSnapshot;
import io.github.salyvn.omnipet.core.catalog.CachingStatCatalog;
import io.github.salyvn.omnipet.core.catalog.StatCatalog;
import io.github.salyvn.omnipet.core.catalog.StatCatalogSource;

/** Generation-aware Paper facade for the optional stat catalog. */
public final class PaperStatCatalogContext {
    private final StatCatalog catalog;
    private final Supplier<String> fingerprint;
    private final AtomicLong providerEpoch = new AtomicLong();

    public PaperStatCatalogContext(ReflectiveMythicLibStatCatalogSource source) {
        this(new CachingStatCatalog(Objects.requireNonNull(source, "source")), source::fingerprint);
    }

    public PaperStatCatalogContext(StatCatalog catalog, Supplier<String> fingerprint) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    }

    public static PaperStatCatalogContext unavailable() {
        StatCatalogSource source = new StatCatalogSource() {
            @Override public String provider() { return "MythicLib"; }
            @Override public CatalogSnapshot<io.github.salyvn.omnipet.core.catalog.StatCatalogEntry> load(CatalogCacheKey key) {
                return CatalogSnapshot.unavailable(key, "catalog adapter is not configured");
            }
        };
        return new PaperStatCatalogContext(new CachingStatCatalog(source), () -> "not-configured");
    }

    public CatalogSnapshot<io.github.salyvn.omnipet.core.catalog.StatCatalogEntry> snapshot(long generation) {
        return catalog.snapshot(new CatalogCacheKey(generation,
                fingerprint.get() + "|epoch:" + providerEpoch.get()));
    }

    public void invalidate() {
        providerEpoch.incrementAndGet();
        if (catalog instanceof CachingStatCatalog caching) caching.invalidate();
    }

    public long providerEpoch() {
        return providerEpoch.get();
    }
}
