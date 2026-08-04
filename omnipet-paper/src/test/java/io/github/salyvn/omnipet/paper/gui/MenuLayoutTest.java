package io.github.salyvn.omnipet.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.paper.config.MenuStyle;

/**
 * The invariant that keeps a restyled menu working.
 *
 * <p>The Studio's Close button was drawn at a slot that was never added to the action map, so the click
 * resolved to nothing and the button was dead. Letting operators move buttons multiplies the chance of
 * that, so a placement records the action and the item together — these tests pin that a moved button
 * carries its action to the new slot and that a refused move keeps the old one.
 *
 * <p>Driven through {@link MenuLayout#bind} rather than {@code put}: binding is the decision under test,
 * while {@code put} additionally builds an {@code ItemStack}, which needs a running server. That split
 * is why {@code bind} exists.
 */
class MenuLayoutTest {
    private final List<String> warnings = new ArrayList<>();

    @Test
    void aMovedButtonCarriesItsActionToTheNewSlot() {
        MenuLayout<String> layout = layout(Map.of("vault", button(null, 10)));

        int slot = layout.bind("vault", 11, "VAULT");

        assertEquals(10, slot);
        assertEquals(Map.of(10, "VAULT"), layout.actions(),
                "the action must follow the button, or the moved button would be dead");
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void anUnmovedButtonKeepsItsBuiltInSlot() {
        MenuLayout<String> layout = layout(Map.of());

        assertEquals(11, layout.bind("vault", 11, "VAULT"));
        assertEquals(Map.of(11, "VAULT"), layout.actions());
    }

    @Test
    void aCollisionKeepsTheBuiltInSlotAndSaysSo() {
        // Silently dropping one of two colliding buttons would look exactly like the dead-button bug.
        MenuLayout<String> layout = layout(Map.of("hatch", button(null, 11)));

        layout.bind("vault", 11, "VAULT");
        layout.bind("hatch", 13, "HATCH");

        assertEquals(Map.of(11, "VAULT", 13, "HATCH"), layout.actions());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("already used by vault")),
                warnings.toString());
    }

    @Test
    void movingTwoButtonsToDistinctSlotsKeepsBothActions() {
        MenuLayout<String> layout = layout(Map.of(
                "vault", button(null, 0),
                "hatch", button(null, 8)));

        layout.bind("vault", 11, "VAULT");
        layout.bind("hatch", 13, "HATCH");

        assertEquals(Map.of(0, "VAULT", 8, "HATCH"), layout.actions());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void theSizeIsTheOperatorsWhenSetAndTheRenderersOtherwise() {
        assertEquals(54, layout(Map.of()).size(54));
        assertEquals(27, new MenuLayout<String>(
                new MenuStyle(Optional.of(27), Optional.empty(), Map.of()),
                "gui.menus.hub", warnings::add).size(54));
    }

    private MenuLayout<String> layout(Map<String, MenuStyle.ButtonStyle> buttons) {
        return new MenuLayout<>(
                new MenuStyle(Optional.empty(), Optional.empty(), buttons),
                "gui.menus.hub", warnings::add);
    }

    private static MenuStyle.ButtonStyle button(String material, Integer slot) {
        return new MenuStyle.ButtonStyle(Optional.ofNullable(material), Optional.ofNullable(slot));
    }
}
