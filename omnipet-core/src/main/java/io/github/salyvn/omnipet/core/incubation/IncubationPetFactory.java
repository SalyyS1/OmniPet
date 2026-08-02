package io.github.salyvn.omnipet.core.incubation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.RealizedStat;

final class IncubationPetFactory {
    private IncubationPetFactory() {}

    static PetInstance create(IncubationState incubation) {
        var outcome = incubation.outcome();
        Map<String, Object> hatching = new LinkedHashMap<>();
        hatching.put("incubationId", incubation.id().toString());
        hatching.put("eggId", incubation.eggId());
        hatching.put("rarityId", outcome.rarityId());
        hatching.put("qualityScore", outcome.qualityScore());
        hatching.put("seed", outcome.seed());
        hatching.put("algorithmId", outcome.algorithmId());

        List<Map<String, Object>> stats = new ArrayList<>(outcome.realizedStats().size());
        for (RealizedStat stat : outcome.realizedStats()) {
            Map<String, Object> node = new LinkedHashMap<>(stat.extensions());
            node.put("id", stat.id());
            node.put("modifierType", stat.modifierType().name());
            node.put("value", stat.value());
            stats.add(node);
        }

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("hatching", hatching);
        components.put("stats", stats);
        components.put("appearance", Map.of(
                "provider", "HEAD",
                "fallbackHeadSource", outcome.icon().source(),
                "fallbackHeadValue", outcome.icon().value()));
        Object release = outcome.extensions().get("release");
        if (release != null) components.put("release", io.github.salyvn.omnipet.core.domain.RawNodeValues.mutableCopy(release));
        return new PetInstance(
                outcome.petInstanceId(),
                outcome.definitionId(),
                outcome.definitionRevision(),
                components,
                Map.of());
    }
}
