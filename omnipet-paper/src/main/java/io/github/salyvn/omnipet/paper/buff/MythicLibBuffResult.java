package io.github.salyvn.omnipet.paper.buff;

public record MythicLibBuffResult(Status status, int applied, int removed, int skipped, String detail) {
    public enum Status {
        APPLIED,
        UNAVAILABLE,
        OFF_THREAD,
        QUARANTINED
    }

    public boolean succeeded() {
        return status == Status.APPLIED;
    }
}
