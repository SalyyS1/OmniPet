package io.github.salyvn.omnipet.core.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public final class YamlDocuments {
    // SnakeYAML's Yaml instances are documented as not thread-safe, and the plugin loads player and
    // definition files from async tasks while the main thread loads too. One instance per thread keeps
    // the construction cost where it belongs — once per thread — instead of locking every read.
    private static final ThreadLocal<Yaml> READER = ThreadLocal.withInitial(YamlDocuments::reader);
    private static final ThreadLocal<Yaml> WRITER = ThreadLocal.withInitial(YamlDocuments::writer);

    private YamlDocuments() {}

    public static Map<String, Object> readMap(String yaml) {
        Object value = READER.get().load(yaml == null ? "" : yaml);
        if (value == null) return new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("YAML document must be a map");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> result.put(String.valueOf(key), RawNodeValues.mutableCopy(nested)));
        RawNodeValues.rejectNonFinite(result, "document");
        return result;
    }

    public static String writeMap(Map<String, Object> value) {
        return WRITER.get().dump(RawNodeValues.mutableCopy(value));
    }

    private static Yaml reader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        return new Yaml(new SafeConstructor(options));
    }

    private static Yaml writer() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setWidth(120);
        return new Yaml(options);
    }
}
