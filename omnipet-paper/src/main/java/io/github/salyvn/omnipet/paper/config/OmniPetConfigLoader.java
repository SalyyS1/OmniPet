package io.github.salyvn.omnipet.paper.config;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.github.salyvn.omnipet.core.persistence.YamlDocuments;
import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.core.studio.input.CompiledFormula;
import io.github.salyvn.omnipet.core.studio.input.StudioFormulaValidator;
import io.github.salyvn.omnipet.paper.runtime.PaperRuntimeSettings;

/** Aggregate loader that preserves the strict Phase 4 storage contract. */
public final class OmniPetConfigLoader {
    private static final Set<String> ROOT_KEYS =
            Set.of("storage", "runtime", "progression", "items", "integrations", "gui");

    public LoadResult load(Path file) throws IOException {
        return load(file, warning -> {});
    }

    /**
     * Reads the config, routing lenient {@code gui:} warnings to {@code warnings}. The sink follows
     * the {@code messages.yml} precedent: a typo in a display setting degrades that setting alone and
     * tells the operator, rather than failing startup.
     */
    public LoadResult load(Path file, Consumer<String> warnings) throws IOException {
        if (file == null || !Files.isRegularFile(file)) throw new IOException("OmniPet config is not a regular file: " + file);
        return parse(Files.readString(file, StandardCharsets.UTF_8), warnings);
    }

    public LoadResult parse(String yaml) {
        return parse(yaml, warning -> {});
    }

    public LoadResult parse(String yaml, Consumer<String> warnings) {
        Map<String, Object> root = YamlDocuments.readMap(yaml);
        boolean legacy = root.keySet().stream().allMatch(Set.of("globalMaxSlots", "slotPermission")::contains);
        if (!legacy) rejectUnknown(root, ROOT_KEYS, "config");
        Phase4PaperConfigLoader.LoadResult storage = legacy
                ? new Phase4PaperConfigLoader().parseWithReport(yaml)
                : new Phase4PaperConfigLoader().parseWithReport(YamlDocuments.writeMap(Map.of(
                        "storage", requiredMap(root.get("storage"), "storage"))));
        PaperRuntimeSettings runtime = runtime(optionalMap(root.get("runtime"), "runtime"));
        ProgressionConfig progression = progression(optionalMap(root.get("progression"), "progression"));
        OmniPetConfig.CultivationItems items = items(optionalMap(root.get("items"), "items"));
        GuiConfig gui = new GuiConfigLoader().parse(root.get("gui"), warnings);
        return new LoadResult(new OmniPetConfig(storage.config(), runtime, progression, items, gui),
                legacy || storage.migratedLegacy());
    }

    public String encode(OmniPetConfig config) {
        Map<String, Object> storageRoot = YamlDocuments.readMap(new Phase4PaperConfigLoader().encode(config.storage()));
        LinkedHashMap<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("initialDelayTicks", config.runtime().initialDelayTicks());
        runtime.put("periodTicks", config.runtime().periodTicks());
        runtime.put("maximumOwnersPerTick", config.runtime().maximumOwnersPerTick());
        runtime.put("maximumPetsPerOwner", config.runtime().maximumPetsPerOwner());
        LinkedHashMap<String, Object> progression = new LinkedHashMap<>();
        progression.put("maxLevel", config.progression().maxLevel());
        progression.put("maxStamina", config.progression().maxStamina());
        progression.put("staminaRegenPerSecond", config.progression().staminaRegenPerSecond());
        progression.put("defaultExperienceFormula", "100 + level * 25 + evolution * 100");
        progression.put("formulaSamples", config.progression().formulaSamples());
        progression.put("overflowPolicy", config.progression().overflowPolicy().name());
        LinkedHashMap<String, Object> items = new LinkedHashMap<>();
        items.put("experienceCandy", Map.of(
                "material", config.cultivationItems().experienceMaterial(),
                "experience", config.cultivationItems().experienceAmount()));
        items.put("breakthroughStone", Map.of(
                "material", config.cultivationItems().breakthroughMaterial(),
                "requiredLevel", config.cultivationItems().breakthroughRequiredLevel(),
                "requiredEvolution", config.cultivationItems().breakthroughRequiredEvolution()));
        LinkedHashMap<String, Object> integrations = new LinkedHashMap<>();
        integrations.put("mythicLib", "1.7.1-SNAPSHOT build 106");
        integrations.put("mythicMobs", "5.9.0");
        integrations.put("modelEngine", "R4.0.9");
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("storage", storageRoot.get("storage"));
        root.put("runtime", runtime);
        root.put("progression", progression);
        root.put("items", items);
        root.put("integrations", integrations);
        // Serialised, not omitted: encode() is what a legacy migration writes back, so leaving gui out
        // would silently discard the operator's display and feedback settings on upgrade.
        root.put("gui", new GuiConfigLoader().encode(config.gui()));
        return YamlDocuments.writeMap(root);
    }

