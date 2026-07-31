package io.github.salyvn.omnipet.core.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

final class IncubationYamlValues {
    private IncubationYamlValues() {}

    static Map<String, Object> map(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + " must be a map");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> result.put(String.valueOf(key), RawNodeValues.mutableCopy(nested)));
        return result;
    }

    static List<Map<String, Object>> mapList(Object value, String path) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(path + " must be a list");
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) result.add(map(list.get(index), path + "[" + index + "]"));
        return result;
    }

    static List<UUID> uuidList(Object value, String path) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(path + " must be a list");
        List<UUID> result = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) result.add(uuid(list.get(index), path + "[" + index + "]"));
        return List.copyOf(result);
    }

    static Map<String, Object> without(Map<String, Object> value, String... keys) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(value);
        for (String key : keys) result.remove(key);
        return RawNodeValues.immutableMap(result);
    }

    static String text(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(path + " must be text");
        return text;
    }

    static UUID uuid(Object value, String path) {
        try { return UUID.fromString(text(value, path)); }
        catch (RuntimeException error) { throw new IllegalArgumentException(path + " must be a UUID", error); }
    }

    static long integer(Object value, String path) {
        if (!(value instanceof Number number) || number.doubleValue() != number.longValue()) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        return number.longValue();
    }

    static double number(Object value, String path) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(path + " must be finite");
        }
        return number.doubleValue();
    }

    static <T extends Enum<T>> T enumValue(Object value, Class<T> type, String path) {
        try { return Enum.valueOf(type, text(value, path).toUpperCase(Locale.ROOT)); }
        catch (RuntimeException error) { throw new IllegalArgumentException(path + " is invalid", error); }
    }
}
