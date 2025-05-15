package com.strikesenchantcore.util;

import com.strikesenchantcore.EnchantCore;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull; // Import NotNull

import java.text.NumberFormat;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger

/**
 * Handles integration with the Vault API for economy features.
 * Provides safe methods for interacting with the economy.
 */
public class VaultHook {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    private Economy economy = null; // Vault Economy provider instance
    private boolean enabled = false; // Flag indicating successful hook

    public VaultHook(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        // Setup is called separately by EnchantCore main class after plugins are loaded
    }

    /**
     * Attempts to hook into the Vault economy service.
     * Should be called during plugin startup (onEnable).
     * @return True if successfully hooked, false otherwise.
     */
    public boolean setupEconomy() {
        // Check if Vault plugin is installed and enabled
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.info("Vault plugin not found. Vault economy features disabled.");
            enabled = false;
            return false;
        }

        // Try to get the Economy service provider from Vault
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("No Vault economy provider found (Economy service not registered). Vault features disabled.");
            enabled = false;
            return false;
        }

        // Get the actual Economy implementation
        economy = rsp.getProvider();
        enabled = (economy != null); // Set enabled flag based on success

        if (enabled) {
            logger.info("Vault Economy hooked successfully: Provider '" + economy.getName() + "' found.");
        } else {
            // Should not happen if rsp wasn't null, but safety check
            logger.severe("Failed to hook into Vault economy provider even though service registration was found!");
        }
        return enabled;
    }

    /**
     * Checks if the Vault hook is enabled and functional.
     * @return True if enabled, false otherwise.
     */
    public boolean isEnabled() {
        return enabled && economy != null;
    }

    /**
     * Gets the player's current balance safely.
     * @param player The player (online or offline) to check.
     * @return The player's balance, or 0.0 if Vault is disabled, player is null, or an error occurs.
     */
    public double getBalance(@NotNull OfflinePlayer player) {
        if (!isEnabled()) {
            // Log warning if trying to use when disabled? Optional.
            // logger.warning("Attempted to get balance but Vault is not enabled.");
            return 0.0;
        }
        // Removed null check for player as parameter is @NotNull now
        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            // Catch potential exceptions from the underlying economy plugin
            logger.log(Level.SEVERE, "Vault Error getting balance for player " + player.getName() + " (UUID: " + player.getUniqueId() + ")", e);
            return 0.0; // Return 0 on error
        }
    }

    /**
     * Checks if a player has enough money safely.
     * Uses economy.has() for online players, falls back to getBalance() for offline.
     * @param player The player (online or offline) to check.
     * @param amount The amount required.
     * @return True if the player has enough, false otherwise or on error.
     */
    public boolean hasEnough(@NotNull OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return false; // Cannot check if Vault isn't working
        }
        if (amount <= 0) return true; // Always "has enough" for zero or negative amounts

        try {
            // Use Vault's has() method for online players (might be more efficient/accurate depending on econ plugin)
            if (player.isOnline()) {
                return economy.has(player, amount);
            } else {
                // Fallback for offline players: Compare getBalance() result.
                // Some economy plugins might not support has() reliably for offline players.
                return getBalance(player) >= amount;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Vault Error checking if player " + player.getName() + " has " + amount, e);
            return false; // Assume false on error
        }
    }


    /**
     * Withdraws money from a player's account safely.
     * @param player The player (online or offline) to withdraw from.
     * @param amount The positive amount to withdraw.
     * @return True if the transaction was successful, false otherwise or on error.
     */
    public boolean withdraw(@NotNull OfflinePlayer player, double amount) {
        if (!isEnabled() || amount <= 0) { // Can't withdraw non-positive amounts
            return false;
        }
        try {
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            if (!response.transactionSuccess()) {
                // Log Vault's specific error message if withdrawal failed
                logger.warning("Vault withdraw failed for " + player.getName() + " (Amount: " + amount + "): " + response.errorMessage);
            }
            return response.transactionSuccess();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Vault Error withdrawing " + amount + " from player " + player.getName(), e);
            return false; // Return false on error
        }
    }

    /**
     * Deposits money into a player's account safely.
     * @param player The player (online or offline) to deposit into.
     * @param amount The positive amount to deposit.
     * @return True if the transaction was successful, false otherwise or on error.
     */
    public boolean deposit(@NotNull OfflinePlayer player, double amount) {
        if (!isEnabled() || amount <= 0) { // Can't deposit non-positive amounts
            return false;
        }
        try {
            EconomyResponse response = economy.depositPlayer(player, amount);
            if (!response.transactionSuccess()) {
                // Log Vault's specific error message if deposit failed
                logger.warning("Vault deposit failed for " + player.getName() + " (Amount: " + amount + "): " + response.errorMessage);
            }
            return response.transactionSuccess();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Vault Error depositing " + amount + " to player " + player.getName(), e);
            return false; // Return false on error
        }
    }

    /**
     * Formats a currency amount according to Vault's configured economy plugin.
     * Includes fallback formatting.
     * @param amount The amount to format.
     * @return The formatted currency string (e.g., "$1,000.00").
     */
    @NotNull
    public String format(double amount) {
        if (!isEnabled()) {
            // Fallback formatting if Vault is disabled - use standard US currency format
            try {
                return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
            } catch (Exception fallbackEx) {
                return String.format(Locale.US, "%.2f", amount); // Ultimate fallback
            }
        }
        try {
            // Use the economy provider's formatting method
            String formatted = economy.format(amount);
            // Ensure null isn't returned by a faulty econ plugin
            return (formatted != null) ? formatted : String.format(Locale.US, "%.2f", amount);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Vault Error formatting currency amount: " + amount, e);
            // Fallback formatting on error
            return String.format(Locale.US, "%.2f", amount);
        }
    }
}