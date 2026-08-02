package io.github.salyvn.omnipet.paper.release;

import java.util.List;

public record ReleaseAdminResult(Status status, List<String> messages) {
    public ReleaseAdminResult {
        if (status == null) throw new IllegalArgumentException("release admin result status is required");
        messages = List.copyOf(messages == null ? List.of() : messages);
    }

    public enum Status { COMPLETED, FAILED }
}
