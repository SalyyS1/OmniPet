package io.github.salyvn.omnipet.core.domain;

public record DisplayDefinition(Provider provider, String model) {
    public DisplayDefinition {
        if (provider == null) throw new IllegalArgumentException("display.provider is required");
        if (provider == Provider.MODELENGINE && (model == null || model.isBlank())) {
            throw new IllegalArgumentException("display.model is required for MODELENGINE");
        }
    }

    public enum Provider {
        HEAD,
        MODELENGINE
    }
}
