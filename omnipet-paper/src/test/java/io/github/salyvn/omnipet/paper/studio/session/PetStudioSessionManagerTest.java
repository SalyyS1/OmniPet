package io.github.salyvn.omnipet.paper.studio.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PetStudioSessionManagerTest {
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    @Test
    void isolatesViewersAndTracksDirtyAndPendingState() {
        Fixture fixture = new Fixture();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        StudioViewToken firstToken = fixture.manager.open(first, "wolf", 4, "hash-a", 9).viewToken();
        StudioViewToken secondToken = fixture.manager.open(second, "fox", 2, "hash-b", 9).viewToken();

        assertTrue(fixture.manager.markDirty(firstToken));
        assertTrue(fixture.manager.setPendingInput(firstToken, true));

        PetStudioSession firstSession = fixture.manager.get(first).orElseThrow();
        PetStudioSession secondSession = fixture.manager.get(second).orElseThrow();
        assertTrue(firstSession.dirty());
        assertTrue(firstSession.pendingInput());
        assertFalse(secondSession.dirty());
        assertFalse(secondSession.pendingInput());
        assertTrue(fixture.manager.isCurrent(secondToken));
    }

    @Test
    void supersedingAViewerInvalidatesTheOldTokenAndCleansUpOnce() {
        Fixture fixture = new Fixture();
        UUID viewer = UUID.randomUUID();
        StudioViewToken oldToken = fixture.manager.open(viewer, "wolf", 1, "old", 3).viewToken();
        StudioViewToken newToken = fixture.manager.open(viewer, "wolf", 2, "new", 4).viewToken();

        assertFalse(fixture.manager.isCurrent(oldToken));
        assertTrue(fixture.manager.isCurrent(newToken));
        assertEquals(List.of(SessionCloseReason.SUPERSEDED), fixture.reasons());
    }

    @Test
    void rejectsStaleViewGenerationRevisionHashAndNonce() {
        Fixture fixture = new Fixture();
        UUID viewer = UUID.randomUUID();
        StudioViewToken token = fixture.manager.open(viewer, "wolf", 5, "base", 7).viewToken();

        assertFalse(fixture.manager.isCurrent(new StudioViewToken(token.sessionId(), viewer, token.viewNonce(), 8, 5, "base")));
        assertFalse(fixture.manager.isCurrent(new StudioViewToken(token.sessionId(), viewer, token.viewNonce(), 7, 6, "base")));
        assertFalse(fixture.manager.isCurrent(new StudioViewToken(token.sessionId(), viewer, token.viewNonce(), 7, 5, "other")));

        StudioViewToken next = fixture.manager.nextView(token);
        assertFalse(fixture.manager.isCurrent(token));
        assertTrue(fixture.manager.isCurrent(next));
    }

    @Test
    void expiresAndCleansUpQuitReloadAndDisableSessions() {
        Fixture fixture = new Fixture();
        UUID timedOut = UUID.randomUUID();
        UUID quit = UUID.randomUUID();
        fixture.manager.open(timedOut, "wolf", 0, "", 1);
        fixture.manager.open(quit, "fox", 0, "", 1);

        fixture.clock.advance(TIMEOUT);
        fixture.scheduler.runNextActive();
        assertTrue(fixture.manager.get(timedOut).isEmpty());
        fixture.manager.onQuit(quit);

        fixture.manager.open(UUID.randomUUID(), "bear", 0, "", 2);
        fixture.manager.onReload();
        fixture.manager.open(UUID.randomUUID(), "cat", 0, "", 3);
        fixture.manager.onDisable();

        assertEquals(List.of(
                SessionCloseReason.TIMEOUT,
                SessionCloseReason.QUIT,
                SessionCloseReason.RELOAD,
                SessionCloseReason.DISABLE), fixture.reasons());
    }

    @Test
    void refusesMutationWhenThreadGuardRejectsTheCaller() {
        Fixture fixture = new Fixture();
        fixture.mainThread = false;
        assertThrows(IllegalStateException.class,
                () -> fixture.manager.open(UUID.randomUUID(), "wolf", 0, "", 0));
    }

    @Test
    void rejectsExpiredTokensBeforeTheScheduledCallbackRuns() {
        Fixture fixture = new Fixture();
        UUID viewer = UUID.randomUUID();
        StudioViewToken token = fixture.manager.open(viewer, "wolf", 1, "hash", 2).viewToken();

        fixture.clock.advance(TIMEOUT);

        assertFalse(fixture.manager.markDirty(token));
        assertTrue(fixture.manager.get(viewer).isEmpty());
        assertEquals(List.of(SessionCloseReason.TIMEOUT), fixture.reasons());
    }

    @Test
    void canceledTimeoutCallbackCannotReplaceTheActiveHandle() {
        Fixture fixture = new Fixture();
        UUID viewer = UUID.randomUUID();
        StudioViewToken token = fixture.manager.open(viewer, "wolf", 1, "hash", 2).viewToken();
        assertTrue(fixture.manager.touch(token));

        fixture.scheduler.runEvenIfCanceled(0);
        assertTrue(fixture.manager.isCurrent(token));

        fixture.clock.advance(TIMEOUT);
        fixture.scheduler.runEvenIfCanceled(1);
        assertTrue(fixture.manager.get(viewer).isEmpty());
        assertEquals(List.of(SessionCloseReason.TIMEOUT), fixture.reasons());
    }

    private static final class Fixture {
        private final MutableClock clock = new MutableClock();
        private final TestScheduler scheduler = new TestScheduler();
        private final List<SessionClosed> closed = new ArrayList<>();
        private boolean mainThread = true;
        private final PetStudioSessionManager manager = new PetStudioSessionManager(
                clock,
                scheduler,
                () -> {
                    if (!mainThread) throw new IllegalStateException("not main thread");
                },
                TIMEOUT,
                closed::add);

        private List<SessionCloseReason> reasons() {
            return closed.stream().map(SessionClosed::reason).toList();
        }
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

    private static final class TestScheduler implements StudioScheduler {
        private final List<Task> tasks = new ArrayList<>();

        @Override
        public ScheduledHandle schedule(Duration delay, Runnable task) {
            Task scheduled = new Task(task);
            tasks.add(scheduled);
            return scheduled;
        }

        private void runNextActive() {
            tasks.stream().filter(task -> !task.canceled).findFirst().orElseThrow().run();
        }

        private void runEvenIfCanceled(int index) {
            tasks.get(index).action.run();
        }

        private static final class Task implements ScheduledHandle {
            private final Runnable action;
            private boolean canceled;

            private Task(Runnable action) {
                this.action = action;
            }

            @Override
            public void cancel() {
                canceled = true;
            }

            private void run() {
                if (!canceled) action.run();
            }
        }
    }
}
