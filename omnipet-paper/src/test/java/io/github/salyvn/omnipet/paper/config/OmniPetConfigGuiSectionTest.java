package io.github.salyvn.omnipet.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * How the optional {@code gui:} section behaves inside the strict aggregate config.
 *
 * <p>Two properties matter and pull against each other: adding {@code gui} to the accepted root keys
 * must not weaken the strict rejection of a genuinely unknown section, and a legacy migration —
 * which rewrites the file from {@code encode()} — must not silently discard the operator's settings.
 */
class OmniPetConfigGuiSectionTest {
    private final OmniPetConfigLoader loader = new OmniPetConfigLoader();

    @Test
    void theShippedConfigParsesAndCarriesItsGuiSection() {
        List<String> warnings = new ArrayList<>();

        OmniPetConfig config = loader.parse(shipped(), warnings::add).config();

        assertEquals(45, config.gui().vaultPetsPerPage());
        assertEquals(8, config.gui().helpLinesPerPage());
        assertEquals(Duration.ofMinutes(2), config.gui().studioPromptTimeout());
        assertTrue(config.gui().feedback().enabled());
        assertTrue(warnings.isEmpty(), "the shipped config must parse without complaint: " + warnings);
    }

    @Test
    void deletingTheSectionEntirelyLeavesBehaviorIdenticalToTheHardcodedDefaults() {
        String withoutGui = shipped().substring(0, shipped().indexOf("\ngui:") + 1);
        assertFalse(withoutGui.contains("gui:"), "the fixture must actually have the section removed");

        OmniPetConfig config = loader.parse(withoutGui).config();

        assertEquals(GuiConfig.defaults(), config.gui());
    }

    @Test
    void aGenuinelyUnknownRootSectionIsStillRejected() {
        // Adding "gui" to the accepted roots must not turn the strict root check into a lenient one.
        assertThrows(IllegalArgumentException.class, () -> loader.parse(shipped() + "\nunknownRoot: true\n"));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(shipped() + "\nguii: {}\n"));
    }

    @Test
    void aTypoInsideTheGuiSectionWarnsInsteadOfFailingTheWholeConfig() {
        List<String> warnings = new ArrayList<>();

        OmniPetConfig config = loader.parse(
                shipped() + "\n  vaultt:\n    petsPerPage: 3\n", warnings::add).config();

        assertEquals(45, config.gui().vaultPetsPerPage());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("gui.vaultt")), warnings.toString());
    }

    @Test
    void aLegacyMigrationRoundTripDoesNotDropTheGuiSection() {
        // encode() is what a legacy migration writes back to disk. Before gui: was serialised there,
        // an upgrade silently discarded every feedback and page-size override the operator had set.
        OmniPetConfig original = loader.parse(shipped()
                .replace("petsPerPage: 45", "petsPerPage: 27")
                .replace("promptTimeoutSeconds: 120", "promptTimeoutSeconds: 45")
                .replace("enabled: true", "enabled: false")).config();
        assertEquals(27, original.gui().vaultPetsPerPage());

        String encoded = loader.encode(original);
        assertTrue(encoded.contains("gui"), "encode must serialise the section");

        OmniPetConfig reloaded = loader.parse(encoded).config();
        assertEquals(original.gui(), reloaded.gui());
        assertEquals(27, reloaded.gui().vaultPetsPerPage());
        assertEquals(Duration.ofSeconds(45), reloaded.gui().studioPromptTimeout());
        assertFalse(reloaded.gui().feedback().enabled());
    }

    @Test
    void aLegacyMigrationRoundTripKeepsACustomExperienceFormula() {
        // Compiling a formula is one-way, so encode() has no way back to the text unless the source is
        // carried beside the compiled result. Without that, a migration rewrote every operator's tuned
        // curve back to the shipped default and their pets silently changed levelling speed.
        String custom = "250 + level * 40";
        OmniPetConfig original = loader.parse(
                shipped().replace(OmniPetConfig.DEFAULT_EXPERIENCE_FORMULA, custom)).config();
        assertEquals(custom, original.experienceFormulaSource());

        String encoded = loader.encode(original);
        assertTrue(encoded.contains(custom), "encode must write the operator's formula: " + encoded);

        OmniPetConfig reloaded = loader.parse(encoded).config();
        assertEquals(custom, reloaded.experienceFormulaSource());
        assertEquals(
                original.progression().defaultFormula().required(original.progression().formulaSamples()),
                reloaded.progression().defaultFormula().required(reloaded.progression().formulaSamples()));
    }

    @Test
    void aConfigStillCarryingTheRetiredIntegrationsSectionKeepsLoading() {
        // The section is no longer read or written, but an existing install still has it on disk and
        // must not fail startup on an unknown root key.
        OmniPetConfig config = loader.parse(
                shipped() + "\nintegrations:\n  mythicLib: \"1.7.1-SNAPSHOT build 106\"\n").config();

        assertEquals(45, config.gui().vaultPetsPerPage());
        assertFalse(loader.encode(config).contains("integrations"), "a retired section must not be rewritten");
    }

    @Test
    void anUnboundGuiSettingsReadsAsTheHardcodedDefaults() {
        // A click must never fail because config was not bound yet; GuiSettings starts at defaults
        // rather than throwing the way the message catalog does.
        GuiSettings.unbind();

        assertEquals(GuiConfig.defaults(), GuiSettings.gui());
        assertEquals(45, GuiSettings.gui().vaultPetsPerPage());
    }

    private static String shipped() {
        try (var input = OmniPetConfigGuiSectionTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