    private static PaperRuntimeSettings runtime(Map<String, Object> values) {
        rejectUnknown(values, Set.of("initialDelayTicks", "periodTicks", "maximumOwnersPerTick", "maximumPetsPerOwner"),
                "runtime");
        return new PaperRuntimeSettings(
                longValue(values.getOrDefault("initialDelayTicks", 1), "runtime.initialDelayTicks"),
                longValue(values.getOrDefault("periodTicks", 1), "runtime.periodTicks"),
                integer(values.getOrDefault("maximumOwnersPerTick", 64), "runtime.maximumOwnersPerTick"),
                integer(values.getOrDefault("maximumPetsPerOwner", 10), "runtime.maximumPetsPerOwner"));
    }

    private static ProgressionConfig progression(Map<String, Object> values) {
        rejectUnknown(values, Set.of("maxLevel", "maxStamina", "staminaRegenPerSecond",
                "defaultExperienceFormula", "formulaSamples", "overflowPolicy"), "progression");
        String formula = text(values.getOrDefault(
                "defaultExperienceFormula", "100 + level * 25 + evolution * 100"),
                "progression.defaultExperienceFormula");
        Map<String, Double> samples = numberMap(optionalMap(values.get("formulaSamples"), "progression.formulaSamples"));
        if (samples.isEmpty()) samples = Map.of("level", 1.0, "rarity", 1.0, "quality", 0.5, "evolution", 0.0);
        CompiledFormula compiled = StudioFormulaValidator.compile(formula);
        double sample = compiled.evaluate(samples);
        if (!Double.isFinite(sample) || sample <= 0) throw new IllegalArgumentException("progression formula sample must be positive");
        ProgressionConfig.OverflowPolicy overflow = ProgressionConfig.OverflowPolicy.valueOf(
                text(values.getOrDefault("overflowPolicy", "CARRY"), "progression.overflowPolicy")
                        .toUpperCase(java.util.Locale.ROOT));
        return new ProgressionConfig(
                integer(values.getOrDefault("maxLevel", 100), "progression.maxLevel"),
                number(values.getOrDefault("maxStamina", 100), "progression.maxStamina"),
                number(values.getOrDefault("staminaRegenPerSecond", 1), "progression.staminaRegenPerSecond"),
                compiled::evaluate,
                samples,
                overflow);
    }

    private static OmniPetConfig.CultivationItems items(Map<String, Object> values) {
        rejectUnknown(values, Set.of("experienceCandy", "breakthroughStone"), "items");
        Map<String, Object> candy = optionalMap(values.get("experienceCandy"), "items.experienceCandy");
        Map<String, Object> stone = optionalMap(values.get("breakthroughStone"), "items.breakthroughStone");
        rejectUnknown(candy, Set.of("material", "experience"), "items.experienceCandy");
        rejectUnknown(stone, Set.of("material", "requiredLevel", "requiredEvolution"), "items.breakthroughStone");
        return new OmniPetConfig.CultivationItems(
                text(candy.getOrDefault("material", "EXPERIENCE_BOTTLE"), "items.experienceCandy.material"),
                number(candy.getOrDefault("experience", 100), "items.experienceCandy.experience"),
                text(stone.getOrDefault("material", "NETHER_STAR"), "items.breakthroughStone.material"),
                integer(stone.getOrDefault("requiredLevel", 10), "items.breakthroughStone.requiredLevel"),
                integer(stone.getOrDefault("requiredEvolution", 0), "items.breakthroughStone.requiredEvolution"));
    }

    private static Map<String, Double> numberMap(Map<String, Object> source) {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, number(value, "formulaSamples." + key)));
        return Map.copyOf(result);
    }

    private static Map<String, Object> requiredMap(Object value, String path) {
        if (value == null) throw new IllegalArgumentException(path + " is required");
        return optionalMap(value, path);
    }

    private static Map<String, Object> optionalMap(Object value, String path) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> source)) throw new IllegalArgumentException(path + " must be a map");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    private static void rejectUnknown(Map<String, Object> values, Set<String> allowed, String path) {
        values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst()
                .ifPresent(key -> { throw new IllegalArgumentException("unknown config key: " + path + "." + key); });
    }

    private static String text(Object value, String path) {
        if (!(value instanceof String result) || result.isBlank()) throw new IllegalArgumentException(path + " must be text");
        return result.trim();
    }

    private static double number(Object value, String path) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(path + " must be finite");
        }
        return number.doubleValue();
    }

    private static int integer(Object value, String path) {
        long result = longValue(value, path);
        try { return Math.toIntExact(result); }
        catch (ArithmeticException error) { throw new IllegalArgumentException(path + " is outside the integer range", error); }
    }

    private static long longValue(Object value, String path) {
        if (value instanceof Byte number) return number.longValue();
        if (value instanceof Short number) return number.longValue();
        if (value instanceof Integer number) return number.longValue();
        if (value instanceof Long number) return number;
        if (value instanceof BigInteger number) return number.longValueExact();
        throw new IllegalArgumentException(path + " must be an integer");
    }

    public record LoadResult(OmniPetConfig config, boolean migratedLegacy) {}
}
