package io.github.salyvn.omnipet.core.studio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON reader for vendor payloads that must not accept YAML. */
final class StrictJsonDocuments {
    private final String source;
    private int offset;

    private StrictJsonDocuments(String source) {
        this.source = source;
    }

    static Map<String, Object> readObject(String source) {
        if (source == null) throw new IllegalArgumentException("JSON is required");
        StrictJsonDocuments reader = new StrictJsonDocuments(source);
        Object value = reader.value();
        reader.whitespace();
        if (reader.offset != source.length()) throw reader.error("unexpected trailing data");
        if (!(value instanceof Map<?, ?> map)) throw reader.error("JSON root must be an object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    private Object value() {
        whitespace();
        if (offset >= source.length()) throw error("value is required");
        return switch (source.charAt(offset)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        whitespace();
        if (take('}')) return result;
        while (true) {
            whitespace();
            if (offset >= source.length() || source.charAt(offset) != '"') throw error("object key must be a string");
            String key = string();
            if (result.containsKey(key)) throw error("duplicate object key: " + key);
            whitespace();
            expect(':');
            result.put(key, value());
            whitespace();
            if (take('}')) return result;
            expect(',');
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> result = new ArrayList<>();
        whitespace();
        if (take(']')) return result;
        while (true) {
            result.add(value());
            whitespace();
            if (take(']')) return result;
            expect(',');
        }
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (offset < source.length()) {
            char character = source.charAt(offset++);
            if (character == '"') return result.toString();
            if (character < 0x20) throw error("control character in string");
            if (character != '\\') {
                result.append(character);
                continue;
            }
            if (offset >= source.length()) throw error("unterminated escape");
            char escaped = source.charAt(offset++);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> result.append(unicode());
                default -> throw error("invalid escape");
            }
        }
        throw error("unterminated string");
    }

    private char unicode() {
        if (offset + 4 > source.length()) throw error("short unicode escape");
        int value = 0;
        for (int index = 0; index < 4; index++) {
            int digit = Character.digit(source.charAt(offset++), 16);
            if (digit < 0) throw error("invalid unicode escape");
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private Object number() {
        int start = offset;
        if (take('-')) {
            if (offset >= source.length()) throw error("invalid number");
        }
        if (take('0')) {
            if (offset < source.length() && Character.isDigit(source.charAt(offset))) throw error("leading zero");
        } else {
            digits();
        }
        if (take('.')) digits();
        if (offset < source.length() && (source.charAt(offset) == 'e' || source.charAt(offset) == 'E')) {
            offset++;
            if (offset < source.length() && (source.charAt(offset) == '+' || source.charAt(offset) == '-')) offset++;
            digits();
        }
        String token = source.substring(start, offset);
        try {
            double value = Double.parseDouble(token);
            if (!Double.isFinite(value)) throw error("number must be finite");
            return value;
        } catch (NumberFormatException error) {
            throw error("invalid number");
        }
    }

    private void digits() {
        int start = offset;
        while (offset < source.length() && Character.isDigit(source.charAt(offset))) offset++;
        if (start == offset) throw error("digits are required");
    }

    private Object literal(String token, Object value) {
        if (!source.startsWith(token, offset)) throw error("invalid literal");
        offset += token.length();
        return value;
    }

    private void whitespace() {
        while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) offset++;
    }

    private boolean take(char expected) {
        if (offset < source.length() && source.charAt(offset) == expected) {
            offset++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!take(expected)) throw error("expected '" + expected + "'");
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at offset " + offset);
    }
}
