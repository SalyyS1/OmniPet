package io.github.salyvn.omnipet.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.paper.render.PaperHeadRendererSettings;

/**
 * The operator-owned {@code render:} section.
 *
 * <p>Renderer movement was previously unreachable: both renderers built their own settings from
 * hardcoded defaults, and the ModelEngine adapter carried a second copy of the same numbers, so there
 * was no way to retune either and no way to keep the two in step.
 *
 * <p>Strict rather than lenient, unlike the display settings: these values steer entities, so a
 * misspelled key is refused rather than quietly ignored.
 */
class OmniPetConfigRenderSectionTest {
    private final OmniPetConfigLoader loader = new OmniPetConfigLoader();

    @Test
    void theShippedSectionParsesToTheBuiltInTuning() {
        OmniPetConfig config = loader.parse(shipped()).config();

        assertEquals(PaperHeadRendererSettings.defaults(), config.render(),
                "the shipped file must document the built-in values, not diverge from them");
    }

    @Test
    void anAbsentSectionIsTheBuiltInTuning() {
        String withoutRender = withoutRenderSection();
        assertFalse(withoutRender.contains("\nrender:\n"), "the fixture must actually drop the section");

        assertEquals(PaperHeadRendererSettings.defaults(), loader.parse(withoutRender).config().render());
    }

    @Test
    void everyFieldIsActuallyRead() {
        OmniPetConfig config = loader.parse(withRenderSection("""
                render:
                  safetyDistance: 12.5
                  movementGain: 0.5
                  maximumVelocity: 2.0
                  interpolationTicks: 5
                  maximumLeanDegrees: 0.0
                """)).config();

        assertEquals(12.5, config.render().safetyDistance());
        assertEquals(0.5, config.render().movementGain());
        assertEquals(2.0, config.render().maximumVelocity());
        assertEquals(5, config.render().interpolationTicks());
        assertEquals(0.0, config.render().maximumLeanDegrees(), "zero must disable the lean, not fall back");
    }

    @Test
    void aTypoIsRefusedRatherThanIgnored() {
        // Deliberately unlike the display section: silently ignoring this would leave an operator
        // convinced they had retuned movement when they had not.
        assertThrows(IllegalArgumentException.class,
                () -> loader.parse(withRenderSection("render:\n  saftyDistance: 12.5\n")));
    }

    @Test
    void anOutOfRangeValueIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> loader.parse(withRenderSection("render:\n  movementGain: 2.0\n")));
        assertThrows(IllegalArgumentException.class,
                () -> loader.parse(withRenderSection("render:\n  maximumLeanDegrees: 90\n")));
    }

    @Test
    void aTunedSectionSurvivesALegacyMigrationRoundTrip() {
        // encode() is what a migration writes back, so tuned movement must not be reset on upgrade.
        OmniPetConfig original = loader.parse(withRenderSection("""
                render:
                  movementGain: 0.6
                  maximumLeanDegrees: 30.0
                """)).config();
        assertEquals(0.6, original.render().movementGain());
        assertEquals(30.0, original.render().maximumLeanDegrees());

        OmniPetConfig reloaded = loader.parse(loader.encode(original)).config();

        assertEquals(original.render(), reloaded.render());
    }

    @Test
    void theSectionIsDocumentedForOperatorsRatherThanOnlyAccepted() {
        String shipped = shipped();

        assertTrue(shipped.contains("\nrender:\n"), "an operator cannot tune a section that is not there");
        for (String key : java.util.List.of(
                "safetyDistance", "movementGain", "maximumVelocity", "interpolationTicks",
                "maximumLeanDegrees")) {
            assertTrue(shipped.contains(key), key + " is readable but undocumented");
        }
    }

    /** The shipped config, normalised to LF so anchors match on a CRLF checkout. */
    private static String shipped() {
        try (var input = OmniPetConfigRenderSectionTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    /**
     * The shipped config with its whole {@code render:} block replaced.
     *
     * <p>Replaced rather than prepended to: inserting keys ahead of the shipped ones would leave both in
     * the document, and YAML refuses a duplicate key.
     */
    private static String withRenderSection(String replacement) {
        String shipped = shipped();
        int start = shipped.indexOf("\nrender:\n");
        if (start < 0) throw new AssertionError("the shipped config no longer has a render section");
        int end = shipped.indexOf("\n# ---", start + 1);
        if (end < 0) throw new AssertionError("the render section is not followed by another section");
        return shipped.substring(0, start + 1) + replacement + shipped.substring(end);
    }

    /** The shipped config with no {@code render:} block at all. */
    private static String withoutRenderSection() {
        return withRenderSection("");
    }
}
