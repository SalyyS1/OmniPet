package io.github.salyvn.omnipet.core.studio;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;

/** Detached immutable state edited by Pet Studio before a repository transaction. */
public record StudioPetDraft(
        String id,
        Mode mode,
        long baseRevision,
        String baseSemanticHash,
        long registryGeneration,
        PetTier tier,
        HeadIcon icon,
        DisplayDefinition display,
        Map<String, Object> rawNode,
        List<StudioStat> stats,
        List<RarityBand> rarityBands,
        ProgressionFields progression,
        List<SkillReference> skills,
        Map<String, Object> behaviorExtensions,
        ReleasePolicy releasePolicy) {

    public enum Mode { CREATE, EDIT }

    public StudioPetDraft {
        if (mode == null) throw new IllegalArgumentException("studio mode is required");
        if (id != null) id = StableId.requireValid(id);
        if (mode == Mode.EDIT && id == null) throw new IllegalArgumentException("edit drafts require an immutable id");
        if (baseRevision < 0) throw new IllegalArgumentException("base revision cannot be negative");
        if (registryGeneration < 0) throw new IllegalArgumentException("registry generation cannot be negative");
        baseSemanticHash = baseSemanticHash == null ? "" : baseSemanticHash;
        if (tier == null) throw new IllegalArgumentException("tier is required");
        if (icon == null) throw new IllegalArgumentException("icon is required");
        if (display == null) throw new IllegalArgumentException("display is required");
        rawNode = RawNodeValues.immutableMap(rawNode == null ? Map.of() : rawNode);
        RawNodeValues.rejectNonFinite(rawNode, "rawNode");
        stats = List.copyOf(stats == null ? List.of() : stats);
        rarityBands = List.copyOf(rarityBands == null ? List.of() : rarityBands);
        skills = List.copyOf(skills == null ? List.of() : skills);
        behaviorExtensions = RawNodeValues.immutableMap(behaviorExtensions == null ? Map.of() : behaviorExtensions);
    }

    public static StudioPetDraft create(String id, long registryGeneration, PetTier tier, HeadIcon icon,
                                        DisplayDefinition display, Map<String, Object> rawNode) {
        return new StudioPetDraft(id, Mode.CREATE, 0, "", registryGeneration, tier, icon, display, rawNode,
                List.of(), List.of(), null, List.of(), Map.of(), null);
    }

    public static StudioPetDraft create(long registryGeneration, PetTier tier, HeadIcon icon,
                                        DisplayDefinition display, Map<String, Object> rawNode) {
        return create(null, registryGeneration, tier, icon, display, rawNode);
    }

    public static StudioPetDraft edit(PetDefinition definition, String semanticHash, long registryGeneration) {
        if (definition == null) throw new IllegalArgumentException("definition is required");
        StudioDraftHydrator.Fields fields = StudioDraftHydrator.read(definition.rawNode());
        return new StudioPetDraft(definition.id(), Mode.EDIT, definition.revision(), semanticHash,
                registryGeneration, definition.tier(), definition.icon(), definition.display(), definition.rawNode(),
                fields.stats(), fields.rarity(), fields.progression(), fields.skills(), fields.behavior(), fields.release());
    }

    public static StudioPetDraft from(PetDefinition definition, String semanticHash, long registryGeneration) {
        return edit(definition, semanticHash, registryGeneration);
    }

    public static String semanticHash(Map<String, ?> rawNode) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(RawNodeValues.semanticBytes(rawNode == null ? Map.of() : rawNode));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public String baseHash() {
        return baseSemanticHash;
    }

    public long generation() {
        return registryGeneration;
    }

    public StudioPetDraft withId(String newId) {
        String valid = StableId.requireValid(newId);
        if (id != null && !id.equals(valid)) {
            throw new IllegalStateException(mode == Mode.EDIT
                    ? "definition id is immutable in edit mode"
                    : "definition id can only be assigned once in create mode");
        }
        return id == null ? copy(valid, tier, icon, display, rawNode, stats, rarityBands, progression, skills,
                behaviorExtensions, releasePolicy) : this;
    }

    public StudioPetDraft withTier(PetTier value) {
        if (value == null) throw new IllegalArgumentException("tier is required");
        return copy(id, value, icon, display, StudioRawNodes.put(rawNode, value.name(), "classification", "tier"),
                stats, rarityBands, progression, skills, behaviorExtensions, releasePolicy);
    }

    public StudioPetDraft withIcon(HeadIcon value) {
        if (value == null) throw new IllegalArgumentException("icon is required");
        return copy(id, tier, value, display, StudioRawNodes.icon(rawNode, value), stats, rarityBands, progression,
                skills, behaviorExtensions, releasePolicy);
    }

    public StudioPetDraft withDisplay(DisplayDefinition value) {
        if (value == null) throw new IllegalArgumentException("display is required");
        return copy(id, tier, icon, value, StudioRawNodes.display(rawNode, value), stats, rarityBands, progression,
                skills, behaviorExtensions, releasePolicy);
    }

    public StudioPetDraft withStats(List<StudioStat> value) {
        List<StudioStat> immutable = StudioFieldExtensions.stats(
                stats, List.copyOf(value == null ? List.of() : value));
        return copy(id, tier, icon, display, StudioRawNodes.put(rawNode, StudioRawNodes.stats(immutable), "stats"),
                immutable, rarityBands, progression, skills, behaviorExtensions, releasePolicy);
    }

    public StudioPetDraft withRarityBands(List<RarityBand> value) {
        List<RarityBand> immutable = StudioFieldExtensions.rarity(
                rarityBands, List.copyOf(value == null ? List.of() : value));
        return copy(id, tier, icon, display,
                StudioRawNodes.put(rawNode, StudioRawNodes.rarityBands(immutable), "rarity", "bands"), stats,
                immutable, progression, skills, behaviorExtensions, releasePolicy);
    }

    public StudioPetDraft withProgression(ProgressionFields value) {
        ProgressionFields merged = StudioFieldExtensions.progression(progression, value);
        Map<String, Object> updated = merged == null
                ? StudioRawNodes.put(rawNode, null, "progression")
                : StudioRawNodes.put(rawNode, StudioRawNodes.progression(merged), "progression");
        return copy(id, tier, icon, display, updated, stats, rarityBands, merged, skills, behaviorExtensions,
                releasePolicy);
    }

    public StudioPetDraft withSkills(List<SkillReference> value) {
        List<SkillReference> immutable = StudioFieldExtensions.skills(
                skills, List.copyOf(value == null ? List.of() : value));
        return copy(id, tier, icon, display, StudioRawNodes.put(rawNode, StudioRawNodes.skills(immutable), "skills"),
                stats, rarityBands, progression, immutable, behaviorExtensions, releasePolicy);
    }

    public StudioPetDraft withBehaviorExtensions(Map<String, Object> value) {
        Map<String, Object> immutable = RawNodeValues.immutableMap(value == null ? Map.of() : value);
        return copy(id, tier, icon, display, StudioRawNodes.put(rawNode, immutable, "behavior"), stats,
                rarityBands, progression, skills, immutable, releasePolicy);
    }

    public StudioPetDraft withReleasePolicy(ReleasePolicy value) {
        ReleasePolicy merged = StudioFieldExtensions.release(releasePolicy, value);
        Map<String, Object> node = merged == null ? Map.of() : RawNodeValues.mutableMap(merged.extensions());
        if (merged != null) node.put("mode", merged.mode());
        return copy(id, tier, icon, display, StudioRawNodes.put(rawNode, node, "release"), stats, rarityBands,
                progression, skills, behaviorExtensions, merged);
    }

    public StudioPetDraft withRawValue(Object value, String... path) {
        return copy(id, tier, icon, display, StudioRawNodes.put(rawNode, value, path), stats, rarityBands,
                progression, skills, behaviorExtensions, releasePolicy);
    }

    public StudioPetDraft withRawValue(String dottedPath, Object value) {
        if (dottedPath == null) throw new IllegalArgumentException("raw node path is required");
        return withRawValue(value, dottedPath.split("\\.", -1));
    }

    private StudioPetDraft copy(String nextId, PetTier nextTier, HeadIcon nextIcon, DisplayDefinition nextDisplay,
                                Map<String, Object> nextRaw, List<StudioStat> nextStats,
                                List<RarityBand> nextRarity, ProgressionFields nextProgression,
                                List<SkillReference> nextSkills, Map<String, Object> nextBehavior,
                                ReleasePolicy nextRelease) {
        return new StudioPetDraft(nextId, mode, baseRevision, baseSemanticHash, registryGeneration, nextTier,
                nextIcon, nextDisplay, nextRaw, nextStats, nextRarity, nextProgression, nextSkills, nextBehavior,
                nextRelease);
    }
}
