package io.github.salyvn.omnipet.core.skill;

import java.util.Set;

public record SkillCatalogSnapshot(long epoch, SkillProviderHealth health, Set<String> skillIds) {
    public SkillCatalogSnapshot {
        if (epoch < 0) throw new IllegalArgumentException("skill catalog epoch cannot be negative");
        if (health == null) throw new IllegalArgumentException("skill catalog health is required");
        skillIds = Set.copyOf(skillIds == null ? Set.of() : skillIds);
        if (skillIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("skill catalog IDs must be non-blank");
        }
    }

    public boolean contains(String skillId) {
        return skillIds.contains(skillId);
    }
}
