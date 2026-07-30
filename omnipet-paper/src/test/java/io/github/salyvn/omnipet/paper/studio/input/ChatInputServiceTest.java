package io.github.salyvn.omnipet.paper.studio.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.paper.studio.session.StudioClock;
import io.github.salyvn.omnipet.paper.studio.session.StudioThreadGuard;
import io.github.salyvn.omnipet.paper.studio.session.StudioViewToken;

class ChatInputServiceTest {
    @Test
    void asyncCaptureOnlySchedulesAndValidInputMutatesOnMainThread() {
        Fixture fixture = new Fixture();
        List<String> accepted = new ArrayList<>();
        List<ChatInputFailure.Reason> failures = new ArrayList<>();
        PendingChatInput<String> input = fixture.service.await(
                fixture.player,
                fixture.token,
                "icon.head.value",
                Duration.ofSeconds(30),
                String::trim,
                accepted::add,
                failure -> failures.add(failure.reason()));

        fixture.service.captureAsync(fixture.player, "  texture-url  ");
        assertTrue(fixture.service.hasPending(fixture.player));
        assertTrue(accepted.isEmpty());

        fixture.dispatcher.runAll();
        assertEquals(List.of("texture-url"), accepted);
        assertTrue(failures.isEmpty());
        assertFalse(fixture.service.hasPending(fixture.player));
        assertEquals("icon.head.value", input.fieldPath());
    }

    @Test
    void cancelAndExpiryRemoveInputsWithoutCallingAccepted() {
        Fixture fixture = new Fixture();
        List<ChatInputFailure.Reason> failures = new ArrayList<>();
        fixture.service.await(fixture.player, fixture.token, "id", Duration.ofSeconds(5), String::trim,
                ignored -> { throw new AssertionError("expired input was accepted"); },
                failure -> failures.add(failure.reason()));
        fixture.service.captureAsync(fixture.player, "cancel");
        fixture.dispatcher.runAll();

        assertEquals(List.of(ChatInputFailure.Reason.CANCELED), failures);
        assertFalse(fixture.service.hasPending(fixture.player));

        fixture.service.await(fixture.player, fixture.token, "id", Duration.ofSeconds(5), String::trim,
                ignored -> { throw new AssertionError("expired input was accepted"); },
                failure -> failures.add(failure.reason()));
        fixture.clock.advance(Duration.ofSeconds(5));
        fixture.service.captureAsync(fixture.player, "late");
        fixture.dispatcher.runAll();
        assertEquals(List.of(ChatInputFailure.Reason.CANCELED, ChatInputFailure.Reason.EXPIRED), failures);
    }

    @Test
    void staleLateCallbackCannotMutateAfterCancelOrSessionInvalidation() {
        Fixture fixture = new Fixture();
        List<String> accepted = new ArrayList<>();
        List<ChatInputFailure.Reason> failures = new ArrayList<>();
        fixture.service.await(fixture.player, fixture.token, "id", Duration.ofSeconds(30), String::trim,
                accepted::add,
                failure -> failures.add(failure.reason()));
        fixture.service.captureAsync(fixture.player, "queued");
        fixture.service.cancel(fixture.player);
        fixture.dispatcher.runAll();
        assertTrue(accepted.isEmpty());
        assertEquals(List.of(ChatInputFailure.Reason.CANCELED), failures);

        fixture.service.await(fixture.player, fixture.token, "id", Duration.ofSeconds(30), String::trim,
                accepted::add,
                failure -> failures.add(failure.reason()));
        fixture.service.captureAsync(fixture.player, "stale");
        fixture.valid = false;
        fixture.dispatcher.runAll();
        assertTrue(accepted.isEmpty());
        assertEquals(List.of(ChatInputFailure.Reason.CANCELED, ChatInputFailure.Reason.STALE_SESSION), failures);
    }

    @Test
    void invalidTextKeepsPromptOpenForRetry() {
        Fixture fixture = new Fixture();
        List<String> accepted = new ArrayList<>();
        List<ChatInputFailure.Reason> failures = new ArrayList<>();
        fixture.service.await(fixture.player, fixture.token, "range", Duration.ofSeconds(30), StudioInputParsers::stableId,
                accepted::add,
                failure -> failures.add(failure.reason()));

        fixture.service.captureAsync(fixture.player, "bad id");
        fixture.dispatcher.runAll();
        assertTrue(fixture.service.hasPending(fixture.player));
        assertEquals(List.of(ChatInputFailure.Reason.INVALID_INPUT), failures);

        fixture.service.captureAsync(fixture.player, "good_id");
        fixture.dispatcher.runAll();
        assertEquals(List.of("good_id"), accepted);
    }

    @Test
    void replacementAndSessionCleanupRejectTheSupersededPrompts() {
        Fixture fixture = new Fixture();
        List<ChatInputFailure.Reason> failures = new ArrayList<>();
        fixture.service.await(fixture.player, fixture.token, "first", Duration.ofSeconds(30), String::trim,
                ignored -> {}, failure -> failures.add(failure.reason()));
        fixture.service.await(fixture.player, fixture.token, "second", Duration.ofSeconds(30), String::trim,
                ignored -> {}, failure -> failures.add(failure.reason()));

        assertEquals(List.of(ChatInputFailure.Reason.REPLACED), failures);
        assertEquals(1, fixture.service.cancelSession(fixture.token.sessionId()));
        assertEquals(List.of(
                ChatInputFailure.Reason.REPLACED,
                ChatInputFailure.Reason.SESSION_CLOSED), failures);
        assertFalse(fixture.service.hasPending(fixture.player));
    }

    @Test
    void queuedTextForReplacedPromptCannotMutateTheNewField() {
        Fixture fixture = new Fixture();
        List<String> firstAccepted = new ArrayList<>();
        List<String> secondAccepted = new ArrayList<>();
        fixture.service.await(fixture.player, fixture.token, "first", Duration.ofSeconds(30), String::trim,
                firstAccepted::add, ignored -> {});
        fixture.service.captureAsync(fixture.player, "value-for-first");

        fixture.service.await(fixture.player, fixture.token, "second", Duration.ofSeconds(30), String::trim,
                secondAccepted::add, ignored -> {});
        fixture.dispatcher.runAll();

        assertTrue(firstAccepted.isEmpty());
        assertTrue(secondAccepted.isEmpty());
        assertTrue(fixture.service.hasPending(fixture.player));

        fixture.service.captureAsync(fixture.player, "value-for-second");
        fixture.dispatcher.runAll();
        assertEquals(List.of("value-for-second"), secondAccepted);
    }

    private static final class Fixture {
        private final UUID player = UUID.randomUUID();
        private final StudioViewToken token = new StudioViewToken(UUID.randomUUID(), player, 1, 2, 3, "hash");
        private final MutableClock clock = new MutableClock();
        private final Queue<Runnable> queued = new ArrayDeque<>();
        private final QueueDispatcher dispatcher = new QueueDispatcher(queued);
        private boolean valid = true;
        private final ChatInputService service = new ChatInputService(
                clock,
                dispatcher,
                () -> {},
                ignored -> valid);
    }

    private static final class MutableClock implements StudioClock {
        private Instant now = Instant.parse("2026-07-30T10:00:00Z");

        @Override
        public Instant now() {
            return now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private static final class QueueDispatcher implements StudioMainThreadDispatcher {
        private final Queue<Runnable> queue;

        private QueueDispatcher(Queue<Runnable> queue) {
            this.queue = queue;
        }

        @Override
        public void execute(Runnable task) {
            queue.add(task);
        }

        private void runAll() {
            while (!queue.isEmpty()) queue.remove().run();
        }
    }
}
