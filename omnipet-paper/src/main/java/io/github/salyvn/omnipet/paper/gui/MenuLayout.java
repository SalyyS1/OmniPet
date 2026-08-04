package io.github.salyvn.omnipet.paper.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.paper.config.MenuStyle;

/**
 * Places one menu's buttons, applying the operator's layout over the renderer's defaults.
 *
 * <p>Exists to make one class of bug impossible. The Studio's Close button was drawn at a slot that was
 * never added to the action map, so clicking it resolved to no action and did nothing — the item was
 * there, the handler had nothing to dispatch. Letting operators move buttons multiplies the chance of
 * that mistake, so {@link #put} records the action binding and the item together and there is no way to
 * do one without the other.
 *
 * <p>Buttons are collected first and drawn by {@link #draw}, because every inventory holder in this
 * plugin snapshots its action map at construction — deliberately, so a caller cannot mutate bindings
 * after the inventory is shown. Buffering lets the holder still be built from a complete map.
 *
 * <p>A collision — two buttons landing on one slot — is reported and the later button keeps its
 * built-in slot, because silently dropping one would look exactly like the dead-button bug this
 * prevents.
 */
public final class MenuLayout<A> {
    private final MenuStyle style;
    private final String menuPath;
    private final Consumer<String> warnings;
    private final Map<Integer, A> actions = new LinkedHashMap<>();
    private final Map<Integer, String> claimedBy = new LinkedHashMap<>();
    private final List<Placement> placements = new ArrayList<>();

    public MenuLayout(MenuStyle style, String menuPath, Consumer<String> warnings) {
        this.style = Objects.requireNonNull(style, "menu style");
        this.menuPath = Objects.requireNonNull(menuPath, "menu config path");
        this.warnings = Objects.requireNonNull(warnings, "warning sink");
    }

    /** The size the operator chose, or the renderer's own. */
    public int size(int defaultSize) {
        return style.size().orElse(defaultSize);
    }

    /** The filler pane the operator chose, or the shared neutral one. */
    public ItemStack filler() {
        Material configured = style.filler()
                .flatMap(name -> material(name, menuPath + ".filler"))
                .orElse(null);
        return configured == null
                ? GuiItems.filler()
                : GuiItems.of(configured, GuiItems.label(" ", GuiItems.LORE_COLOR), List.of());
    }

    /**
     * Records a button: its action binding and the item that carries it.
     *
     * <p>The only way to add a button, so a slot that carries an item always carries its action.
     */
    public void put(
            String button,
            int defaultSlot,
            A action,
            Material defaultMaterial,
            Component name,
            List<Component> lore) {
        Material material = material(button, defaultMaterial);
        int slot = bind(button, defaultSlot, action);
        placements.add(new Placement(slot, defaultSlot, () -> GuiItems.of(material, name, lore)));
    }

    /**
     * Binds an action to a button's resolved slot without drawing anything.
     *
     * <p>Split out so the layout decisions — which slot wins, which collision is refused — are testable
     * without a running server, since building an {@link ItemStack} needs one.
     *
     * @return the slot the button will occupy
     */
    public int bind(String button, int defaultSlot, A action) {
        Objects.requireNonNull(action, "button action");
        int slot = resolveSlot(button, defaultSlot);
        actions.put(slot, action);
        claimedBy.put(slot, button);
        return slot;
    }

    /**
     * Records a button whose item is already built.
     *
     * <p>For a stack carrying specialised meta the layout must not rebuild — a textured player head, for
     * instance, where replacing the stack would drop the skull texture. The operator can still move it;
     * only its material is fixed by the renderer.
     */
    public void putStack(String button, int defaultSlot, A action, ItemStack item) {
        Objects.requireNonNull(item, "button item");
        int slot = bind(button, defaultSlot, action);
        placements.add(new Placement(slot, defaultSlot, () -> item));
    }

    /** Records a decorative item with no action, for a tile that only reports state. */
    public void decorate(String button, int defaultSlot, ItemStack item) {
        Objects.requireNonNull(item, "decoration item");
        int slot = resolveSlot(button, defaultSlot);
        claimedBy.put(slot, button);
        placements.add(new Placement(slot, defaultSlot, () -> item));
    }

    /** The action bindings, for the holder that the click handler reads. */
    public Map<Integer, A> actions() {
        return Map.copyOf(actions);
    }

    /**
     * Fills the inventory and draws every recorded button.
     *
     * <p>A button whose configured slot falls outside the menu falls back to its built-in slot, with a
     * warning: a smaller configured size must not silently delete a control.
     */
    public void draw(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        for (Placement placement : placements) {
            int slot = placement.slot();
            if (slot >= inventory.getSize()) {
                warnings.accept(menuPath + " slot " + slot + " is outside this menu's "
                        + inventory.getSize() + " slots; using " + placement.defaultSlot());
                slot = placement.defaultSlot();
                if (slot >= inventory.getSize()) continue;
            }
            inventory.setItem(slot, placement.item().get());
        }
    }

    /**
     * The material for a button: the operator's choice, else the renderer's.
     *
     * <p>A configured material replaces every state variant the renderer would pick between. That is
     * intentional — naming one material for a tile with three states says it should look the same in
     * all three.
     */
    public Material material(String button, Material fallback) {
        Objects.requireNonNull(fallback, "fallback material");
        return style.button(button).material()
                .flatMap(name -> material(name, menuPath + ".buttons." + button + ".material"))
                .orElse(fallback);
    }

    private int resolveSlot(String button, int defaultSlot) {
        int configured = style.button(button).slot().orElse(defaultSlot);
        if (configured == defaultSlot) return defaultSlot;
        String existing = claimedBy.get(configured);
        if (existing != null && !existing.equals(button)) {
            warnings.accept(menuPath + ".buttons." + button + ".slot " + configured
                    + " is already used by " + existing + "; keeping its built-in slot " + defaultSlot);
            return defaultSlot;
        }
        return configured;
    }

    /** The named material, or empty after warning when it is unusable. */
    private Optional<Material> material(String raw, String path) {
        Material resolved = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (resolved == null || resolved == Material.AIR || !resolved.isItem()) {
            warnings.accept(path + " is not a usable item material: " + raw + "; using the built-in one");
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    /** A deferred item, so recording a button needs no live server; only {@link #draw} does. */
    private record Placement(int slot, int defaultSlot, java.util.function.Supplier<ItemStack> item) {}
}
