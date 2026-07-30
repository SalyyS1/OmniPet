package io.github.salyvn.omnipet.paper.studio.input;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.github.salyvn.omnipet.paper.studio.session.StudioClock;
import io.github.salyvn.omnipet.paper.studio.session.StudioThreadGuard;
import io.github.salyvn.omnipet.paper.studio.session.StudioViewToken;

/** Captures async chat text and defers every state transition to the main thread. */
public final class ChatInputService {
    private final StudioClock clock;
    private final StudioMainThreadDispatcher dispatcher;
    private final StudioThreadGuard threadGuard;
    private final Predicate<StudioViewToken> tokenValidator;
    private final Supplier<UUID> inputIds;
    private final Map<UUID, PendingChatInput<?>> pending = new HashMap<>();
    private final Map<UUID, UUID> asyncCaptureIds = new ConcurrentHashMap<>();

    public ChatInputService(
            StudioClock clock,
            StudioMainThreadDispatcher dispatcher,
            StudioThreadGuard threadGuard,
            Predicate<StudioViewToken> tokenValidator) {
        this(clock, dispatcher, threadGuard, tokenValidator, UUID::randomUUID);
    }

    public ChatInputService(
            StudioClock clock,
            StudioMainThreadDispatcher dispatcher,
            StudioThreadGuard threadGuard,
            Predicate<StudioViewToken> tokenValidator,
            Supplier<UUID> inputIds) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.threadGuard = Objects.requireNonNull(threadGuard, "threadGuard");
        this.tokenValidator = Objects.requireNonNull(tokenValidator, "tokenValidator");
        this.inputIds = Objects.requireNonNull(inputIds, "inputIds");
    }

    public <T> PendingChatInput<T> await(
            UUID playerId,
            StudioViewToken token,
            String fieldPath,
            Duration timeout,
            ChatInputParser<T> parser,
            java.util.function.Consumer<? super T> accepted,
            java.util.function.Consumer<? super ChatInputFailure> rejected) {
        assertMainThread();
        if (!tokenValidator.test(token)) throw new IllegalStateException("studio view token is stale");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        PendingChatInput<T> input = new PendingChatInput<>(inputIds.get(), playerId, token, fieldPath,
                clock.now().plus(timeout), parser, accepted, rejected);
        PendingChatInput<?> replaced = pending.put(playerId, input);
        asyncCaptureIds.put(playerId, input.inputId());
        if (replaced != null) reject(replaced, ChatInputFailure.Reason.REPLACED, "input replaced");
        return input;
    }

    /** Safe entry point for an async chat listener after it has canceled broadcast. */
    public void captureAsync(UUID playerId, String rawMessage) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rawMessage, "rawMessage");
        UUID inputId = asyncCaptureIds.get(playerId);
        if (inputId != null) dispatcher.execute(() -> handleOnMainThread(playerId, inputId, rawMessage));
    }

    public boolean cancel(UUID playerId) {
        assertMainThread();
        PendingChatInput<?> input = pending.remove(playerId);
        if (input == null) return false;
        asyncCaptureIds.remove(playerId, input.inputId());
        reject(input, ChatInputFailure.Reason.CANCELED, "input canceled");
        return true;
    }

    public int cancelSession(UUID sessionId) {
        assertMainThread();
        int canceled = 0;
        for (PendingChatInput<?> input : pending.values().toArray(PendingChatInput[]::new)) {
            if (!input.viewToken().sessionId().equals(sessionId)) continue;
            pending.remove(input.playerId());
            asyncCaptureIds.remove(input.playerId(), input.inputId());
            reject(input, ChatInputFailure.Reason.SESSION_CLOSED, "studio session closed");
            canceled++;
        }
        return canceled;
    }

    public boolean hasPending(UUID playerId) {
        assertMainThread();
        return pending.containsKey(playerId);
    }

    public boolean hasAsyncCapture(UUID playerId) {
        return asyncCaptureIds.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    public boolean expire(UUID playerId, UUID inputId) {
        assertMainThread();
        PendingChatInput<?> input = pending.get(playerId);
        if (input == null || !input.inputId().equals(inputId)) return false;
        pending.remove(playerId);
        asyncCaptureIds.remove(playerId, input.inputId());
        reject(input, ChatInputFailure.Reason.EXPIRED, "input expired");
        return true;
    }

    private void handleOnMainThread(UUID playerId, UUID inputId, String rawMessage) {
        assertMainThread();
        PendingChatInput<?> input = pending.get(playerId);
        if (input == null || !input.inputId().equals(inputId)) return;
        if (!tokenValidator.test(input.viewToken())) {
            pending.remove(playerId);
            asyncCaptureIds.remove(playerId, input.inputId());
            reject(input, ChatInputFailure.Reason.STALE_SESSION, "studio session is stale");
            return;
        }

        Instant now = clock.now();
        if (!now.isBefore(input.expiresAt())) {
            pending.remove(playerId);
            asyncCaptureIds.remove(playerId, input.inputId());
            reject(input, ChatInputFailure.Reason.EXPIRED, "input expired");
            return;
        }
        if (rawMessage.trim().equalsIgnoreCase("cancel")) {
            pending.remove(playerId);
            asyncCaptureIds.remove(playerId, input.inputId());
            reject(input, ChatInputFailure.Reason.CANCELED, "input canceled");
            return;
        }

        acceptParsed(playerId, input, rawMessage);
    }

    private <T> void acceptParsed(UUID playerId, PendingChatInput<T> input, String rawMessage) {
        T value;
        try {
            value = input.parse(rawMessage);
        } catch (RuntimeException exception) {
            input.reject(new ChatInputFailure(ChatInputFailure.Reason.INVALID_INPUT,
                    exception.getMessage() == null ? "invalid input" : exception.getMessage()));
            return;
        }
        pending.remove(playerId);
        asyncCaptureIds.remove(playerId, input.inputId());
        input.accept(value);
    }

    private static void reject(PendingChatInput<?> input, ChatInputFailure.Reason reason, String message) {
        input.reject(new ChatInputFailure(reason, message));
    }

    private void assertMainThread() {
        threadGuard.assertMainThread();
    }
}
