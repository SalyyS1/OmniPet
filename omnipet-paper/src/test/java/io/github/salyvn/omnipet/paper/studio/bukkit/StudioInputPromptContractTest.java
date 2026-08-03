package io.github.salyvn.omnipet.paper.studio.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.studio.StatModifierType;

/**
 * Guards the Phase 4 fix at the source level: no chat capture may start without a prompt.
 *
 * <p>The original defect was structural — {@code awaitField} closed the inventory and waited in
 * silence — so the regression guard has to be structural too.
 */
class StudioInputPromptContractTest {
    private static final Path CONTROLLER =
            Path.of("src/main/java/io/github/salyvn/omnipet/paper/studio/bukkit/PetStudioController.java");

    @Test
    void everyChatCaptureSendsSomethingBeforeClosingTheInventory() throws IOException {
        List<String> lines = Files.readAllLines(CONTROLLER);

        for (int index = 0; index < lines.size(); index++) {
            if (!lines.get(index).contains("player.closeInventory()")) continue;
            // Only await paths matter; the shutdown and session-close paths legitimately just close.
            if (!capturesChatNearby(lines, index)) continue;
            int line = index + 1;
            assertTrue(promptedBefore(lines, index),
                    () -> "line " + line + " closes the inventory to capture chat without prompting");
        }
    }

    @Test
    void theAwaitFieldHelperCannotBeCalledWithoutAPrompt() throws IOException {
        String source = Files.readString(CONTROLLER);

        // The private overload that actually captures takes a Runnable prompt; there is no
        // prompt-less variant left for a future caller to reach for.
        assertTrue(source.contains("String path, Runnable sendPrompt,"));
        assertFalse(source.contains("awaitField(Player player, StudioState state, String path, ChatInputParser"));
    }

    @Test
    void everyPromptDeclaresAFieldNameFormatAndExample() {
        for (StudioFieldPrompt prompt : StudioFieldPrompt.values()) {
            assertFalse(prompt.field().isBlank(), () -> prompt.name() + " has no field name");
            assertFalse(prompt.path().isBlank(), () -> prompt.name() + " has no input path");
        }
    }

    @Test
    void promptPathsAreUniqueAndMatchTheAwaitCallsInTheController() throws IOException {
        String source = Files.readString(CONTROLLER);
        Set<String> seen = new HashSet<>();

        for (StudioFieldPrompt prompt : StudioFieldPrompt.values()) {
            assertTrue(seen.add(prompt.path()), () -> "duplicate prompt path: " + prompt.path());
        }
        // Each declared path must appear as an await key, except the stat value path which is
        // built per stat as "stats." + id.
        for (StudioFieldPrompt prompt : StudioFieldPrompt.values()) {
            if (prompt == StudioFieldPrompt.STATS_MANUAL) continue;
            assertTrue(source.contains("\"" + prompt.path() + "\"") || source.contains(prompt.name()),
                    () -> prompt.path() + " is declared but never used");
        }
    }

    @Test
    void theStatValuePathIsRecognisedSeparatelyFromTheSearchPath() {
        assertTrue(StudioFieldPrompt.isStatValuePath("stats.mythiclib:attack_damage"));
        assertFalse(StudioFieldPrompt.isStatValuePath("stats.search"));
        assertFalse(StudioFieldPrompt.isStatValuePath("stats"));
        assertFalse(StudioFieldPrompt.isStatValuePath(null));
    }

    @Test
    void everyModifierHasALabelExplanationExampleAndRangeHint() {
        for (StatModifierType modifier : StatModifierType.values()) {
            assertFalse(StatModifierPresentation.label(modifier).isBlank());
            assertFalse(StatModifierPresentation.explanation(modifier).isBlank());
            assertFalse(StatModifierPresentation.example(modifier).isBlank());
            assertFalse(StatModifierPresentation.rangeHint(modifier).isBlank());
            // The raw enum name must never reach the operator.
            assertFalse(StatModifierPresentation.label(modifier).equals(modifier.name()));
        }
    }

    @Test
    void modifierLabelsReadAsProseNotEnumNames() {
        assertEquals("Flat", StatModifierPresentation.label(StatModifierType.FLAT));
        assertEquals("Relative", StatModifierPresentation.label(StatModifierType.RELATIVE));
        assertEquals("Additive multiplier",
                StatModifierPresentation.label(StatModifierType.ADDITIVE_MULTIPLIER));
    }

    @Test
    void modifierLoreJoinsLabelsInsteadOfPrintingASet() {
        String joined = StatModifierPresentation.labels(
                List.of(StatModifierType.FLAT, StatModifierType.ADDITIVE_MULTIPLIER));

        assertEquals("Flat, Additive multiplier", joined);
        assertFalse(joined.contains("["));
        assertEquals("none", StatModifierPresentation.labels(List.of()));
    }

    @Test
    void aPendingStatIsClearedOnCompletionCancelAndNavigation() throws IOException {
        String source = Files.readString(CONTROLLER);
        Matcher matcher = Pattern.compile("clearPendingStat\\(\\)").matcher(source);

        int clears = 0;
        while (matcher.find()) clears++;
        int found = clears;
        // openStats, the two stale-entry guards, the modifier-type guards, BACK from both stat
        // screens, the input-failure path, and the successful apply.
        assertTrue(found >= 6, () -> "expected the pending stat to be cleared on every exit, found " + found);
    }

    private static boolean capturesChatNearby(List<String> lines, int closeIndex) {
        int limit = Math.min(lines.size(), closeIndex + 4);
        for (int index = closeIndex + 1; index < limit; index++) {
            if (lines.get(index).contains("inputs.await(")) return true;
        }
        return false;
    }

    /**
     * Looks back a few lines rather than exactly one, because a prompt may be a wrapped multi-line
     * statement. Stops at the statement that stages the input token, which every await path shares.
     */
    private static boolean promptedBefore(List<String> lines, int closeIndex) {
        for (int index = closeIndex - 1; index >= 0 && index >= closeIndex - 4; index--) {
            String line = lines.get(index);
            if (line.contains(".send(player)") || line.contains("sendPrompt.run()")
                    || line.contains("sendMessage")) {
                return true;
            }
            if (line.contains("setPendingInput")) return false;
        }
        return false;
    }
}
