package io.github.salyvn.omnipet.paper.incubation.placed;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.salyvn.omnipet.paper.feedback.Feedback;
import io.github.salyvn.omnipet.paper.feedback.FeedbackEvent;
import io.github.salyvn.omnipet.paper.gui.egg.PlacedEggInventoryHolder;
import io.github.salyvn.omnipet.paper.gui.egg.PlacedEggMenuRenderer;
import io.github.salyvn.omnipet.paper.gui.egg.PlacedEggSupportOffer;
import io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationItemActionCodec;
import io.github.salyvn.omnipet.paper.text.Durations;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * Opens the placed-egg menu and spends support items into a placed egg's countdown.
 *
 * <p>Support items previously only reached a <em>held</em> incubation, so an egg on the ground could not be
 * hurried at all — the player's accelerator was useless against the very thing it looked like it was for.
 *
 * <p>Deliberately not routed through the escrow saga. Escrow's job is to prove a held item was paid for
 * against a {@code PlayerState} revision, and a placed egg has no such revision: it is its own durable
 * record, keyed by block. Reusing escrow would mean teaching the money-handling path about blocks. Instead
 * the order here is chosen so that no failure can both take the item and lose the effect:
 *
 * <ol>
 *   <li>read the record and the item,
 *   <li>write the reduced record to disk,
 *   <li>only then remove the item from the hand.
 * </ol>
 *
 * <p>A crash between 2 and 3 leaves the player with a spent-looking item they still own — they get the
 * effect for free. The reverse order would take the item and lose the effect, which is the failure that
 * actually costs someone something.
 */
public final class PlacedEggSupportController {
    private final PlacedEggCoordinator coordinator;
    private final PlacedEggView view;
    private final PaperIncubationItemActionCodec codec;
    private final PlacedEggMenuRenderer renderer = new PlacedEggMenuRenderer();

    public PlacedEggSupportController(
            PlacedEggCoordinator coordinator, PlacedEggView view, PaperIncubationItemActionCodec codec) {
        this.coordinator = Objects.requireNonNull(coordinator, "placed egg coordinator");
        this.view = Objects.requireNonNull(view, "placed egg view");
        this.codec = Objects.requireNonNull(codec, "incubation action item codec");
    }

    /** Shows the menu for the egg at a block. Does nothing when there is no record there. */
    public void open(Player player, Block block) {
        requireMainThread();
        Optional<PlacedEggRecord> record = coordinator.at(block);
        if (record.isEmpty()) return;
        open(player, record.get());
    }

    /**
     * Shows the menu for a record.
     *
     * <p>Anyone may look — the countdown is public information, and an egg in the world is visible anyway.
     * Only the owner gets buttons; see the renderer.
     */
    public void open(Player player, PlacedEggRecord record) {
        requireMainThread();
        boolean owner = record.ownerId().equals(player.getUniqueId());
        player.openInventory(renderer.render(
                player,
                record.key(),
                record.remainingMillis(),
                owner,
                offer(player.getInventory().getItemInMainHand(), record.remainingMillis()),
                offer(player.getInventory().getItemInOffHand(), record.remainingMillis())));
    }

    /** Routes one click. */
    public void click(
            Player player, PlacedEggInventoryHolder holder, PlacedEggInventoryHolder.Action action) {
        requireMainThread();
        switch (action.type()) {
            case HUB -> {
                player.closeInventory();
                player.performCommand("pet");
            }
            case REFRESH -> reopen(player, holder);
            case REDEEM -> spend(player, holder, action.offHand());
        }
    }

    /**
     * Re-reads the record and reopens, or reports that the egg is gone.
     *
     * <p>Also the path taken after a successful spend, so the countdown the player is looking at is the one
     * that was just written rather than the one the menu was built from.
     */
    private void reopen(Player player, PlacedEggInventoryHolder holder) {
        Optional<PlacedEggRecord> current = find(holder);
        if (current.isEmpty()) {
            player.closeInventory();
            player.sendMessage(Messages.line(MessageKey.PLACED_EGG_GONE));
            return;
        }
        open(player, current.get());
    }

