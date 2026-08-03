package io.github.salyvn.omnipet.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The {@code gui:} section is lenient by design.
 *
 * <p>{@code storage:} rejects an unknown key because a misread slot limit corrupts data. This section
 * only controls how menus look and sound, so every mistake falls back to the default for that one key
 * and warns. A typo must never stop players using their pets.
 */
class GuiConfigLoaderTest {
    private final GuiConfigLoader loader = new GuiConfigLoader();

    @Test
    void anAbsentSectionReproducesThePreviousHardcodedValues() {
        List<String> warnings = new ArrayList<>();

        GuiConfig config = loader.parse(null, warnings::add);

        assertEquals(45, config.vaultPetsPerPage());
        assertEquals(8, config.helpLinesPerPage());
        assertEquals(Duration.ofMinutes(2), config.studioPromptTimeout());
        assertTrue(config.feedback().enabled());
        assertEquals(Duration.ofMillis(150), config.feedback().minimumInterval());
        assertEquals("ENTITY_EXPERIENCE_ORB_PICKUP", config.feedback().success().sound());
        assertEquals(GuiConfig.defaults(), config);
        assertTrue(warnings.isEmpty(), "an absent section is normal, not a problem to report");
    }

    @Test
    void anOversizedVaultPageIsCappedToTheLayoutWithAWarning() {
        List<String> warnings = new ArrayList<>();

        GuiConfig config = loader.parse(Map.of("vault", Map.of("petsPerPage", 99)), warnings::add);

        assertEquals(45, config.vaultPetsPerPage());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("petsPerPage") && w.contains("45")),
                "the operator must learn what was actually applied: " + warnings);
    }

    @Test
    void aZeroOrNegativeVaultPageIsRaisedToOne() {
        assertEquals(1, loader.parse(Map.of("vault", Map.of("petsPerPage", 0)), warning -> {})
                .vaultPetsPerPage());
        assertEquals(1, loader.parse(Map.of("vault", Map.of("petsPerPage", -20)), warning -> {})
                .vaultPetsPerPage());
    }

    @Test
    void aHelpPageLengthIsClampedAtBothEnds() {
        assertEquals(20, loader.parse(Map.of("help", Map.of("linesPerPage", 5000)), warning -> {})
                .helpLinesPerPage());
        assertEquals(1, loader.parse(Map.of("help", Map.of("linesPerPage", 0)), warning -> {})
                .helpLinesPerPage());
    }

    @Test
    void aMalformedValueNeverThrowsAndFallsBackPerKey() {
        List<String> warnings = new ArrayList<>();

        GuiConfig config = loader.parse(Map.of(
                "vault", Map.of("petsPerPage", "not a number"),
                "help", "not even a map",
                "studio", Map.of("promptTimeoutSeconds", -5),
                "feedback", Map.of(
                        "enabled", "maybe",
                        "minIntervalMillis", "soon",
                        "success", Map.of("sound", 42, "volume", "loud"))),
                warnings::add);

        // Every bad value degrades to its own default; nothing cascades.
        assertEquals(45, config.vaultPetsPerPage());
        assertEquals(8, config.helpLinesPerPage());
        assertEquals(Duration.ofMinutes(2), config.studioPromptTimeout());
        assertTrue(config.feedback().enabled());
        assertEquals(Duration.ofMillis(150), config.feedback().minimumInterval());
        assertEquals("ENTITY_EXPERIENCE_ORB_PICKUP", config.feedback().success().sound());
        assertEquals(0.6f, config.feedback().success().volume());
        assertFalse(warnings.isEmpty(), "each fallback must be reported");
    }

    @Test
    void anUnknownKeyIsIgnoredWithAWarningRatherThanRejected() {
        List<String> warnings = new ArrayList<>();

        GuiConfig config = loader.parse(Map.of(
                "vaultt", Map.of("petsPerPage", 10),
                "feedback", Map.of("enabledd", false)),
                warnings::add);

        assertEquals(GuiConfig.defaults(), config);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("gui.vaultt")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("gui.feedback.enabledd")), warnings.toString());
    }

    @Test
    void operatorOverridesAreApplied() {
        GuiConfig config = loader.parse(Map.of(
                "vault", Map.of("petsPerPage", 27),
                "help", Map.of("linesPerPage", 12),
                "studio", Map.of("promptTimeoutSeconds", 45),
                "feedback", Map.of(
                        "enabled", false,
                        "actionBar", false,
                        "minIntervalMillis", 500,
                        "blocked", Map.of("sound", "block_anvil_land", "volume", 1.0, "pitch", 0.9))),
                warning -> {});

        assertEquals(27, config.vaultPetsPerPage());
        assertEquals(12, config.helpLinesPerPage());
        assertEquals(Duration.ofSeconds(45), config.studioPromptTimeout());
        assertFalse(config.feedback().enabled());
        assertFalse(config.feedback().actionBar());
        assertEquals(Duration.ofMillis(500), config.feedback().minimumInterval());
        // Sound names normalise to upper case so an operator's lower-case entry still resolves.
        assertEquals("BLOCK_ANVIL_LAND", config.feedback().blocked().sound());
        assertEquals(0.9f, config.feedback().blocked().pitch());
    }

    @Test
    void volumeAndPitchAreClampedToTheRangeBukkitAccepts() {
        GuiConfig config = loader.parse(Map.of("feedback", Map.of(
                "success", Map.of("sound", "UI_BUTTON_CLICK", "volume", -3.0, "pitch", 99.0))),
                warning -> {});

        assertEquals(0.0f, config.feedback().success().volume());
        assertEquals(2.0f, config.feedback().success().pitch());
    }

    @Test
    void theStudioTickExpiryIsDerivedFromItsDurationRatherThanMaintainedBesideIt() {
        // Six call sites previously carried a hand-written 2 * 60 * 20L next to their own
        // Duration.ofMinutes(2). Deriving one from the other removes six chances to disagree.
        assertEquals(2400L, GuiConfig.defaults().studioPromptTimeoutTicks());

        GuiConfig custom = loader.parse(
                Map.of("studio", Map.of("promptTimeoutSeconds", 45)), warning -> {});
        assertEquals(45 * 20L, custom.studioPromptTimeoutTicks());
        assertEquals(custom.studioPromptTimeout().toSeconds() * 20L, custom.studioPromptTimeoutTicks());
    }

    @Test
    void encodeRoundTripsSoALegacyMigrationCannotDropTheSection() {
        GuiConfig original = loader.parse(Map.of(
                "vault", Map.of("petsPerPage", 27),
                "help", Map.of("linesPerPage", 12),
                "studio", Map.of("promptTimeoutSeconds", 45),
                "feedback", Map.of(
                        "enabled", false,
                        "actionBar", false,
                        "minIntervalMillis", 500,
                        "progress", Map.of("sound", "BLOCK_ANVIL_LAND", "volume", 1.0, "pitch", 0.9))),
                warning -> {});

        GuiConfig roundTripped = loader.parse(loader.encode(original), warning -> {});

        assertEquals(original, roundTripped);
    }
}
