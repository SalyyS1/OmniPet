package io.github.salyvn.omnipet.paper.studio.input;

public record ChatInputFailure(Reason reason, String message) {
    public ChatInputFailure {
        if (reason == null) throw new IllegalArgumentException("reason is required");
        message = message == null ? "" : message;
    }

    public enum Reason {
        CANCELED,
        EXPIRED,
        STALE_SESSION,
        REPLACED,
        INVALID_INPUT,
        SESSION_CLOSED
    }
}
