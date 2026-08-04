package io.github.salyvn.omnipet.paper.incubation.action;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionStage;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionTransaction;
import io.github.salyvn.omnipet.core.incubation.RepositoryHatchService;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.Durations;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/** Player redemption and bounded operator distribution for durable incubation action items. */
public final class IncubationActionItemController {
    private final RepositoryHatchService hatches;
    private final PaperIncubationItemActionCodec codec;
    private final PaperIncubationItemActionInventory inventory;
    private final IncubationItemActionCoordinator coordinator;

    public IncubationActionItemController(
            RepositoryHatchService hatches,
            PaperIncubationItemActionCodec codec,
            PaperIncubationItemActionInventory inventory,
            IncubationItemActionCoordinator coordinator) {
        this.hatches = Objects.requireNonNull(hatches, "hatch repository service");
        this.codec = Objects.requireNonNull(codec, "incubation action item codec");
        this.inventory = Objects.requireNonNull(inventory, "incubation action item inventory");
        this.coordinator = Objects.requireNonNull(coordinator, "incubation action coordinator");
    }

    public void redeem(Player player, EggInventoryHand hand) {
        Objects.requireNonNull(player, "incubation action player");
        requireMainThread();
        try {
            var state = hatches.snapshot(player.getUniqueId());
            if (state.incubation() == null) {
                player.sendMessage(Messages.line(MessageKey.HATCH_ITEM_NO_INCUBATION));
                return;
            }
            CapturedIncubationItemAction captured = inventory.capture(player.getUniqueId(), hand);
            UUID token = UUID.randomUUID();
            IncubationItemActionTransaction requested = new IncubationItemActionTransaction(
                    token,
                    player.getUniqueId(),
                    state.incubation().id(),
                    state.revision(),
                    captured.identity(),
                    captured.type(),
                    captured.effectMillis(),
                    IncubationItemActionStage.PREPARED);
            IncubationItemActionResult result = coordinator.redeem(requested);
            boolean committed = result.status() == IncubationItemActionResult.Status.COMMITTED
                    || result.status() == IncubationItemActionResult.Status.ALREADY_COMMITTED;
            player.sendMessage(Messages.line(
                    committed ? MessageKey.HATCH_ITEM_RESULT : MessageKey.HATCH_ITEM_RESULT_PENDING,
                    Messages.of("status", words(result.status()))));
        } catch (IOException | RuntimeException failure) {
            player.sendMessage(Messages.line(MessageKey.HATCH_ITEM_FAILED,
                    Messages.of("detail", detail(failure))));
        }
    }

    public void command(CommandSender sender, List<String> arguments) {
        requireMainThread();
        if (arguments.size() < 2 || arguments.size() > 4) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(arguments.get(1));
        if (target == null || !target.isOnline()) {
            sender.sendMessage("OmniPet: item target must be an online exact player name.");
            return;
        }
        try {
            String type = arguments.getFirst().toLowerCase(Locale.ROOT);
            if (type.equals("reducer") && (arguments.size() < 3 || arguments.size() > 4)) {
                usage(sender);
                return;
            }
            if (type.equals("instant") && (arguments.size() < 2 || arguments.size() > 3)) {
                usage(sender);
                return;
            }
            long effectMillis = type.equals("reducer")
                    ? Math.multiplyExact(Long.parseLong(arguments.get(2)), 1000L)
                    : 0;
            int amountIndex = type.equals("reducer") ? 3 : 2;
            int amount = arguments.size() > amountIndex ? Integer.parseInt(arguments.get(amountIndex)) : 1;
            ItemStack item = switch (type) {
                case "reducer" -> label(codec.createReducer(Material.CLOCK, effectMillis), true, effectMillis);
                case "instant" -> label(codec.createInstantHatch(Material.NETHER_STAR), false, 0);
                default -> throw new IllegalArgumentException("unknown incubation item type");
            };
            if (amount < 1 || amount > item.getMaxStackSize()) {
                throw new IllegalArgumentException("amount must fit one item stack");
            }
            int empty = target.getInventory().firstEmpty();
            if (empty < 0) {
                sender.sendMessage("OmniPet: target inventory has no empty slot; nothing was delivered.");
                return;
            }
            item.setAmount(amount);
            target.getInventory().setItem(empty, item);
            sender.sendMessage("OmniPet: delivered " + amount + " " + type + " item(s) to " + target.getName() + ".");
        } catch (ArithmeticException | IllegalArgumentException failure) {
            sender.sendMessage("OmniPet: invalid item request - " + detail(failure) + ".");
        }
    }

    /**
     * Names and describes the item so its holder can tell what it is and how to redeem it.
     *
     * <p>Applied here rather than in the codec: the codec is driven in tests through a fake item
     * factory that carries no real meta, and its job is the PDC identity that {@code capture()}
     * validates. Display text is set before the identity is ever fingerprinted, so it cannot affect
     * escrow matching.
     */
    private static ItemStack label(ItemStack item, boolean reducer, long effectMillis) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(Messages.line(reducer
                ? MessageKey.GUI_REDUCER_ITEM_NAME : MessageKey.GUI_INSTANT_ITEM_NAME));
        meta.lore(List.of(
                reducer
                        ? Messages.line(MessageKey.GUI_REDUCER_ITEM_DETAIL,
                                Messages.of("detail", Durations.countdown(effectMillis)))
                        : Messages.line(MessageKey.GUI_INSTANT_ITEM_DETAIL),
                Component.empty(),
                Messages.line(reducer
                        ? MessageKey.GUI_REDUCER_ITEM_HINT : MessageKey.GUI_INSTANT_ITEM_HINT)));
        item.setItemMeta(meta);
        return item;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage("OmniPet: use /pet admin item reducer <online-player> <seconds> [amount] "
                + "or /pet admin item instant <online-player> [amount].");
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("incubation item commands require the Paper main thread");
    }

    private static String words(Enum<?> value) {
        return Displays.words(value);
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
