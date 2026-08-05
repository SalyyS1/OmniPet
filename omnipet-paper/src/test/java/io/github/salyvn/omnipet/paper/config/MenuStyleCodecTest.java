package io.github.salyvn.omnipet.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The optional {@code gui.menus:} section.
 *
 * <p>Menu layout is cosmetic, so the loader is lenient: a bad value degrades that one key and warns
 * rather than stopping players opening a menu. Names are validated against the known menus and buttons
 * so a typo is reported at load, instead of a button the operator thought they had moved silently
 * staying put with no explanation.
 */
class MenuStyleCodecTest {
    private final MenuStyleCodec codec = new MenuStyleCodec();
    private final List<String> warnings = new ArrayList<>();

    @Test
    void anAbsentSectionConfiguresNothing() {
        assertEquals(Map.of(), codec.parse(null, "gui.menus", warnings::add));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void readsSizeFillerAndPerButtonMaterialAndSlot() {
        Map<String, MenuStyle> menus = codec.parse(Map.of(
                "hub", Map.of(
                        "size", 54,
                        "filler", "BLACK_STAINED_GLASS_PANE",
                        "buttons", Map.of("vault", Map.of("material", "CHEST", "slot", 10)))),
                "gui.menus", warnings::add);

        MenuStyle hub = menus.get("hub");
        assertEquals(Optional.of(54), hub.size());
        assertEquals(Optional.of("BLACK_STAINED_GLASS_PANE"), hub.filler());
        assertEquals(Optional.of("CHEST"), hub.button("vault").material());
        assertEquals(Optional.of(10), hub.button("vault").slot());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void anUnknownMenuOrButtonIsReportedRatherThanIgnored() {
        // A silently-ignored name is the confusing failure: the operator edits config, reloads, and the
        // button has not moved, with nothing explaining why.
        Map<String, MenuStyle> menus = codec.parse(Map.of(
                "vualt", Map.of("size", 27),
                "hub", Map.of("buttons", Map.of("vualt", Map.of("material", "CHEST")))),
                "gui.menus", warnings::add);

        assertFalse(menus.containsKey("vualt"));
        assertTrue(menus.get("hub") == null || menus.get("hub").buttons().isEmpty());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("vualt")), warnings.toString());
    }

    @Test
    void aSizeThatIsNotRowsOfNineIsRefused() {
        Map<String, MenuStyle> menus = codec.parse(
                Map.of("hub", Map.of("size", 30)), "gui.menus", warnings::add);

        assertTrue(menus.isEmpty(), "an unusable size leaves the menu at its built-in shape");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("multiple of 9")), warnings.toString());
    }

    @Test
    void anOutOfRangeSlotIsRefused() {
        Map<String, MenuStyle> menus = codec.parse(Map.of(
                "hub", Map.of("buttons", Map.of("vault", Map.of("slot", 99)))),
                "gui.menus", warnings::add);

        assertTrue(menus.isEmpty());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("slot index")), warnings.toString());
    }

    @Test
    void aConfiguredLayoutSurvivesALegacyMigrationRoundTrip() {
        // encode() is what a migration writes back, so a restyled menu must not be dropped on upgrade.
        OmniPetConfigLoader loader = new OmniPetConfigLoader();
        OmniPetConfig original = loader.parse(shipped().replace(
                "  studio:\n",
                "  menus:\n    hub:\n      buttons:\n        vault:\n          material: CHEST\n"
                        + "  studio:\n")).config();
        assertEquals(Optional.of("CHEST"),
                original.gui().menu("hub").button("vault").material());

        OmniPetConfig reloaded = loader.parse(loader.encode(original)).config();

        assertEquals(original.gui().menus(), reloaded.gui().menus());
    }

    @Test
    void theShippedConfigRestylesNothing() {
        // Every menu block ships commented out, so a stock install reads as all-defaults and encode
        // must not then write empty menu sections back into the file.
        OmniPetConfigLoader loader = new OmniPetConfigLoader();

        OmniPetConfig config = loader.parse(shipped(), warnings::add).config();

        assertEquals(Map.of(), config.gui().menus());
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertFalse(loader.encode(config).contains("menus"),
                "an unconfigured layout must not be written back");
    }

    @Test
    void everyDocumentedButtonNameIsAcceptedByItsMenu() {
        // config.yml lists the buttons per menu. If that list and knownMenus() disagree, an operator
        // following the documentation gets an unknown-button warning for a name that should work.
        MenuStyle.knownMenus().forEach((menu, buttons) -> buttons.forEach(button -> {
            List<String> perButton = new ArrayList<>();
            Map<String, MenuStyle> parsed = codec.parse(Map.of(
                    menu, Map.of("buttons", Map.of(button, Map.of("material", "CHEST")))),
                    "gui.menus", perButton::add);
            assertTrue(perButton.isEmpty(), menu + "." + button + " warned: " + perButton);
            assertEquals(Optional.of("CHEST"), parsed.get(menu).button(button).material());
        }));
    }

    /**
     * The shipped config, with line endings normalised to LF.
     *
     * <p>Normalised because the tests anchor their injections on {@code "\n"}: on a CRLF checkout the
     * anchor would silently fail to match, {@code replace} would be a no-op, and the assertion would
     * compare against a value that was never injected.
     */
    private static String shipped() {
        try (var input = MenuStyleCodecTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
