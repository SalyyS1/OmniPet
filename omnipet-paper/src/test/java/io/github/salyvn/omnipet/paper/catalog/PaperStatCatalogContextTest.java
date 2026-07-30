package io.github.salyvn.omnipet.paper.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.catalog.CatalogCacheKey;
import io.github.salyvn.omnipet.core.catalog.CatalogSnapshot;
import io.github.salyvn.omnipet.core.catalog.CachingStatCatalog;
import io.github.salyvn.omnipet.core.catalog.StatCatalogEntry;
import io.github.salyvn.omnipet.core.catalog.StatCatalogSource;

class PaperStatCatalogContextTest {
    @Test
    void invalidationAdvancesProviderEpochAndForcesReload() {
        AtomicInteger loads = new AtomicInteger();
        StatCatalogSource source = new StatCatalogSource() {
            @Override public String provider() { return "test"; }

            @Override
            public CatalogSnapshot<StatCatalogEntry> load(CatalogCacheKey key) {
                loads.incrementAndGet();
                return CatalogSnapshot.available(key, List.of());
            }
        };
        PaperStatCatalogContext context = new PaperStatCatalogContext(
                new CachingStatCatalog(source), () -> "same-version");

        String first = context.snapshot(4).providerFingerprint();
        assertEquals(first, context.snapshot(4).providerFingerprint());
        context.invalidate();
        String second = context.snapshot(4).providerFingerprint();

        assertNotEquals(first, second);
        assertEquals(1, context.providerEpoch());
        assertEquals(2, loads.get());
    }
}
