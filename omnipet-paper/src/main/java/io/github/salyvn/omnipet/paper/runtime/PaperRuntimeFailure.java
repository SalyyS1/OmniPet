package io.github.salyvn.omnipet.paper.runtime;

import java.util.UUID;

public record PaperRuntimeFailure(UUID ownerId, UUID petInstanceId, Stage stage, String detail) {
    public PaperRuntimeFailure {
        if (stage == null) throw new IllegalArgumentException("runtime failure stage is required");
        detail = detail == null || detail.isBlank() ? "unknown runtime failure" : detail;
    }

    public enum Stage {
        SNAPSHOT,
        OWNER_POSE,
        MOVEMENT,
        PARTICLE,
        RECONCILIATION,
        CLEANUP,
        COORDINATOR
    }
}
