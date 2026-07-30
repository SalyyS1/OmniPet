package io.github.salyvn.omnipet.paper.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.salyvn.omnipet.core.catalog.CatalogCacheKey;
import io.github.salyvn.omnipet.core.catalog.CatalogHealth;
import io.github.salyvn.omnipet.core.catalog.CatalogSnapshot;
import org.junit.jupiter.api.Test;

class ReflectiveMythicLibStatCatalogSourceTest {
    @Test
    void reportsMissingAndDisabledProvidersWithoutLinkage() {
        ReflectiveMythicLibStatCatalogSource missing = new ReflectiveMythicLibStatCatalogSource(
                ReflectiveMythicLibStatCatalogSource.ProviderView::missing,
                ReflectiveMythicLibStatCatalogSource.ProviderView::missing);
        assertEquals(CatalogHealth.UNAVAILABLE, missing.load(key()).health());

        ReflectiveMythicLibStatCatalogSource disabled = new ReflectiveMythicLibStatCatalogSource(
                () -> new ReflectiveMythicLibStatCatalogSource.ProviderView(true, false, false, "1.0", getClassLoader()),
                ReflectiveMythicLibStatCatalogSource.ProviderView::missing);
        assertEquals(CatalogHealth.DISABLED, disabled.load(key()).health());
    }

    @Test
    void readsAndSortsMythicLibStatsThroughVendorClassLoader() {
        ReflectiveMythicLibStatCatalogSource source = new ReflectiveMythicLibStatCatalogSource(
                () -> ReflectiveMythicLibStatCatalogSource.ProviderView.available(getClassLoader()),
                ReflectiveMythicLibStatCatalogSource.ProviderView::missing);

        CatalogSnapshot<?> snapshot = source.load(key());

        assertEquals(CatalogHealth.AVAILABLE, snapshot.health());
        assertEquals(2, snapshot.entries().size());
        assertEquals("mythiclib:attack_damage", ((io.github.salyvn.omnipet.core.catalog.StatCatalogEntry)
                snapshot.entries().getFirst()).id());
        assertEquals("Attack Damage", ((io.github.salyvn.omnipet.core.catalog.StatCatalogEntry)
                snapshot.entries().getFirst()).displayName());
        assertTrue(((io.github.salyvn.omnipet.core.catalog.StatCatalogEntry) snapshot.entries().getFirst())
                .extensions().containsKey("vendorStatId"));
    }

    @Test
    void fingerprintIncludesOptionalProviderState() {
        ReflectiveMythicLibStatCatalogSource source = new ReflectiveMythicLibStatCatalogSource(
                () -> new ReflectiveMythicLibStatCatalogSource.ProviderView(true, true, false, "1.7.1", getClassLoader()),
                () -> new ReflectiveMythicLibStatCatalogSource.ProviderView(true, false, false, "6.10.1", getClassLoader()));

        assertEquals("MythicLib:true:true:false:1.7.1|MMOItems:true:false:false:6.10.1", source.fingerprint());
    }

    @Test
    void handlerMetadataFailureFallsBackToRegisteredIds() {
        io.lumine.mythic.lib.MythicLib.setHandlerFailure(true);
        try {
            ReflectiveMythicLibStatCatalogSource source = new ReflectiveMythicLibStatCatalogSource(
                    () -> ReflectiveMythicLibStatCatalogSource.ProviderView.available(getClassLoader()),
                    ReflectiveMythicLibStatCatalogSource.ProviderView::missing);

            CatalogSnapshot<?> snapshot = source.load(key());

            assertEquals(CatalogHealth.AVAILABLE, snapshot.health());
            assertEquals(2, snapshot.entries().size());
        } finally {
            io.lumine.mythic.lib.MythicLib.setHandlerFailure(false);
        }
    }

    private static CatalogCacheKey key() {
        return new CatalogCacheKey(4, "test");
    }

    private static ClassLoader getClassLoader() {
        return ReflectiveMythicLibStatCatalogSourceTest.class.getClassLoader();
    }
}
