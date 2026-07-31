package net.milkbowl.vault.economy;

import org.bukkit.OfflinePlayer;

public interface Economy {
    EconomyResponse withdrawPlayer(OfflinePlayer player, double amount);

    EconomyResponse withdrawPlayer(String player, double amount);

    EconomyResponse depositPlayer(OfflinePlayer player, double amount);

    EconomyResponse depositPlayer(String player, double amount);

    double getBalance(OfflinePlayer player);

    double getBalance(String player);
}
