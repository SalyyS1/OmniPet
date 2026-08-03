package io.github.salyvn.omnipet.paper.gui.hatch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import io.github.salyvn.omnipet.paper.gui.GuiColors;
import io.github.salyvn.omnipet.paper.gui.GuiItems;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

public final class HatchMenuRenderer {
    public Inventory render(Player player, PlayerState state) {
        Map<Integer, HatchInventoryHolder.Action> actions = new HashMap<>();
        IncubationState incubation = state.incubation();
        UUID incubationId = incubation == null ? null : incubation.id();
        HatchInventoryHolder holder = new HatchInventoryHolder(
                player.getUniqueId(), state.revision(), incubationId, actions);
        Inventory inventory = Bukkit.createInventory(
                holder, 27, Messages.line(MessageKey.GUI_TITLE_HATCH));
        holder.bind(inventory);
        fill(inventory);

        if (incubation == null || incubation.terminal()) {
            if (incubation != null) inventory.setItem(13, previousHatch(incubation));
            actions.put(11, HatchInventoryHolder.Action.startMain());
            actions.put(15, HatchInventoryHolder.Action.startOffHand());
            inventory.setItem(11, GuiItems.of(Material.DRAGON_EGG,
                    Messages.line(MessageKey.GUI_HATCH_START_MAIN),
                    List.of(Messages.line(MessageKey.GUI_HATCH_START_MAIN_HINT))));
            inventory.setItem(15, GuiItems.of(Material.DRAGON_EGG,
                    Messages.line(MessageKey.GUI_HATCH_START_OFF),
                    List.of(Messages.line(MessageKey.GUI_HATCH_START_OFF_HINT))));
        } else {
            inventory.setItem(13, activeIncubation(incubation));
            actions.put(13, incubation.status() == IncubationStatus.READY
                    ? HatchInventoryHolder.Action.claim()
                    : HatchInventoryHolder.Action.refresh());
        }
        actions.put(22, HatchInventoryHolder.Action.refresh());
        inventory.setItem(22, GuiItems.of(Material.CLOCK,
                Messages.line(MessageKey.GUI_HATCH_REFRESH),
                List.of(Messages.line(MessageKey.GUI_HATCH_REFRESH_HINT))));
        // Slot 18 is free: 11/13/15/22 carry the start, incubation, and refresh controls.
        actions.put(18, HatchInventoryHolder.Action.hub());
        inventory.setItem(18, GuiItems.of(Material.COMPASS,
                Messages.line(MessageKey.HUB_BACK), List.of()));
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
     * texture applied by {@link HatchHeadItems}.
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
                Messages.of("remaining", format(incubation.remainingActiveMillis()))));
        lore.add(Component.empty());
        lore.add(Messages.line(ready ? MessageKey.GUI_HATCH_READY : MessageKey.GUI_HATCH_ONLINE_ONLY));
        return GuiItems.of(egg,
                Messages.line(MessageKey.GUI_HATCH_INCUBATION,
                                Messages.of("status", Displays.words(incubation.status())))
                        .color(ready ? GuiColors.POSITIVE : GuiColors.TITLE),
                lore);
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = GuiItems.filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }

    private static String format(long millis) {
        long seconds = Math.max(0, Duration.ofMillis(millis).toSeconds());
        long days = seconds / 86_400;
        seconds %= 86_400;
        long hours = seconds / 3_600;
        seconds %= 3_600;
        long minutes = seconds / 60;
        seconds %= 60;
        return days > 0
                ? "%dd %02dh %02dm %02ds".formatted(days, hours, minutes, seconds)
                : "%02dh %02dm %02ds".formatted(hours, minutes, seconds);
    }
}
