package io.github.salyvn.omnipet.paper.gui.hatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.paper.config.GuiSettings;
import io.github.salyvn.omnipet.paper.gui.GuiColors;
import io.github.salyvn.omnipet.paper.gui.GuiItems;
import io.github.salyvn.omnipet.paper.gui.MenuLayout;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.Durations;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The hatch view, in four durable presentations.
 *
 * <p>Size, materials, and slots come from {@code gui.menus.hatch} when the operator set them. Controls
 * go through {@link MenuLayout}, so a moved control keeps its action.
 */
public final class HatchMenuRenderer {
    private static final int DEFAULT_SIZE = 27;
    private static final int START_MAIN_SLOT = 11;
    private static final int INCUBATION_SLOT = 13;
    private static final int START_OFF_SLOT = 15;
    private static final int HUB_SLOT = 18;
    private static final int REFRESH_SLOT = 22;
    /**
     * Where the redeem controls sit while an egg is incubating: the slots the start buttons vacate.
     *
     * <p>Derived from the start slots rather than repeated, so moving a start button in config cannot
     * leave the two sets describing different places. They never collide at runtime — the start and
     * redeem controls render in mutually exclusive branches.
     */
    private static final int REDEEM_MAIN_SLOT = START_MAIN_SLOT;
    private static final int REDEEM_OFF_SLOT = START_OFF_SLOT;

    public Inventory render(Player player, PlayerState state) {
        MenuLayout<HatchInventoryHolder.Action> layout = new MenuLayout<>(
                GuiSettings.gui().menu("hatch"), "gui.menus.hatch", GuiSettings::warn);
        IncubationState incubation = state.incubation();
        UUID incubationId = incubation == null ? null : incubation.id();

        if (incubation == null || incubation.terminal()) {
            // Terminal history is reported but not clickable: the tile explains what happened, and the
            // start controls below are what the player acts on.
            if (incubation != null) {
                layout.decorate("incubation", INCUBATION_SLOT, previousHatch(incubation));
            }
            layout.put("startMain", START_MAIN_SLOT, HatchInventoryHolder.Action.startMain(),
                    Material.DRAGON_EGG, Messages.line(MessageKey.GUI_HATCH_START_MAIN),
                    List.of(Messages.line(MessageKey.GUI_HATCH_START_MAIN_HINT)));
            layout.put("startOff", START_OFF_SLOT, HatchInventoryHolder.Action.startOffHand(),
                    Material.DRAGON_EGG, Messages.line(MessageKey.GUI_HATCH_START_OFF),
                    List.of(Messages.line(MessageKey.GUI_HATCH_START_OFF_HINT)));
        } else {
            layout.putStack("incubation", INCUBATION_SLOT,
                    incubation.status() == IncubationStatus.READY
                            ? HatchInventoryHolder.Action.claim()
                            : HatchInventoryHolder.Action.refresh(),
                    activeIncubation(incubation));
            // Only while incubating, and only in the slots the start buttons are not using. Redeeming
            // an accelerator was previously command-only, which left the item unusable from the very
            // menu the player was already looking at.
            if (incubation.status() != IncubationStatus.READY) {
                layout.put("redeemMain", REDEEM_MAIN_SLOT, HatchInventoryHolder.Action.redeemMain(),
                        Material.CLOCK, Messages.line(MessageKey.GUI_HATCH_REDEEM_MAIN),
                        List.of(Messages.line(MessageKey.GUI_HATCH_REDEEM_MAIN_HINT)));
                layout.put("redeemOff", REDEEM_OFF_SLOT, HatchInventoryHolder.Action.redeemOffHand(),
                        Material.CLOCK, Messages.line(MessageKey.GUI_HATCH_REDEEM_OFF),
                        List.of(Messages.line(MessageKey.GUI_HATCH_REDEEM_OFF_HINT)));
            }
        }
        layout.put("refresh", REFRESH_SLOT, HatchInventoryHolder.Action.refresh(),
                Material.CLOCK, Messages.line(MessageKey.GUI_HATCH_REFRESH),
                List.of(Messages.line(MessageKey.GUI_HATCH_REFRESH_HINT)));
        layout.put("hub", HUB_SLOT, HatchInventoryHolder.Action.hub(),
                Material.COMPASS, Messages.line(MessageKey.HUB_BACK), List.of());

        HatchInventoryHolder holder = new HatchInventoryHolder(
                player.getUniqueId(), state.revision(), incubationId, layout.actions());
        Inventory inventory = Bukkit.createInventory(
                holder, layout.size(DEFAULT_SIZE), Messages.line(MessageKey.GUI_TITLE_HATCH));
        holder.bind(inventory);
        layout.draw(inventory);
        return inventory;
    }

    private static ItemStack previousHatch(IncubationState incubation) {
        return GuiItems.of(Material.ENDER_CHEST,
                Messages.line(MessageKey.GUI_HATCH_PREVIOUS,
                        Messages.of("status", Displays.words(incubation.status()))),
                List.of(
                        Messages.line(MessageKey.GUI_HATCH_PREVIOUS_PET,
                                Messages.of("pet", incubation.outcome().definitionId())),
                        Component.empty(),
                        Messages.line(MessageKey.GUI_HATCH_PREVIOUS_HINT)));
    }

    /**
     * The active incubation head.
     *
     * <p>Built on the skull stack so {@link GuiItems#of(ItemStack, Component, List)} preserves the
     * texture applied by {@link HatchHeadItems}. Placed with {@code putStack} for the same reason: the
     * layout must not rebuild it from a material and drop the texture.
     */
    private static ItemStack activeIncubation(IncubationState incubation) {
        ItemStack egg = new ItemStack(Material.PLAYER_HEAD);
        HatchHeadItems.apply(egg, incubation.outcome().icon());
        boolean ready = incubation.status() == IncubationStatus.READY;
        List<Component> lore = new ArrayList<>();
        lore.add(Messages.line(MessageKey.GUI_HATCH_PET,
                Messages.of("pet", incubation.outcome().definitionId())));
        lore.add(Messages.line(MessageKey.GUI_HATCH_TIER,
                Messages.of("status", String.valueOf(incubation.outcome().tier()))));
        lore.add(Messages.line(MessageKey.GUI_HATCH_RARITY,
                Messages.of("detail", Displays.identifier(incubation.outcome().rarityId()))));
        lore.add(Messages.line(MessageKey.GUI_HATCH_REMAINING,
                Messages.of("remaining", Durations.countdown(incubation.remainingActiveMillis()))));
        lore.add(Component.empty());
        lore.add(Messages.line(ready ? MessageKey.GUI_HATCH_READY : MessageKey.GUI_HATCH_ONLINE_ONLY));
        return GuiItems.of(egg,
                Messages.line(MessageKey.GUI_HATCH_INCUBATION,
                                Messages.of("status", Displays.words(incubation.status())))
                        .color(ready ? GuiColors.POSITIVE : GuiColors.TITLE),
                lore);
    }
}
