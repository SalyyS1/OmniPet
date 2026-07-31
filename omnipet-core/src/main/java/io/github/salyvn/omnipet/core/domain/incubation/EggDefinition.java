package io.github.salyvn.omnipet.core.domain.incubation;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;

public record EggDefinition(
        String id,
        PetTier tier,
        long baseActiveMillis,
        List<HatchCandidate> candidates,
        Map<String, Object> extensions) {
    public static final long MAX_ACTIVE_MILLIS = Duration.ofDays(3650).toMillis();

    public EggDefinition {
        id = StableId.requireValid(id);
        if (tier == null) throw new IllegalArgumentException("egg tier is required");
        if (baseActiveMillis < 1 || baseActiveMillis > MAX_ACTIVE_MILLIS) {
            throw new IllegalArgumentException("egg duration is outside the supported range");
        }
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
        if (candidates.isEmpty()) throw new IllegalArgumentException("egg candidate pool cannot be empty");
        if (new HashSet<>(candidates.stream().map(HatchCandidate::definitionId).toList()).size()
                != candidates.size()) {
            throw new IllegalArgumentException("egg candidate IDs must be unique");
        }
        if (candidates.stream().mapToDouble(HatchCandidate::weight).sum() <= 0) {
            throw new IllegalArgumentException("egg candidate weights must have a positive total");
        }
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(extensions, "egg[" + id + "].extensions");
    }
}
