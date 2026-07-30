package io.github.salyvn.omnipet.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.studio.StatModifierType;

class StatCatalogTest {
    @Test
    void unavailableDisabledAndIncompatibleSnapshotsAreExplicitAndEmpty() {
        CatalogCacheKey key = new CatalogCacheKey(4, "missing-vendor");

        assertEquals(CatalogHealth.UNAVAILABLE, CatalogSnapshot.unavailable(key, "not installed").health());
        assertEquals(CatalogHealth.DISABLED, CatalogSnapshot.disabled(key, "disabled").health());
        assertEquals(CatalogHealth.INCOMPATIBLE, CatalogSnapshot.incompatible(key, "wrong ABI").health());
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogSnapshot<>(key, CatalogHealth.DISABLED, List.of("invalid"), "disabled"));
    }

    @Test
    void entriesAndSnapshotsOwnImmutableCollections() {
        StatCatalogEntry entry = new StatCatalogEntry("mythiclib:attack_damage", "Attack Damage",
                Set.of(StatModifierType.FLAT), "MythicLib", CatalogHealth.AVAILABLE, 8,
                Map.of("vendor", Map.of("numeric", true)));
        CatalogSnapshot<StatCatalogEntry> snapshot = CatalogSnapshot.available(new CatalogCacheKey(8, "ml-1"), List.of(entry));

        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().add(entry));
        assertThrows(UnsupportedOperationException.class, () -> entry.supportedModifierTypes().add(StatModifierType.RELATIVE));
        assertThrows(UnsupportedOperationException.class, () -> entry.extensions().put("x", true));
    }

    @Test
    void cacheUsesRegistryGenerationAndProviderFingerprint() {
        AtomicInteger loads = new AtomicInteger();
        StatCatalogSource source = new StatCatalogSource() {
            @Override public String provider() { return "test"; }

            @Override public CatalogSnapshot<StatCatalogEntry> load(CatalogCacheKey key) {
                loads.incrementAndGet();
                return CatalogSnapshot.available(key, List.of());
            }
        };
        CachingStatCatalog catalog = new CachingStatCatalog(source);
        CatalogCacheKey firstKey = new CatalogCacheKey(1, "provider-a");

        CatalogSnapshot<StatCatalogEntry> first = catalog.snapshot(firstKey);
        assertSame(first, catalog.snapshot(firstKey));
        catalog.snapshot(new CatalogCacheKey(2, "provider-a"));
        catalog.snapshot(new CatalogCacheKey(2, "provider-b"));

        assertEquals(3, loads.get());
        catalog.invalidate();
        catalog.snapshot(firstKey);
        assertEquals(4, loads.get());
    }
}
