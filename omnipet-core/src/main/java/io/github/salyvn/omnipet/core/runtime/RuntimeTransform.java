package io.github.salyvn.omnipet.core.runtime;

import java.util.Objects;

public record RuntimeTransform(RuntimeVector position, float yaw, float pitch, double scale) {
    public RuntimeTransform {
        position = Objects.requireNonNull(position, "runtime transform position");
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("runtime transform rotation must be finite");
        }
        if (!Double.isFinite(scale) || scale <= 0 || scale > 64) {
            throw new IllegalArgumentException("runtime transform scale is outside the supported range");
        }
    }
}
