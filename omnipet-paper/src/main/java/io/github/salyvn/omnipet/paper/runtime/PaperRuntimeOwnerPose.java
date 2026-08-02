package io.github.salyvn.omnipet.paper.runtime;

import java.util.Objects;

import io.github.salyvn.omnipet.core.runtime.RuntimeVector;

/** Immutable main-thread view of an owner used by the dependency-neutral movement controller. */
public record PaperRuntimeOwnerPose(
        RuntimeVector position,
        RuntimeVector forward,
        float yaw,
        float pitch,
        boolean visible) {
    public PaperRuntimeOwnerPose {
        position = Objects.requireNonNull(position, "runtime owner position");
        forward = Objects.requireNonNull(forward, "runtime owner forward");
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("runtime owner rotation must be finite");
        }
    }
}
