package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.catalog.CatalogHealth;
import io.github.salyvn.omnipet.core.catalog.CatalogSnapshot;
import io.github.salyvn.omnipet.core.catalog.StatCatalogEntry;
import io.github.salyvn.omnipet.core.studio.StatLogicalIdentity;
import io.github.salyvn.omnipet.core.studio.StudioConflictException;
import io.github.salyvn.omnipet.core.studio.StudioStat;

final class StudioCatalogSelections {
    private StudioCatalogSelections() {}

    static void validate(CatalogSnapshot<StatCatalogEntry> snapshot, List<StudioStat> stats) {
        if (snapshot.health() != CatalogHealth.AVAILABLE) {
            for (StudioStat stat : stats) {
                if (stat.extensions().get("vendorStatId") instanceof String) {
                    throw new StudioConflictException(
                            "MythicLib is unavailable; convert picker stat to a manual ID before saving: " + stat.id());
                }
            }
            return;
        }
        Map<String, StatCatalogEntry> available = new LinkedHashMap<>();
        for (StatCatalogEntry entry : snapshot.entries()) {
            available.putIfAbsent(StatLogicalIdentity.key(entry.id(), entry.extensions()), entry);
        }
        for (StudioStat stat : stats) {
            if (!(stat.extensions().get("vendorStatId") instanceof String)) continue;
            StatCatalogEntry current = available.get(StatLogicalIdentity.key(stat));
            if (current == null) {
                throw new StudioConflictException(
                        "MythicLib stat catalog changed; refresh stats before saving: " + stat.id());
            }
            if (!current.supportedModifierTypes().contains(stat.modifierType())) {
                throw new StudioConflictException(
                        "MythicLib stat modifier changed; refresh stats before saving: " + stat.id());
            }
        }
    }
}
