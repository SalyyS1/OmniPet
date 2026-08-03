package io.github.salyvn.omnipet.paper.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Structural guarantees about feedback that a behavioral test cannot reach.
 *
 * <p>Two of these are security properties, not style rules: a broadcast overload turns a spammable
 * menu click into an audible griefing vector, and a call site naming a {@code Sound} constant directly
 * escapes the operator's config and the rate limiter at once.
 */
class FeedbackCallSiteContractTest {
    private static final Path MAIN = Path.of("src/main/java/io/github/salyvn/omnipet/paper");

    @Test
    void playbackHappensInExactlyOnePlaceAndIsPlayerScoped() throws IOException {
        List<Path> withPlayback = sources()
                .filter(FeedbackCallSiteContractTest::callsPlaySound)
                .toList();

        assertEquals(1, withPlayback.size(),
                "playback must funnel through BukkitFeedbackOutput so the rate limit cannot be bypassed: "
                        + withPlayback);
        assertTrue(withPlayback.getFirst().endsWith("BukkitFeedbackOutput.java"), withPlayback.toString());
    }

    @Test
    void noCallSitePlaysToAWorldOrALocationInsteadOfAPlayer() throws IOException {
        // Player.playSound(Location, ...) and World.playSound reach everyone nearby. On a click a
        // player can repeat at will, that is a griefing vector rather than a feature.
        List<String> offenders = sources()
                .filter(path -> {
                    String source = read(path);
                    return source.contains("getWorld().playSound")
                            || source.contains("playSound(location")
                            || Pattern.compile("playSound\\(\\s*\\w+\\.getLocation\\(\\)").matcher(source).find();
                })
                .map(Path::toString)
                .toList();

        assertTrue(offenders.isEmpty(), "feedback must be audible to the acting player only: " + offenders);
    }

    @Test
    void noCallSiteNamesASoundConstantDirectly() throws IOException {
        // A hardcoded constant is invisible to the operator's config and skips the rate limiter.
        Pattern constant = Pattern.compile("\\bSound\\.[A-Z][A-Z0-9_]{3,}");
        List<String> offenders = sources()
                .filter(path -> !path.toString().replace('\\', '/').contains("/feedback/"))
                .filter(path -> constant.matcher(read(path)).find())
                .map(Path::toString)
                .toList();

        assertTrue(offenders.isEmpty(), "sounds belong to config, not to call sites: " + offenders);
    }

    @Test
    void soundNamesResolveOnceAtLoadRatherThanPerClick() throws IOException {
        String settings = read(MAIN.resolve("feedback/FeedbackSettings.java"));
        String service = read(MAIN.resolve("feedback/FeedbackService.java"));

        assertTrue(settings.contains("Sound.valueOf("), "resolution belongs to the settings load path");
        assertFalse(service.contains("Sound.valueOf("),
                "resolving per click would put a lookup and a possible throw inside a click handler");
    }

    @Test
    void theRateLimiterIsEvictedFromTheLifecycleListenerThatAlreadyHandlesQuitAndKick() throws IOException {
        String listener = read(MAIN.resolve("player/PlayerStorageLifecycleListener.java"));

        assertTrue(listener.contains("Feedback.release(ownerId)"),
                "an un-evicted limiter retains a UUID per player for the server's lifetime");
        // ownerQuit is the shared funnel for both PlayerQuitEvent and PlayerKickEvent.
        assertTrue(listener.contains("private void ownerQuit("), listener);
    }

    @Test
    void feedbackIsAdditiveAndNeverReplacesTheChatMessage() throws IOException {
        // Text is the accessible channel. Every attachment sits beside a sendMessage, so turning
        // feedback off never removes information.
        for (String file : List.of(
                "player/PlayerPetController.java",
                "player/PlayerSlotPurchaseController.java",
                "player/PlayerHatchController.java",
                "skill/PaperActiveSkillController.java")) {
            String source = read(MAIN.resolve(file));
            assertTrue(source.contains("Feedback."), file + " must emit feedback");
            assertTrue(source.contains("sendMessage") || source.contains("message(player"),
                    file + " must still send text");
        }
    }

    private static boolean callsPlaySound(Path path) {
        // Matches the call, not the prose in a comment explaining why the call is shaped this way.
        return Pattern.compile("^(?!\\s*\\*).*\\.playSound\\(", Pattern.MULTILINE).matcher(read(path)).find();
    }

    private static Stream<Path> sources() throws IOException {
        try (var walk = Files.walk(MAIN)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList().stream();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new AssertionError(error);
        }
    }
}
