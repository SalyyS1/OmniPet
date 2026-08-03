package io.github.salyvn.omnipet.core.incubation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.incubation.HatchRarityBand;
import io.github.salyvn.omnipet.core.studio.StatModifierType;
import io.github.salyvn.omnipet.core.studio.StatRange;
import io.github.salyvn.omnipet.core.studio.StudioStat;

final class PetIncubationProfileReader {
    /**
     * The band used when a definition authors no rarity profile at all.
     *
     * <p>Full quality range and weight one, so it is selected unconditionally and rolls the same
     * spread a single hand-authored band would. Named rather than blank because the ID is persisted on
     * the hatched pet and read back by the vault and progression views.
     */
    private static final HatchRarityBand IMPLICIT_BAND =
            new HatchRarityBand("standard", 0.0, 100.0, 1.0, 1.0, Map.of());

    PetIncubationProfile read(PetDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("pet definition is required");
        Map<String, Object> raw = definition.rawNode();
        return new PetIncubationProfile(definition, stats(raw.get("stats")), rarity(raw.get("rarity")));
    }

    /**
     * Reads {@code rarity.bands}, defaulting to a single full-range band when the node is absent.
     *
     * <p>Rarity is optional for authoring: the Studio starts a new draft with no bands and accepts a
     * save that leaves it that way. Treating that as a hard error made every such pet unhatchable —
     * the egg minted for it was delivered and accepted, then the hatch threw deep in the roller, so
     * the player was told the hatch was queued and nothing ever happened. An explicit but malformed
     * node is still rejected; only genuine absence defaults.
     */
    private static List<HatchRarityBand> rarity(Object rarityNode) {
        if (rarityNode == null) return List.of(IMPLICIT_BAND);
        Object bands = objectMap(rarityNode, "rarity").get("bands");
        if (bands == null) return List.of(IMPLICIT_BAND);
        List<HatchRarityBand> parsed = bands(bands);
        return parsed.isEmpty() ? List.of(IMPLICIT_BAND) : parsed;
    }

    private static List<StudioStat> stats(Object value) {
        if (value == null) return List.of();
        List<Map<String, Object>> nodes = listOfMaps(value, "stats");
        List<StudioStat> result = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            String path = "stats[" + index + "]";
            Object modifier = node.containsKey("modifierType") ? node.get("modifierType") : node.get("type");
            result.add(new StudioStat(
                    text(node.get("id"), path + ".id"),
                    enumValue(modifier, StatModifierType.class, path + ".modifierType"),
                    new StatRange(number(node.get("min"), path + ".min"), number(node.get("max"), path + ".max")),
                    extensions(node, "id", "modifierType", "type", "min", "max")));
        }
        return List.copyOf(result);
    }

    private static List<HatchRarityBand> bands(Object value) {
        List<Map<String, Object>> nodes = listOfMaps(value, "rarity.bands");
        List<HatchRarityBand> result = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            String path = "rarity.bands[" + index + "]";
            result.add(new HatchRarityBand(
                    text(node.get("id"), path + ".id"),
                    number(node.get("qualityMin"), path + ".qualityMin"),
                    number(node.get("qualityMax"), path + ".qualityMax"),
                    number(node.get("weight"), path + ".weight"),
                    node.get("hatchMultiplier") == null
                            ? 1.0
                            : number(node.get("hatchMultiplier"), path + ".hatchMultiplier"),
                    extensions(node, "id", "qualityMin", "qualityMax", "weight", "hatchMultiplier")));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> listOfMaps(Object value, String path) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(path + " must be a list");
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) result.add(objectMap(list.get(index), path + "[" + index + "]"));
        return result;
    }

    private static Map<String, Object> objectMap(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + " must be a map");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> result.put(String.valueOf(key), RawNodeValues.mutableCopy(nested)));
        return result;
    }

    private static Map<String, Object> extensions(Map<String, Object> node, String... keys) {
        Map<String, Object> result = RawNodeValues.mutableMap(node);
        for (String key : keys) result.remove(key);
        return RawNodeValues.immutableMap(result);
    }

    private static String text(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(path + " must be text");
        return text;
    }

    private static double number(Object value, String path) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(path + " must be finite");
        }
        return number.doubleValue();
    }

    private static <T extends Enum<T>> T enumValue(Object value, Class<T> type, String path) {
        try { return Enum.valueOf(type, text(value, path).toUpperCase(Locale.ROOT)); }
        catch (RuntimeException error) { throw new IllegalArgumentException(path + " is invalid", error); }
    }
}
