package io.github.salyvn.omnipet.paper.release;

public record ReleaseAdminParseResult(Status status, ReleaseAdminCommand command, String detail) {
    public ReleaseAdminParseResult {
        if (status == null) throw new IllegalArgumentException("release admin parse status is required");
        detail = detail == null ? "" : detail;
    }

    public enum Status { ACCEPTED, INVALID }
}
