package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Map;
import java.util.function.Predicate;

import io.github.salyvn.omnipet.core.economy.EconomyAmount;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;

/**
 * How many active slots a player has, how many they could still buy, and what the next one costs.
 *
 * <p>Exists so the vault can show a locked slot instead of only offering a button that leads somewhere
 * else. A player previously had to open the purchase menu to discover whether anything was left to buy,
 * what it cost, or whether they were even permitted to buy it.
 *
 * <p>A display estimate, deliberately. {@link #nextSlot()} is derived from the <em>effective</em> slot
 * count, while the purchase flow prices the slot after the <em>persisted</em> count and refuses when an
 * external entitlement provider disagrees with it. Those two can differ under a LuckPerms-authoritative
 * policy, so this record promises a price a player is likely to be offered rather than one they are
 * guaranteed; the purchase flow remains the only thing that decides. Nothing here spends money.
 *
 * <p>Pure and Bukkit-free so the lock arithmetic is unit-testable without a server.
 */
public record ActiveSlotStatus(
        int unlocked,
        int max,
        int nextSlot,
        boolean eligible,
        boolean multiPetEnabled,
        Map<EconomyProvider, EconomyAmount> costs) {

    public ActiveSlotStatus {
        if (unlocked < 0) throw new IllegalArgumentException("unlocked slot count cannot be negative");
        if (max < 0) throw new IllegalArgumentException("maximum slot count cannot be negative");
        if (nextSlot < 0) throw new IllegalArgumentException("next slot cannot be negative");
        costs = Map.copyOf(costs == null ? Map.of() : costs);
    }

    /**
     * The lock state the vault should draw for one player.
     *
     * <p>Reads the config rather than being handed a slot number so that "what comes next" is decided in
     * one place. A slot with no {@code unlocks} entry is not for sale, which is how an operator caps
     * purchases below {@code max} without editing the cap.
     */
    public static ActiveSlotStatus of(
            Phase4PaperConfig.ActiveSlots config, int unlockedCount, Predicate<String> hasPermission) {
        if (config == null) return unknown(unlockedCount);
        int unlocked = Math.max(0, unlockedCount);
        int max = config.max();
        // Multi-pet off pins every player to one active pet, so a purchase would buy nothing visible.
        if (!config.multiPetEnabled()) {
            return new ActiveSlotStatus(unlocked, max, 0, false, false, Map.of());
        }
        int candidate = unlocked + 1;
        Phase4PaperConfig.SlotUnlock unlock = candidate > max ? null : config.unlock(candidate).orElse(null);
        if (unlock == null) return new ActiveSlotStatus(unlocked, max, 0, false, true, Map.of());
        boolean eligible = hasPermission == null || unlock.eligible(hasPermission);
        return new ActiveSlotStatus(unlocked, max, candidate, eligible, true, unlock.costs());
    }

    /**
     * The state to draw when the config is not in hand.
     *
     * <p>Keeps the older three-argument render path working: it reports the count it can see and offers
     * nothing for sale, rather than inventing a price.
     */
    public static ActiveSlotStatus unknown(int unlockedCount) {
        int unlocked = Math.max(0, unlockedCount);
        return new ActiveSlotStatus(unlocked, unlocked, 0, false, true, Map.of());
    }

    /** Slots the cap allows that the player does not have. */
    public int locked() {
        return Math.max(0, max - unlocked);
    }

    /** True when a click could plausibly buy something, price included. */
    public boolean purchasable() {
        return nextSlot > 0 && eligible && !costs.isEmpty();
    }

    /** True when a slot is priced but the player lacks the permission that gates it. */
    public boolean blockedByPermission() {
        return nextSlot > 0 && !eligible;
    }

    /** True when the cap is reached or nothing further is priced. */
    public boolean exhausted() {
        return nextSlot == 0;
    }
}
