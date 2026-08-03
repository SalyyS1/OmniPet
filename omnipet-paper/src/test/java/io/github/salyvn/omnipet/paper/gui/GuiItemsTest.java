package io.github.salyvn.omnipet.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

class GuiItemsTest {
    @Test
    void labelResolvesItalicToFalseInsteadOfLeavingItUnset() {
        Component label = GuiItems.label("Vault", NamedTextColor.GOLD);

        assertEquals(TextDecoration.State.FALSE, label.decoration(TextDecoration.ITALIC));
        assertEquals(NamedTextColor.GOLD, label.color());
    }

    @Test
    void loreUsesTheSharedDescriptiveColorAndDisablesItalic() {
        Component line = GuiItems.lore("Owned: 3/30");

        assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
        assertEquals(GuiItems.LORE_COLOR, line.color());
    }

    @Test
    void uprightKeepsAnExplicitItalicChoice() {
        Component italic = Component.text("Legacy").decoration(TextDecoration.ITALIC, true);

        assertEquals(TextDecoration.State.TRUE, GuiItems.upright(italic).decoration(TextDecoration.ITALIC));
    }

    @Test
    void appliedNameAndLoreAreNeverItalic() {
        Recorder recorder = new Recorder();
        FakeStack stack = new FakeStack(Material.PLAYER_HEAD, meta(recorder, ItemMeta.class));

        GuiItems.of(stack, Component.text("Pet", NamedTextColor.AQUA),
                List.of(Component.text("Level 4"), Component.text("Rarity rare")));

        assertEquals(TextDecoration.State.FALSE, recorder.name.decoration(TextDecoration.ITALIC));
        assertEquals(2, recorder.lore.size());
        for (Component line : recorder.lore) {
            assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
        }
    }

    @Test
    void appliedStackKeepsItsOwnSkullMetaInstance() {
        Recorder recorder = new Recorder();
        ItemMeta skull = meta(recorder, SkullMeta.class);
        FakeStack stack = new FakeStack(Material.PLAYER_HEAD, skull);

        ItemStack result = GuiItems.of(stack, Component.text("Head"), List.of());

        assertSame(stack, result);
        assertSame(skull, stack.applied);
        assertTrue(stack.applied instanceof SkullMeta);
    }

    @Test
    void aStackWithoutMetaIsRejectedInsteadOfSilentlyLosingText() {
        FakeStack stack = new FakeStack(Material.AIR, null);

        assertThrows(IllegalArgumentException.class,
                () -> GuiItems.of(stack, Component.text("Nothing"), List.of()));
    }

    /** Minimal {@link ItemStack} whose meta is supplied by the test instead of by the server. */
    private static final class FakeStack extends ItemStack {
        private final Material material;
        private final ItemMeta meta;
        private ItemMeta applied;

        private FakeStack(Material material, ItemMeta meta) {
            this.material = material;
            this.meta = meta;
        }

        @Override public Material getType() { return material; }
        @Override public ItemMeta getItemMeta() { return meta; }

        @Override
        public boolean setItemMeta(ItemMeta itemMeta) {
            applied = itemMeta;
            return true;
        }
    }

    /** Captures the components the builder writes so decorations can be asserted. */
    private static final class Recorder {
        private Component name;
        private List<Component> lore = List.of();
    }

    @SuppressWarnings("unchecked")
    private static ItemMeta meta(Recorder recorder, Class<? extends ItemMeta> type) {
        return (ItemMeta) Proxy.newProxyInstance(
                GuiItemsTest.class.getClassLoader(),
                new Class<?>[] {type},
                (instance, method, args) -> switch (method.getName()) {
                    case "displayName" -> {
                        recorder.name = (Component) args[0];
                        yield null;
                    }
                    case "lore" -> {
                        recorder.lore = new ArrayList<>((List<Component>) args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
