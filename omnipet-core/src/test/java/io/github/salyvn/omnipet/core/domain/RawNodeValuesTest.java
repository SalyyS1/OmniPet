package io.github.salyvn.omnipet.core.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RawNodeValuesTest {
    @Test
    void immutableMapFreezesNestedUnknownNodes() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("vendor", new LinkedHashMap<>(Map.of("values", List.of("a", "b"))));

        Map<String, Object> frozen = RawNodeValues.immutableMap(raw);
        @SuppressWarnings("unchecked")
        Map<String, Object> vendor = (Map<String, Object>) frozen.get("vendor");
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) vendor.get("values");

        assertThrows(UnsupportedOperationException.class, () -> vendor.put("new", true));
        assertThrows(UnsupportedOperationException.class, () -> values.add("c"));
    }

    @Test
    void semanticBytesIgnoreMapInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", Map.of("y", 2, "x", 1));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", Map.of("x", 1, "y", 2));
        second.put("b", 2);

        assertArrayEquals(RawNodeValues.semanticBytes(first), RawNodeValues.semanticBytes(second));
    }
}
