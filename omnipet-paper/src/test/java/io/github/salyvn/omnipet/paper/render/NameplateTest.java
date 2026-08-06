package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.runtime.PetStatus;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.paper.text.MessageCatalog;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The layout of the name floating above a pet, which is the operator's to write.
 *
 * <p>It used to be a concatenation in code: the name, two spaces, {@code Lv.}, the level, two more spaces,
 * the status. Every part of that was fixed. An operator could translate the four status words and nothing
 * else — not the spacing, not the word before the level, not the order, and not whether the level appeared
 * at all. The reported gap was that none of it was customisable, and a template is the smallest thing that
 * makes all of it so at once.
 *
 * <p>The level is the awkward part, because it can be genuinely unknown: a malformed progression node
 * leaves it unreadable, and dropping just the placeholder out of {@code Lv.<level>} leaves a bare
 * {@code Lv.} that reads like a bug. Guessing how much surrounding text belongs to the level does not
 * generalise, so the operator marks it with square brackets and the whole run goes together.
 */
class NameplateTest {
    private static final PaperHeadRendererSettings WITH_STATUS = settings(true, true);
    private static final PaperHeadRendererSettings NO_STATUS = settings(true, false);

    @BeforeEach
    void bindDefaults() {
        Messages.bind(MessageCatalog.defaults());
    }

    @AfterEach
    void restoreDefaults() {
        Messages.bind(MessageCatalog.defaults());
    }

    @Test
    void theShippedLayoutShowsNameLevelAndStatus() {
        String plate = Nameplate.text(named("Ember", 12), WITH_STATUS, PetStatus.FOLLOWING);

        assertTrue(plate.startsWith("Ember"), plate);
        assertTrue(plate.contains("12"), plate);
        assertTrue(plate.contains("following"), plate);
        assertTrue(plate.indexOf("12") < plate.indexOf("following"),
                "the shipped order is name, level, status: " + plate);
    }

    /** The whole point: an operator rewrites the layout without touching code. */
    @Test
    void anOperatorCanRewriteTheLayoutEntirely() {
        override("""
                gui:
                  pet:
                    nameplate-status: "<status> | [Lv <level> ]<name>"
                """);

        assertEquals("<green>following</green> | Lv 12 Ember",
                Nameplate.text(named("Ember", 12), WITH_STATUS, PetStatus.FOLLOWING));
    }

    /** A template with no level placeholder simply has no level, which is how somebody hides it. */
    @Test
    void aTemplateWithoutTheLevelPlaceholderShowsNoLevel() {
        override("""
                gui:
                  pet:
                    nameplate-status: "<name> <status>"
                """);

        assertEquals("Ember <green>following</green>",
                Nameplate.text(named("Ember", 12), WITH_STATUS, PetStatus.FOLLOWING));
    }

    /** And a template of nothing but the name is a plate of nothing but the name. */
    @Test
    void aTemplateOfJustTheNameIsAPlateOfJustTheName() {
        override("""
                gui:
                  pet:
                    nameplate: "<name>"
                """);

        assertEquals("Ember", Nameplate.text(named("Ember", 12), NO_STATUS, PetStatus.IDLE));
    }

    /**
     * A pet whose level cannot be read loses the level furniture with it.
     *
     * <p>The reason the bracket syntax exists. Removing only {@code <level>} from the shipped template
     * would leave {@code Ember Lv.} on the plate, which reads as a truncation rather than as an absent
     * level.
     */
    @Test
    void anUnreadableLevelTakesItsSurroundingTextWithIt() {
        String plate = Nameplate.text(named("Ember", null), NO_STATUS, null);

        assertEquals("Ember", plate);
        assertFalse(plate.contains("Lv"), "a bare level label reads as a bug: " + plate);
    }

    /** The brackets are layout marks, never text — they must not survive to the player. */
    @Test
    void theOptionalMarkersNeverReachThePlate() {
        assertFalse(Nameplate.text(named("Ember", 3), WITH_STATUS, PetStatus.IDLE).contains("["));
        assertFalse(Nameplate.text(named("Ember", 3), WITH_STATUS, PetStatus.IDLE).contains("]"));
        assertFalse(Nameplate.text(named("Ember", null), WITH_STATUS, PetStatus.IDLE).contains("["));
    }

    /**
     * Turning the status off uses the other template, not the same one with a hole in it.
     *
     * <p>Two keys rather than one because the separator that divided the name from the status belongs to
     * the status. Blanking the placeholder in a single template would leave that separator behind as
     * trailing space.
     */
    @Test
    void theStatuslessLayoutIsItsOwnTemplateRatherThanAGap() {
        override("""
                gui:
                  pet:
                    nameplate: "<name>!"
                    nameplate-status: "<name> ~ <status>"
                """);

        assertEquals("Ember!", Nameplate.text(named("Ember", 1), NO_STATUS, PetStatus.IDLE));
        assertEquals("Ember ~ <gray>idle</gray>", Nameplate.text(named("Ember", 1), WITH_STATUS, PetStatus.IDLE));
    }

    /** No status to report is the same as having the status switched off. */
    @Test
    void aNullStatusUsesTheStatuslessLayout() {
        override("""
                gui:
                  pet:
                    nameplate: "<name>!"
                    nameplate-status: "<name> ~ <status>"
                """);

        assertEquals("Ember!", Nameplate.text(named("Ember", 1), WITH_STATUS, null));
    }

    /**
     * A name's MiniMessage is spliced in unparsed, so the template can colour around it.
     *
     * <p>The reason the parts are substituted textually and the whole line parsed once at the end: a colour
     * opened in the template has to be able to close after the status, which it could not do if each part
     * arrived as an already-rendered component.
     */
    @Test
    void anOperatorColourCanWrapTheWholePlate() {
        override("""
                gui:
                  pet:
                    nameplate-status: "<gold><name> <status></gold>"
                """);

        assertEquals("<gold><red>Ember</red> <gray>idle</gray></gold>",
                Nameplate.text(named("<red>Ember</red>", 1), WITH_STATUS, PetStatus.IDLE));
    }

    /** Nameplates off is still off, whatever the template says. */
    @Test
    void theTemplateCannotResurrectADisabledPlate() {
        assertEquals("", Nameplate.text(named("Ember", 1), settings(false, true), PetStatus.IDLE));
        assertEquals("", Nameplate.text(named("", 1), WITH_STATUS, PetStatus.IDLE));
    }

    private static void override(String yaml) {
        Messages.bind(MessageCatalog.parse(yaml, warning -> {}));
    }

    private static RendererAppearance named(String displayName, Integer level) {
        return new RendererAppearance(
                "HEAD", "", "TEXTURE_URL", "https://example.invalid/a.png", displayName, level);
    }

    private static PaperHeadRendererSettings settings(boolean nameplates, boolean status) {
        return new PaperHeadRendererSettings(8.0, 0.35, 1.2, 3, 12.0, nameplates, status);
    }
}
