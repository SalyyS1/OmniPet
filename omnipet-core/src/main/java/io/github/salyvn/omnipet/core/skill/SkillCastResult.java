package io.github.salyvn.omnipet.core.skill;

public record SkillCastResult(Status status, String detail) {
    public SkillCastResult {
        if (status == null) throw new IllegalArgumentException("skill cast status is required");
        detail = detail == null ? "" : detail;
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        REJECTED,
        INVALID_TARGET,
        FAILED
    }
}
