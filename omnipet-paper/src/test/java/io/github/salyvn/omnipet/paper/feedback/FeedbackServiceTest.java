package io.github.salyvn.omnipet.paper.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.paper.config.GuiConfig;

/**
 * Feedback behavior asserted through a recording output rather than through the source text.
 *
 * <p>"Disabled produces no sound" and "a burst respects the interval" are only meaningful as counts of
 * actual playback. Asserting them by grepping for a {@code playSound} call would assert nothing, which
 * is why {@link FeedbackOutput} exists as a seam.
 */
class FeedbackServiceTest {
    private final Recording output = new Recording();
    private final AtomicLong now = new AtomicLong(1_000L);

    @Test
    void disabledFeedbackProducesNoOutputAtAll() {
        FeedbackService service = service(disabled());

        service.success(player(), FeedbackEvent.PET_ACTIVATED);
        service.failure(player(), FeedbackEvent.SLOT_PURCHASE_REJECTED);
        service.blocked(player(), FeedbackEvent.PET_REQUEST_IN_FLIGHT);
        service.progress(player(), FeedbackEvent.HATCH_QUEUED);

        assertEquals(0, output.sounds.size(), "the master switch must silence every category");
        assertEquals(0, output.actionBars.size());
    }

    @Test
    void eachCategoryPlaysItsOwnConfiguredSound() {
        FeedbackService service = service(enabled(Duration.ZERO));
        Player player = player();

        service.success(player, FeedbackEvent.PET_ACTIVATED);
        service.failure(player, FeedbackEvent.SLOT_PURCHASE_REJECTED);
        service.blocked(player, FeedbackEvent.PET_REQUEST_IN_FLIGHT);
        service.progress(player, FeedbackEvent.HATCH_QUEUED);

        assertEquals(4, output.sounds.size());
        assertEquals(List.of(
                        "ENTITY_EXPERIENCE_ORB_PICKUP",
                        "BLOCK_NOTE_BLOCK_BASS",
                        "BLOCK_CHEST_LOCKED",
                        "UI_BUTTON_CLICK"),
                output.sounds.stream().map(s -> s.sound().name()).toList());
    }

    @Test
    void aTwentyClickBurstIsRateLimitedToTheConfiguredFloor() {
        FeedbackService service = service(enabled(Duration.ofMillis(150)));
        Player player = player();

        // Twenty clicks inside one interval: the first is heard, the rest are suppressed.
        for (int click = 0; click < 20; click++) service.progress(player, FeedbackEvent.VAULT_VIEW_CHANGED);

        assertEquals(1, output.sounds.size(), "a fast-clicking player must not become an audible nuisance");

        // Once the floor has elapsed, the next click is heard again.
        now.addAndGet(150L);
        service.progress(player, FeedbackEvent.VAULT_VIEW_CHANGED);
        assertEquals(2, output.sounds.size());
    }

    @Test
    void suppressedClicksDoNotPushTheNextPermittedSoundFurtherAway() {
        FeedbackService service = service(enabled(Duration.ofMillis(100)));
        Player player = player();

        service.progress(player, FeedbackEvent.VAULT_VIEW_CHANGED);
        // Clicking throughout the window must not keep resetting the timer, or a player holding a
        // click would never hear anything again.
        for (int step = 0; step < 9; step++) {
            now.addAndGet(10L);
            service.progress(player, FeedbackEvent.VAULT_VIEW_CHANGED);
        }
        assertEquals(1, output.sounds.size());

        now.addAndGet(10L);
        service.progress(player, FeedbackEvent.VAULT_VIEW_CHANGED);
        assertEquals(2, output.sounds.size(), "the floor is measured from the last sound actually played");
    }

    @Test
    void theRateLimitIsPerPlayerSoOnePlayerCannotSilenceAnother() {
        FeedbackService service = service(enabled(Duration.ofMillis(150)));

        service.success(player(), FeedbackEvent.PET_ACTIVATED);
        service.success(player(), FeedbackEvent.PET_ACTIVATED);
        service.success(player(), FeedbackEvent.PET_ACTIVATED);

        assertEquals(3, output.sounds.size());
    }

    @Test
    void theRateLimiterIsEvictedOnQuitSoItCannotGrowForever() {
        FeedbackService service = service(enabled(Duration.ofMillis(150)));
        UUID playerId = UUID.randomUUID();

        service.success(player(playerId), FeedbackEvent.PET_ACTIVATED);
        assertEquals(1, service.trackedPlayers());

        service.release(playerId);

        assertEquals(0, service.trackedPlayers(), "a leaked entry per player lasts the server's lifetime");
    }

