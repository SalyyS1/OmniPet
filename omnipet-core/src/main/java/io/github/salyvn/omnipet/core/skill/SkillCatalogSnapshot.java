package io.github.salyvn.omnipet.core.skill;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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

    /**
     * Whether the provider knows this skill, ignoring case and surrounding space.
     *
     * <p>Case-insensitive because the two ends disagreed and neither is wrong. MythicMobs keys its skills by
     * the YAML node name exactly as written, so a skill headed {@code Fireball} is registered as
     * {@code Fireball} — but its own lookup trims and folds the caller's input, so casting it by any casing
     * works there. An exact match here therefore refused casts MythicMobs would have honoured, and the
     * refusal happened before the vendor was ever called, which is why the skill silently never fired.
     */
    public boolean contains(String skillId) {
        return canonical(skillId) != null;
    }

    /**
     * The provider's own spelling of this skill, or null when it does not know it.
     *
     * <p>Matching folds case; casting must not. The ID handed back to the provider is the one it registered,
     * so a definition written {@code fireball} reaches MythicMobs as {@code Fireball} rather than as
     * whatever the author happened to type.
     */
    public String canonical(String skillId) {
        if (skillId == null) return null;
        String needle = skillId.trim();
        if (needle.isEmpty()) return null;
        return byFoldedId().get(needle.toLowerCase(Locale.ROOT));
    }

    /**
     * Folded ID to the provider's spelling.
     *
     * <p>Rebuilt per call rather than cached in a field: a record's implicit equality and the immutability
     * of {@code skillIds} are worth more than the lookup, and a catalog is consulted once per cast rather
     * than per tick.
     *
     * <p>When two registrations differ only in case, the lexicographically smaller one wins. Insertion
     * order is not available to break the tie — the compact constructor stores {@code Set.copyOf}, which
     * does not keep it — so choosing by iteration order would pick a different skill on each restart. That
     * is worse than either choice: a cast that works today and fails after a reboot is far harder to
     * diagnose than one that is consistently wrong.
     */
    private Map<String, String> byFoldedId() {
        Map<String, String> folded = new LinkedHashMap<>();
        for (String id : skillIds) {
            folded.merge(id.toLowerCase(Locale.ROOT), id,
                    (existing, candidate) -> existing.compareTo(candidate) <= 0 ? existing : candidate);
        }
        return folded;
    }
}
