package io.github.salyvn.omnipet.core.domain;

public record HeadIcon(String source, String value) {
    public HeadIcon {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("icon.head.source is required");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("icon.head.value is required");
    }
}
