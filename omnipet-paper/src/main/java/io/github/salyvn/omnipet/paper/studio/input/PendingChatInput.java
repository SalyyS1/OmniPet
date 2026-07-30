package io.github.salyvn.omnipet.paper.studio.input;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import io.github.salyvn.omnipet.paper.studio.session.StudioViewToken;

public final class PendingChatInput<T> {
    private final UUID inputId;
    private final UUID playerId;
    private final StudioViewToken viewToken;
    private final String fieldPath;
    private final Instant expiresAt;
    private final ChatInputParser<T> parser;
    private final Consumer<? super T> accepted;
    private final Consumer<? super ChatInputFailure> rejected;

    public PendingChatInput(
            UUID inputId,
            UUID playerId,
            StudioViewToken viewToken,
            String fieldPath,
            Instant expiresAt,
            ChatInputParser<T> parser,
            Consumer<? super T> accepted,
            Consumer<? super ChatInputFailure> rejected) {
        this.inputId = Objects.requireNonNull(inputId, "inputId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.viewToken = Objects.requireNonNull(viewToken, "viewToken");
        if (!playerId.equals(viewToken.viewerId())) throw new IllegalArgumentException("player and view token differ");
        this.fieldPath = requireText(fieldPath, "fieldPath");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.accepted = Objects.requireNonNull(accepted, "accepted");
        this.rejected = Objects.requireNonNull(rejected, "rejected");
    }

    public UUID inputId() {
        return inputId;
    }

    public UUID playerId() {
        return playerId;
    }

    public StudioViewToken viewToken() {
        return viewToken;
    }

    public String fieldPath() {
        return fieldPath;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    T parse(String rawInput) {
        return parser.parse(rawInput);
    }

    void accept(T value) {
        accepted.accept(value);
    }

    void reject(ChatInputFailure failure) {
        rejected.accept(failure);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
