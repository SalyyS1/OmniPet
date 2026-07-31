package io.github.salyvn.omnipet.paper.config;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.github.salyvn.omnipet.core.persistence.YamlDocuments;

/** Strict loader that validates a complete config before it can replace a live snapshot. */
public final class Phase4PaperConfigLoader {
    private static final Set<String> ROOT_KEYS = Set.of("storage");
    private static final Set<String> STORAGE_KEYS = Set.of("vault", "activeSlots");
    private static final Set<String> VAULT_KEYS = Set.of("baseCapacity", "maxCapacity", "legacyPermission");
    private static final Set<String> LEGACY_KEYS = Set.of("enabled", "template", "maxScan");
    private static final Set<String> LEGACY_ROOT_KEYS = Set.of("globalMaxSlots", "slotPermission");
    private static final int LEGACY_DEFAULT_MAX_SLOTS = 1000;
    private static final String LEGACY_DEFAULT_PERMISSION = "petstorage.slot.%s";

    public Phase4PaperConfig load(Path configFile) throws IOException {
        return loadWithReport(configFile).config();
    }

    public LoadResult loadWithReport(Path configFile) throws IOException {
        if (configFile == null) throw new IllegalArgumentException("config file is required");
        if (!Files.isRegularFile(configFile)) {
            throw new IOException("Phase 4 config is not a regular file: " + configFile);
        }
        return parseWithReport(Files.readString(configFile, StandardCharsets.UTF_8));
    }

    public Phase4PaperConfig parse(String yaml) {
        return parseWithReport(yaml).config();
    }

    public LoadResult parseWithReport(String yaml) {
        Map<String, Object> root;
        try {
            root = YamlDocuments.readMap(yaml);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid Phase 4 config YAML", exception);
        }
        if (root.keySet().stream().allMatch(LEGACY_ROOT_KEYS::contains)) return parseLegacy(root);
        requireKeys(root, ROOT_KEYS, "config");
        Map<String, Object> storage = requiredMap(root.get("storage"), "storage");
        requireKeys(storage, STORAGE_KEYS, "storage");
        Map<String, Object> vault = requiredMap(storage.get("vault"), "storage.vault");
        Map<String, Object> active = requiredMap(storage.get("activeSlots"), "storage.activeSlots");
        requireKeys(vault, VAULT_KEYS, "storage.vault");

        Map<String, Object> legacy = requiredMap(
                vault.get("legacyPermission"), "storage.vault.legacyPermission");
        requireKeys(legacy, LEGACY_KEYS, "storage.vault.legacyPermission");

        Phase4PaperConfig.LegacyPermission legacyConfig = new Phase4PaperConfig.LegacyPermission(
                bool(legacy.get("enabled"), "storage.vault.legacyPermission.enabled"),
                string(legacy.get("template"), "storage.vault.legacyPermission.template"),
                integer(legacy.get("maxScan"), "storage.vault.legacyPermission.maxScan"));
        Phase4PaperConfig.Vault vaultConfig = new Phase4PaperConfig.Vault(
                integer(vault.get("baseCapacity"), "storage.vault.baseCapacity"),
                integer(vault.get("maxCapacity"), "storage.vault.maxCapacity"),
                legacyConfig);
        Phase4PaperConfig.ActiveSlots activeConfig = new Phase4ActiveSlotConfigCodec().parse(active);
        return new LoadResult(new Phase4PaperConfig(vaultConfig, activeConfig), false);
    }

    public String encode(Phase4PaperConfig config) {
        if (config == null) throw new IllegalArgumentException("Phase 4 config is required");
        LinkedHashMap<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("enabled", config.vault().legacyPermission().enabled());
        legacy.put("template", config.vault().legacyPermission().template());
        legacy.put("maxScan", config.vault().legacyPermission().maxScan());
        LinkedHashMap<String, Object> vault = new LinkedHashMap<>();
        vault.put("baseCapacity", config.vault().baseCapacity());
        vault.put("maxCapacity", config.vault().maxCapacity());
        vault.put("legacyPermission", legacy);
        Map<String, Object> active = new Phase4ActiveSlotConfigCodec().encode(config.activeSlots());
        LinkedHashMap<String, Object> storage = new LinkedHashMap<>();
        storage.put("vault", vault);
        storage.put("activeSlots", active);
        return YamlDocuments.writeMap(Map.of("storage", storage));
    }

    private static LoadResult parseLegacy(Map<String, Object> root) {
        int legacyMax = root.containsKey("globalMaxSlots")
                ? integer(root.get("globalMaxSlots"), "globalMaxSlots")
                : LEGACY_DEFAULT_MAX_SLOTS;
        if (legacyMax < 0 || legacyMax > Phase4PaperConfig.MAX_LEGACY_PERMISSION_SCAN) {
            throw new IllegalArgumentException("globalMaxSlots is outside the supported migration range");
        }
        String template = root.containsKey("slotPermission")
                ? string(root.get("slotPermission"), "slotPermission")
                : LEGACY_DEFAULT_PERMISSION;
        int baseCapacity = Math.min(30, legacyMax);
        Phase4PaperConfig config = new Phase4PaperConfig(
                new Phase4PaperConfig.Vault(
                        baseCapacity,
                        legacyMax,
                        new Phase4PaperConfig.LegacyPermission(legacyMax > 0, template, legacyMax)),
                new Phase4PaperConfig.ActiveSlots(true, 1, 5));
        return new LoadResult(config, true);
    }

    private static Map<String, Object> requiredMap(Object value, String path) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException(path + " must be a map");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(path + " contains a non-text key");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static void requireKeys(Map<String, Object> values, Set<String> expected, String path) {
        for (String key : expected) {
            if (!values.containsKey(key)) throw new IllegalArgumentException(path + "." + key + " is required");
        }
        for (String key : values.keySet()) {
            if (!expected.contains(key)) throw new IllegalArgumentException("unknown config key: " + path + "." + key);
        }
    }

    private static boolean bool(Object value, String path) {
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException(path + " must be true or false");
        return result;
    }

    private static String string(Object value, String path) {
        if (!(value instanceof String result) || result.isBlank()) {
            throw new IllegalArgumentException(path + " must be non-blank text");
        }
        return result;
    }

    private static int integer(Object value, String path) {
        try {
            if (value instanceof Byte number) return number.intValue();
            if (value instanceof Short number) return number.intValue();
            if (value instanceof Integer number) return number;
            if (value instanceof Long number) return Math.toIntExact(number);
            if (value instanceof BigInteger number) return number.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(path + " is outside the integer range", exception);
        }
        throw new IllegalArgumentException(path + " must be an integer");
    }

    public record LoadResult(Phase4PaperConfig config, boolean migratedLegacy) {
        public LoadResult {
            if (config == null) throw new IllegalArgumentException("Phase 4 config is required");
        }
    }
}
