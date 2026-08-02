package io.github.salyvn.omnipet.core.skill;

public record SkillProviderHealth(Status status, String detail) {
    public SkillProviderHealth {
        if (status == null) throw new IllegalArgumentException("skill provider health status is required");
        detail = detail == null ? "" : detail;
    }

    public boolean available() {
        return status == Status.AVAILABLE || status == Status.DEGRADED;
    }

    public enum Status {
        AVAILABLE,
        DEGRADED,
        UNAVAILABLE,
        INCOMPATIBLE,
        QUARANTINED
    }
}
