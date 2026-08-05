package io.github.salyvn.omnipet.core.incubation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.HatchCandidate;
import io.github.salyvn.omnipet.core.domain.incubation.HatchRarityBand;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationOutcome;
import io.github.salyvn.omnipet.core.domain.incubation.RealizedStat;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.studio.StudioStat;

public final class DeterministicHatchRollService {
    public static final String ALGORITHM_ID = "splitmix64-v1";
    private final PetIncubationProfileReader profileReader = new PetIncubationProfileReader();

    public HatchRollResult roll(UUID incubationId, EggDefinition egg, RegistrySnapshot registry, long seed) {
        if (incubationId == null) throw new IllegalArgumentException("incubation id is required");
        if (egg == null) throw new IllegalArgumentException("egg definition is required");
        if (registry == null) throw new IllegalArgumentException("registry snapshot is required");

        List<CandidateProfile> candidates = validateCandidates(egg, registry.definitions());
        SplitMix64 random = new SplitMix64(seed);
        CandidateProfile selected = select(candidates, CandidateProfile::weight, random.nextUnitDouble());
        HatchRarityBand rarity = select(
                selected.profile().rarityBands(), HatchRarityBand::weight, random.nextUnitDouble());
        double quality = range(rarity.qualityMinimum(), rarity.qualityMaximum(), random.nextUnitDouble());
        List<RealizedStat> stats = realizeStats(selected.profile().stats(), quality, seed);
        long duration = duration(egg.baseActiveMillis(), rarity.hatchMultiplier());
        UUID petInstanceId = petInstanceId(incubationId);
        PetDefinition definition = selected.profile().definition();
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("registryGeneration", registry.generation());
        Object release = definition.rawNode().get("release");
        if (release != null) extensions.put("release", RawNodeValues.mutableCopy(release));
        return new HatchRollResult(new IncubationOutcome(
                petInstanceId,
                definition.id(),
                definition.revision(),
                definition.tier(),
                definition.icon(),
                Map.of(),
                rarity.id(),
                quality,
                seed,
                ALGORITHM_ID,
                stats,
                duration,
                extensions));
    }

    public static UUID petInstanceId(UUID incubationId) {
        if (incubationId == null) throw new IllegalArgumentException("incubation id is required");
        return UUID.nameUUIDFromBytes((incubationId + ":pet").getBytes(StandardCharsets.UTF_8));
    }

    private List<CandidateProfile> validateCandidates(EggDefinition egg, Map<String, PetDefinition> definitions) {
        List<CandidateProfile> result = new ArrayList<>(egg.candidates().size());
        for (HatchCandidate candidate : egg.candidates()) {
            PetDefinition definition = definitions.get(candidate.definitionId());
            if (definition == null) {
                throw new IllegalArgumentException("egg candidate does not exist: " + candidate.definitionId());
            }
            if (definition.tier() != egg.tier()) {
                throw new IllegalArgumentException("egg candidate tier does not match egg tier: " + candidate.definitionId());
            }
            result.add(new CandidateProfile(candidate.weight(), profileReader.read(definition)));
        }
        positiveFiniteTotal(result.stream().mapToDouble(CandidateProfile::weight).sum(), "candidate weights");
        return List.copyOf(result);
    }

    /**
     * Rolls the stats a pet definition grants, at a chosen quality.
     *
     * <p>Public so an admin grant can produce a pet with the same stats a hatch would. A granted pet
     * previously carried an empty stat list, so it rendered, levelled, and showed in the vault like any
     * other pet while giving its owner nothing — which is what an operator testing with
     * {@code /petadmin pet give} would see and reasonably report as "stats do not work".
     *
     * <p>Deterministic in the seed, so the same grant repeated with the same seed produces the same pet.
     *
     * @param definition the pet whose {@code stats} node to roll
     * @param quality 0-100; the position within each stat's range before per-stat jitter
     * @param seed mixed per stat, so two stats on one pet do not roll in lockstep
     */
    public static List<RealizedStat> rollStats(PetDefinition definition, double quality, long seed) {
        if (definition == null) throw new IllegalArgumentException("pet definition is required");
        if (!Double.isFinite(quality) || quality < 0 || quality > 100) {
            throw new IllegalArgumentException("stat quality must be between 0 and 100");
        }
        return realizeStats(new PetIncubationProfileReader().read(definition).stats(), quality, seed);
    }

    private static List<RealizedStat> realizeStats(List<StudioStat> definitions, double quality, long seed) {
        List<StudioStat> ordered = definitions.stream().sorted(Comparator.comparing(StudioStat::id)).toList();
        List<RealizedStat> result = new ArrayList<>(ordered.size());
        for (StudioStat stat : ordered) {
            SplitMix64 random = new SplitMix64(mix64(seed ^ stableHash(stat.id())));
            double adjustedQuality = clamp(quality + (random.nextUnitDouble() * 10.0) - 5.0, 0, 100);
            double ratio = adjustedQuality / 100.0;
            double minimum = stat.range().minimum();
            double maximum = stat.range().maximum();
            double value = clamp(minimum + ((maximum - minimum) * ratio), minimum, maximum);
            result.add(new RealizedStat(stat.id(), stat.modifierType(), value, stat.extensions()));
        }
        return List.copyOf(result);
    }

    private static long duration(long baseMillis, double multiplier) {
        double value = baseMillis * multiplier;
        if (!Double.isFinite(value) || value < 1 || value > EggDefinition.MAX_ACTIVE_MILLIS) {
            throw new IllegalArgumentException("resolved hatch duration is outside the supported range");
        }
        long rounded = Math.round(value);
        if (rounded < 1 || rounded > EggDefinition.MAX_ACTIVE_MILLIS) {
            throw new IllegalArgumentException("resolved hatch duration is outside the supported range");
        }
        return rounded;
    }

    private static <T> T select(List<T> values, Weight<T> weight, double unit) {
        double total = values.stream().mapToDouble(weight::value).sum();
        positiveFiniteTotal(total, "weighted selection");
        double target = unit * total;
        T fallback = null;
        for (T value : values) {
            double current = weight.value(value);
            if (current <= 0) continue;
            fallback = value;
            target -= current;
            if (target < 0) return value;
        }
        if (fallback == null) throw new IllegalArgumentException("weighted selection has no positive entry");
        return fallback;
    }

    private static void positiveFiniteTotal(double total, String field) {
        if (!Double.isFinite(total) || total <= 0) {
            throw new IllegalArgumentException(field + " must have a finite positive total");
        }
    }

    private static double range(double minimum, double maximum, double unit) {
        return minimum + ((maximum - minimum) * unit);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long stableHash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= current & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private record CandidateProfile(double weight, PetIncubationProfile profile) {}
    private interface Weight<T> { double value(T value); }

    private static final class SplitMix64 {
        private long state;

        private SplitMix64(long seed) { state = seed; }

        private long nextLong() {
            state += 0x9e3779b97f4a7c15L;
            return mix64(state);
        }

        private double nextUnitDouble() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }
    }
}
