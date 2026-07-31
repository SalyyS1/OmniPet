package io.github.salyvn.omnipet.core.domain.incubation;

final class IncubationValues {
    private IncubationValues() {}

    static String requireReference(String value, String field) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(field + " must be non-blank and whitespace-free");
        }
        return value;
    }

    static double requireFinite(double value, String field) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(field + " must be finite");
        return value;
    }
}
