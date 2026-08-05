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
import io.github.salyvn.omnipet.paper.render.PaperHeadRendererSettings;
import io.github.salyvn.omnipet.paper.runtime.PaperRuntimeSettings;

/** Aggregate loader that preserves the strict Phase 4 storage contract. */
public final class OmniPetConfigLoader {
    /**
     * Accepted root sections.
     *
     * <p>{@code integrations} is accepted but never read. It documented reference vendor builds and is
     * no longer written; it stays listed so an existing config that still carries the section keeps
     * loading instead of failing startup on an unknown key. Those versions now live in
     * {@code docs/integrations.md}.
     */
    private static final Set<String> ROOT_KEYS =
            Set.of("storage", "runtime", "render", "progression", "items", "integrations", "gui");
    private static final ItemAppearanceCodec APPEARANCES = new ItemAppearanceCodec();

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
        PaperHeadRendererSettings render = render(optionalMap(root.get("render"), "render"));
        Map<String, Object> progressionValues = optionalMap(root.get("progression"), "progression");
        ProgressionConfig progression = progression(progressionValues);
        Map<String, Object> itemValues = optionalMap(root.get("items"), "items");
        OmniPetConfig.CultivationItems items = items(itemValues);
        GuiConfig gui = new GuiConfigLoader().parse(root.get("gui"), warnings);
        return new LoadResult(new OmniPetConfig(storage.config(), runtime, render, progression,
                experienceFormulaSource(progressionValues), items,
                appearances(itemValues, warnings), gui),
                legacy || storage.migratedLegacy());
    }

    public String encode(OmniPetConfig config) {
        Map<String, Object> storageRoot = YamlDocuments.readMap(new Phase4PaperConfigLoader().encode(config.storage()));
        LinkedHashMap<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("initialDelayTicks", config.runtime().initialDelayTicks());
        runtime.put("periodTicks", config.runtime().periodTicks());
        runtime.put("maximumOwnersPerTick", config.runtime().maximumOwnersPerTick());
        runtime.put("maximumPetsPerOwner", config.runtime().maximumPetsPerOwner());
        runtime.put("maximumMicrosPerTick", config.runtime().maximumNanosPerTick() / 1_000L);
        // Written back for the same reason the EXP formula is: encode() is what a legacy migration
        // produces, and omitting a tuned section would reset it on upgrade.
        LinkedHashMap<String, Object> render = new LinkedHashMap<>();
        render.put("safetyDistance", config.render().safetyDistance());
        render.put("movementGain", config.render().movementGain());
        render.put("maximumVelocity", config.render().maximumVelocity());
        render.put("interpolationTicks", config.render().interpolationTicks());
        render.put("maximumLeanDegrees", config.render().maximumLeanDegrees());
        render.put("nameplates", config.render().nameplates());
        LinkedHashMap<String, Object> progression = new LinkedHashMap<>();
        progression.put("maxLevel", config.progression().maxLevel());
        progression.put("maxStamina", config.progression().maxStamina());
        progression.put("staminaRegenPerSecond", config.progression().staminaRegenPerSecond());
        progression.put("defaultExperienceFormula", config.experienceFormulaSource());
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
        encodeAppearances(items, config.appearances());
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("storage", storageRoot.get("storage"));
        root.put("runtime", runtime);
        root.put("render", render);
        root.put("progression", progression);
        root.put("items", items);
        // Serialised, not omitted: encode() is what a legacy migration writes back, so leaving gui out
        // would silently discard the operator's display and feedback settings on upgrade.
        root.put("gui", new GuiConfigLoader().encode(config.gui()));
        return YamlDocuments.writeMap(root);
    }

    /**
     * Writes each configured appearance back, omitting anything left at its default.
     *
     * <p>Only sections the operator actually set are written, so a migration does not litter the file
     * with empty blocks - and, as with every other section, an appearance they tuned is not silently
     * dropped on upgrade.
     */
    private static void encodeAppearances(
            Map<String, Object> items, OmniPetConfig.ItemAppearances appearances) {
        putAppearance(items, "egg", appearances.egg());
        putAppearance(items, "hatchReducer", appearances.hatchReducer());
        putAppearance(items, "instantHatch", appearances.instantHatch());
        nestAppearance(items, "experienceCandy", appearances.experienceCandy());
        nestAppearance(items, "breakthroughStone", appearances.breakthroughStone());
    }

    private static void putAppearance(
            Map<String, Object> items, String key, ItemAppearance appearance) {
        if (appearance.isDefault()) return;
        items.put(key, APPEARANCES.encode(appearance));
    }

    /** Candy and the stone already own an {@code items:} entry, so appearance nests inside it. */
    private static void nestAppearance(
            Map<String, Object> items, String key, ItemAppearance appearance) {
        if (appearance.isDefault()) return;
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (items.get(key) instanceof Map<?, ?> existing) {
            existing.forEach((name, value) -> merged.put(String.valueOf(name), value));
        }
        merged.put("appearance", APPEARANCES.encode(appearance));
        items.put(key, merged);
    }

    /**
     * Movement and lean tuning shared by both renderers.
     *
     * <p>Strict like {@code runtime:} rather than lenient like {@code gui:}: these values steer entities,
     * and a silently ignored typo would leave an operator convinced they had retuned something they had
     * not. An absent section is the built-in tuning.
     */
    private static PaperHeadRendererSettings render(Map<String, Object> values) {
        rejectUnknown(values, Set.of(
                "safetyDistance", "movementGain", "maximumVelocity", "interpolationTicks",
                "maximumLeanDegrees", "nameplates"), "render");
        PaperHeadRendererSettings defaults = PaperHeadRendererSettings.defaults();
        return new PaperHeadRendererSettings(
                number(values.getOrDefault("safetyDistance", defaults.safetyDistance()),
                        "render.safetyDistance"),
                number(values.getOrDefault("movementGain", defaults.movementGain()),
                        "render.movementGain"),
                number(values.getOrDefault("maximumVelocity", defaults.maximumVelocity()),
                        "render.maximumVelocity"),
                integer(values.getOrDefault("interpolationTicks", defaults.interpolationTicks()),
                        "render.interpolationTicks"),
                number(values.getOrDefault("maximumLeanDegrees", defaults.maximumLeanDegrees()),
                        "render.maximumLeanDegrees"),
                truthy(values.getOrDefault("nameplates", defaults.nameplates()), "render.nameplates"));
    }

    private static PaperRuntimeSettings runtime(Map<String, Object> values) {
        rejectUnknown(values, Set.of("initialDelayTicks", "periodTicks", "maximumOwnersPerTick",
                "maximumPetsPerOwner", "maximumMicrosPerTick"), "runtime");
        // Defaults come from the record rather than being repeated here: the two disagreed before, so an
        // operator who never wrote maximumPetsPerOwner got 64 while one who wrote the shipped 10 got 10.
        PaperRuntimeSettings defaults = PaperRuntimeSettings.defaults();
        return new PaperRuntimeSettings(
                longValue(values.getOrDefault("initialDelayTicks", defaults.initialDelayTicks()),
                        "runtime.initialDelayTicks"),
                longValue(values.getOrDefault("periodTicks", defaults.periodTicks()),
                        "runtime.periodTicks"),
                integer(values.getOrDefault("maximumOwnersPerTick", defaults.maximumOwnersPerTick()),
                        "runtime.maximumOwnersPerTick"),
                integer(values.getOrDefault("maximumPetsPerOwner", defaults.maximumPetsPerOwner()),
                        "runtime.maximumPetsPerOwner"),
                // Configured in microseconds: nanoseconds are finer than an operator can reason about,
                // and a tick is 50,000 of them.
                longValue(values.getOrDefault("maximumMicrosPerTick",
                        defaults.maximumNanosPerTick() / 1_000L), "runtime.maximumMicrosPerTick") * 1_000L);
    }

    /**
     * The formula exactly as the operator wrote it, or the default when the key is absent.
     *
     * <p>Read separately from {@link #progression} because compiling is one-way: the compiled result is
     * a lambda that cannot reproduce its own source, and {@link #encode} must write back the operator's
     * text rather than resetting a tuned formula to the default.
     */
    private static String experienceFormulaSource(Map<String, Object> values) {
        return text(values.getOrDefault(
                "defaultExperienceFormula", OmniPetConfig.DEFAULT_EXPERIENCE_FORMULA),
                "progression.defaultExperienceFormula");
    }

    private static ProgressionConfig progression(Map<String, Object> values) {
        rejectUnknown(values, Set.of("maxLevel", "maxStamina", "staminaRegenPerSecond",
                "defaultExperienceFormula", "formulaSamples", "overflowPolicy"), "progression");
        String formula = experienceFormulaSource(values);
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
        rejectUnknown(values, Set.of("experienceCandy", "breakthroughStone", "egg", "hatchReducer",
                "instantHatch"), "items");
        Map<String, Object> candy = optionalMap(values.get("experienceCandy"), "items.experienceCandy");
        Map<String, Object> stone = optionalMap(values.get("breakthroughStone"), "items.breakthroughStone");
        rejectUnknown(candy, Set.of("material", "experience", "appearance"), "items.experienceCandy");
        rejectUnknown(stone, Set.of("material", "requiredLevel", "requiredEvolution", "appearance"),
                "items.breakthroughStone");
        return new OmniPetConfig.CultivationItems(
                text(candy.getOrDefault("material", "EXPERIENCE_BOTTLE"), "items.experienceCandy.material"),
                number(candy.getOrDefault("experience", 100), "items.experienceCandy.experience"),
                text(stone.getOrDefault("material", "NETHER_STAR"), "items.breakthroughStone.material"),
                integer(stone.getOrDefault("requiredLevel", 10), "items.breakthroughStone.requiredLevel"),
                integer(stone.getOrDefault("requiredEvolution", 0), "items.breakthroughStone.requiredEvolution"));
    }

    /**
     * Reads every item's optional {@code appearance:} block.
     *
     * <p>Lenient on purpose, unlike the surrounding strict {@code items:} values: a mistyped material
     * name is cosmetic and must degrade to the built-in look rather than stop the plugin loading.
     */
    private static OmniPetConfig.ItemAppearances appearances(
            Map<String, Object> items, Consumer<String> warnings) {
        return new OmniPetConfig.ItemAppearances(
                appearance(items.get("egg"), "items.egg", warnings),
                appearance(nested(items.get("experienceCandy"), "appearance"),
                        "items.experienceCandy.appearance", warnings),
                appearance(nested(items.get("breakthroughStone"), "appearance"),
                        "items.breakthroughStone.appearance", warnings),
                appearance(items.get("hatchReducer"), "items.hatchReducer", warnings),
                appearance(items.get("instantHatch"), "items.instantHatch", warnings));
    }

    private static ItemAppearance appearance(Object node, String path, Consumer<String> warnings) {
        try {
            return APPEARANCES.parse(node, path, warnings);
        } catch (IllegalArgumentException rejected) {
            warnings.accept(path + " was rejected (" + rejected.getMessage()
                    + "); using the built-in appearance");
            return ItemAppearance.defaults();
        }
    }

    /** The named child of a map-valued node, or null when either is absent. */
    private static Object nested(Object node, String child) {
        return node instanceof Map<?, ?> map ? map.get(child) : null;
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

    /**
     * A strict boolean.
     *
     * <p>Only a real boolean, not the string "true". This section is validated strictly and a value that
     * looks like a boolean but is not one should say so rather than being silently coerced — an operator who
     * wrote {@code nameplates: "no"} means to turn them off, and treating that as truthy would be the
     * opposite of what they asked for.
     */
    private static boolean truthy(Object value, String path) {
        if (value instanceof Boolean flag) return flag;
        throw new IllegalArgumentException(path + " must be true or false");
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