    @Test
    void anUnknownSoundNameSilencesOnlyThatCategoryAndWarns() {
        List<String> warnings = new ArrayList<>();
        GuiConfig.Feedback defaults = GuiConfig.Feedback.defaults();
        GuiConfig.Feedback broken = new GuiConfig.Feedback(
                true, true, Duration.ZERO,
                new GuiConfig.Cue("NO_SUCH_SOUND_ON_THIS_VERSION", 0.6f, 1.0f),
                defaults.failure(), defaults.blocked(), defaults.progress());

        FeedbackService service = service(FeedbackSettings.resolve(broken, warnings::add));
        Player player = player();
        service.success(player, FeedbackEvent.PET_ACTIVATED);
        service.failure(player, FeedbackEvent.SLOT_PURCHASE_REJECTED);

        assertEquals(1, output.sounds.size(), "only the broken category goes silent");
        assertEquals("BLOCK_NOTE_BLOCK_BASS", output.sounds.getFirst().sound().name());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("NO_SUCH_SOUND_ON_THIS_VERSION")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("success")), warnings.toString());
    }

    @Test
    void reloadSwapsSettingsLive() {
        FeedbackService service = service(enabled(Duration.ZERO));
        Player player = player();

        service.success(player, FeedbackEvent.PET_ACTIVATED);
        assertEquals(1, output.sounds.size());

        service.apply(disabled());
        service.success(player, FeedbackEvent.PET_ACTIVATED);

        assertEquals(1, output.sounds.size(), "gui.feedback.* must take effect on reload");
    }

    @Test
    void anActionBarOnlyFiresForEventsThatCarryAKeyAndOnlyWhenEnabled() {
        // Every current event is sound-only, so no event may emit an action bar. This test pins that:
        // when a key is added later, the author must decide deliberately rather than by accident.
        FeedbackService service = service(enabled(Duration.ZERO));
        for (FeedbackEvent event : FeedbackEvent.values()) service.emit(player(), event);

        assertEquals(FeedbackEvent.values().length, output.sounds.size());
        assertEquals(0, output.actionBars.size());
        assertTrue(java.util.Arrays.stream(FeedbackEvent.values()).allMatch(e -> e.actionBar() == null));
    }

    @Test
    void everyEventCarriesACategorySoNoCallSiteCanEmitNothing() {
        for (FeedbackEvent event : FeedbackEvent.values()) {
            assertFalse(event.category() == null, event + " must map to a category");
        }
    }

    @Test
    void aNullPlayerOrEventIsIgnoredRatherThanThrowingIntoAClickHandler() {
        FeedbackService service = service(enabled(Duration.ZERO));

        service.success(null, FeedbackEvent.PET_ACTIVATED);
        service.success(player(), null);
        service.emit(player(), null);
        service.release(null);

        assertEquals(0, output.sounds.size());
    }

    private FeedbackService service(FeedbackSettings settings) {
        return new FeedbackService(output, settings, now::get);
    }

    private static FeedbackSettings enabled(Duration interval) {
        GuiConfig.Feedback defaults = GuiConfig.Feedback.defaults();
        return FeedbackSettings.resolve(new GuiConfig.Feedback(
                true, true, interval,
                defaults.success(), defaults.failure(), defaults.blocked(), defaults.progress()),
                warning -> {});
    }

    private static FeedbackSettings disabled() {
        GuiConfig.Feedback defaults = GuiConfig.Feedback.defaults();
        return FeedbackSettings.resolve(new GuiConfig.Feedback(
                false, true, Duration.ZERO,
                defaults.success(), defaults.failure(), defaults.blocked(), defaults.progress()),
                warning -> {});
    }

    private static Player player() {
        return player(UUID.randomUUID());
    }

    /** A Player stand-in that answers only {@code getUniqueId}; this repo has no MockBukkit. */
    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "toString" -> "player:" + id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "feedback must not call Player." + method.getName());
                });
    }

    /** Records what would have been played, so counts can be asserted. */
    private static final class Recording implements FeedbackOutput {
        private final List<ResolvedSound> sounds = new ArrayList<>();
        private final List<Component> actionBars = new ArrayList<>();

        @Override
        public void sound(Player player, ResolvedSound sound) {
            sounds.add(sound);
        }

        @Override
        public void actionBar(Player player, Component text) {
            actionBars.add(text);
        }
    }
}
