package io.github.salyvn.omnipet.core.domain.incubation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;

public record IncubationOutcome(
        UUID petInstanceId,
        String definitionId,
        long definitionRevision,
        PetTier tier,
        HeadIcon icon,
        Map<String, Object> iconExtensions,
        String rarityId,
        double qualityScore,
        long seed,
        String algorithmId,
        List<RealizedStat> realizedStats,
        long totalActiveMillis,
        Map<String, Object> extensions) {
    public IncubationOutcome {
        if (petInstanceId == null) throw new IllegalArgumentException("pet instance id is required");
        definitionId = StableId.requireValid(definitionId);
        if (definitionRevision < 0) throw new IllegalArgumentException("definition revision cannot be negative");
        if (tier == null) throw new IllegalArgumentException("pet tier is required");
        if (icon == null) throw new IllegalArgumentException("pet head icon is required");
        iconExtensions = RawNodeValues.immutableMap(iconExtensions == null ? Map.of() : iconExtensions);
        RawNodeValues.rejectNonFinite(iconExtensions, "incubationOutcome.iconExtensions");
        rarityId = IncubationValues.requireReference(rarityId, "rarity id");
        qualityScore = IncubationValues.requireFinite(qualityScore, "quality score");
        if (qualityScore < 0 || qualityScore > 100) {
            throw new IllegalArgumentException("quality score must be inside 0..100");
        }
        algorithmId = IncubationValues.requireReference(algorithmId, "algorithm id");
        realizedStats = List.copyOf(realizedStats == null ? List.of() : realizedStats);
        if (new HashSet<>(realizedStats.stream().map(RealizedStat::id).toList()).size()
                != realizedStats.size()) {
            throw new IllegalArgumentException("realized stat IDs must be unique");
        }
        if (totalActiveMillis < 1 || totalActiveMillis > EggDefinition.MAX_ACTIVE_MILLIS) {
            throw new IllegalArgumentException("resolved hatch duration is outside the supported range");
        }
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(extensions, "incubationOutcome.extensions");
    }
}
