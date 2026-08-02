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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionStage;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionTransaction;
import io.github.salyvn.omnipet.core.incubation.RepositoryHatchService;

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
                message(player, "No incubation is active.", NamedTextColor.YELLOW);
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
            message(player, "Incubation item: " + words(result.status()) + ".",
                    result.status() == IncubationItemActionResult.Status.COMMITTED
                            || result.status() == IncubationItemActionResult.Status.ALREADY_COMMITTED
                            ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
        } catch (IOException | RuntimeException failure) {
            message(player, "Incubation item failed: " + detail(failure) + ".", NamedTextColor.RED);
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
                case "reducer" -> codec.createReducer(Material.CLOCK, effectMillis);
                case "instant" -> codec.createInstantHatch(Material.NETHER_STAR);
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

    private static void usage(CommandSender sender) {
        sender.sendMessage("OmniPet: use /pet admin item reducer <online-player> <seconds> [amount] "
                + "or /pet admin item instant <online-player> [amount].");
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("incubation item commands require the Paper main thread");
    }

    private static void message(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text("OmniPet: " + text, color));
    }

    private static String words(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
