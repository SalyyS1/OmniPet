package io.github.salyvn.omnipet.core.incubation;

import java.util.List;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.HatchRarityBand;
import io.github.salyvn.omnipet.core.studio.StudioStat;

record PetIncubationProfile(
        PetDefinition definition,
        List<StudioStat> stats,
        List<HatchRarityBand> rarityBands) {
    PetIncubationProfile {
        if (definition == null) throw new IllegalArgumentException("pet definition is required");
        stats = List.copyOf(stats == null ? List.of() : stats);
        rarityBands = List.copyOf(rarityBands == null ? List.of() : rarityBands);
        if (rarityBands.isEmpty()) throw new IllegalArgumentException("pet rarity bands cannot be empty");
        double total = rarityBands.stream().mapToDouble(HatchRarityBand::weight).sum();
        if (!Double.isFinite(total) || total <= 0) {
            throw new IllegalArgumentException("pet rarity weights must have a finite positive total");
        }
    }
}
