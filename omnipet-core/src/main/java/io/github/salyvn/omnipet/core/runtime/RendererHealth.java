package io.github.salyvn.omnipet.core.runtime;

public record RendererHealth(Status status, String detail) {
    public RendererHealth {
        if (status == null) throw new IllegalArgumentException("renderer health status is required");
        detail = detail == null ? "" : detail;
    }

    public boolean available() {
        return status == Status.AVAILABLE || status == Status.DEGRADED;
    }

    public enum Status {
        AVAILABLE,
        DEGRADED,
        UNAVAILABLE,
        QUARANTINED
    }
}