    /**
     * Spends the item in one hand into the egg.
     *
     * <p>Every refusal is checked against freshly read state rather than against the menu the player
     * clicked, because a menu can be minutes old: the egg may have hatched, been broken, or already been
     * finished by the other hand.
     */
    private void spend(Player player, PlacedEggInventoryHolder holder, boolean offHand) {
        Optional<PlacedEggRecord> found = find(holder);
        if (found.isEmpty()) {
            player.closeInventory();
            player.sendMessage(Messages.line(MessageKey.PLACED_EGG_GONE));
            return;
        }
        PlacedEggRecord record = found.get();
        if (!record.ownerId().equals(player.getUniqueId())) {
            player.sendMessage(Messages.line(MessageKey.PLACED_EGG_NOT_OWNER));
            Feedback.blocked(player, FeedbackEvent.HATCH_REJECTED);
            return;
        }
        if (record.ready()) {
            player.sendMessage(Messages.line(MessageKey.PLACED_EGG_ALREADY_READY));
            reopen(player, holder);
            return;
        }
        ItemStack held = offHand
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        PlacedEggSupportOffer offer = offer(held, record.remainingMillis());
        if (!offer.usable()) {
            player.sendMessage(Messages.line(MessageKey.PLACED_EGG_NOTHING_TO_SPEND));
            Feedback.blocked(player, FeedbackEvent.HATCH_REJECTED);
            reopen(player, holder);
            return;
        }
        // Disk first, item second. See the class note: a crash between the two gives the player a free
        // effect, and the opposite order would take a paid item and deliver nothing.
        //
        // creditedMillis rather than the item's own figure, so an instant-hatch passes the record's
        // remaining time and the coordinator never has to interpret a sentinel.
        Optional<PlacedEggRecord> advanced =
                coordinator.applyReduction(record, offer.creditedMillis());
        if (advanced.isEmpty()) {
            player.sendMessage(Messages.line(MessageKey.PLACED_EGG_SPEND_FAILED,
                    Messages.of("detail", "the egg could not be updated")));
            Feedback.failure(player, FeedbackEvent.HATCH_REJECTED);
            return;
        }
        consumeOne(player, offHand);
        view.refresh(advanced.get());
        report(player, offer, advanced.get());
        reopen(player, holder);
    }

    /** Says what changed: how much came off, and what is left — or that the egg is now waiting. */
    private static void report(Player player, PlacedEggSupportOffer offer, PlacedEggRecord advanced) {
        if (advanced.ready()) {
            player.sendMessage(Messages.line(MessageKey.PLACED_EGG_FINISHED));
            Feedback.success(player, FeedbackEvent.HATCH_QUEUED);
            return;
        }
        player.sendMessage(Messages.line(MessageKey.PLACED_EGG_REDUCED,
                Messages.of("detail", Durations.countdown(offer.creditedMillis())),
                Messages.of("remaining", Durations.countdown(advanced.remainingMillis()))));
        Feedback.success(player, FeedbackEvent.HATCH_QUEUED);
    }

    /** Takes exactly one item off the stack in that hand. */
    private static void consumeOne(Player player, boolean offHand) {
        ItemStack held = offHand
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        ItemStack next = held.clone();
        next.setAmount(next.getAmount() - 1);
        ItemStack normalized = next.getAmount() < 1 || next.getType() == Material.AIR ? null : next;
        if (offHand) player.getInventory().setItemInOffHand(normalized);
        else player.getInventory().setItemInMainHand(normalized);
    }

    /** What one held stack would do to an egg with this much time left. */
    private PlacedEggSupportOffer offer(ItemStack held, long remainingMillis) {
        if (held == null || held.getType() == Material.AIR || held.getAmount() < 1) {
            return PlacedEggSupportOffer.empty(remainingMillis);
        }
        return codec.effect(held)
                .map(effect -> effect.instant()
                        ? PlacedEggSupportOffer.instant(remainingMillis)
                        : PlacedEggSupportOffer.reducer(effect.effectMillis(), remainingMillis))
                .orElseGet(() -> PlacedEggSupportOffer.unsupported(remainingMillis));
    }

    /** The record the open menu refers to, re-read rather than remembered. */
    private Optional<PlacedEggRecord> find(PlacedEggInventoryHolder holder) {
        return coordinator.byKey(holder.recordKey());
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("placed egg menus require the Paper main thread");
        }
    }
}
