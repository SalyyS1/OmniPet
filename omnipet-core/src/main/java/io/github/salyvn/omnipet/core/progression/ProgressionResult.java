package io.github.salyvn.omnipet.core.progression;

public record ProgressionResult(Status status, ProgressionState state, int levelsGained, String detail) {
    public ProgressionResult {
        if (status == null) throw new IllegalArgumentException("progression result status is required");
        if (state == null) throw new IllegalArgumentException("progression result state is required");
        if (levelsGained < 0) throw new IllegalArgumentException("progression levels gained cannot be negative");
        detail = detail == null ? "" : detail;
    }

    public boolean succeeded() {
        return status == Status.APPLIED || status == Status.ALREADY_MAX_LEVEL;
    }

    public enum Status {
        APPLIED,
        ALREADY_MAX_LEVEL,
        INVALID_AMOUNT,
        INVALID_FORMULA,
        REQUIREMENT_FAILED,
        INVALID_BREAKTHROUGH
    }
}
