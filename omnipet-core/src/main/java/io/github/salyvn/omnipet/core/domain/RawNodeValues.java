package io.github.salyvn.omnipet.core.domain;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RawNodeValues {
    private RawNodeValues() {}

    public static Map<String, Object> immutableMap(Map<String, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, immutableCopy(value)));
        return Collections.unmodifiableMap(copy);
    }

    public static Map<String, Object> mutableMap(Map<String, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, mutableCopy(value)));
        return copy;
    }

    public static Object mutableCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), mutableCopy(nested)));
            return copy;
        }
        if (value instanceof List<?> list) {
            ArrayList<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(mutableCopy(item)));
            return copy;
        }
        return value;
    }

    private static Object immutableCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), immutableCopy(nested)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            ArrayList<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(immutableCopy(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    public static void rejectNonFinite(Object value, String path) {
        if (value instanceof Double number && !Double.isFinite(number)) {
            throw new IllegalArgumentException(path + " must be finite");
        }
        if (value instanceof Float number && !Float.isFinite(number)) {
            throw new IllegalArgumentException(path + " must be finite");
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> rejectNonFinite(nested, path + "." + key));
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                rejectNonFinite(list.get(index), path + "[" + index + "]");
            }
        }
    }

    public static byte[] semanticBytes(Object value) {
        StringBuilder output = new StringBuilder();
        appendCanonical(output, value);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendCanonical(StringBuilder output, Object value) {
        if (value instanceof Map<?, ?> map) {
            output.append('{');
            map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> {
                        appendCanonical(output, String.valueOf(entry.getKey()));
                        appendCanonical(output, entry.getValue());
                    });
            output.append('}');
        } else if (value instanceof List<?> list) {
            output.append('[');
            list.forEach(item -> appendCanonical(output, item));
            output.append(']');
        } else {
            output.append(value == null ? "null" : value.getClass().getName() + ':' + value).append(';');
        }
    }
}
