package com.strikesenchantcore.util;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.PickaxeConfig;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager; // Import PlayerDataManager
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.enchants.EnchantRegistry; // Import EnchantRegistry
import com.strikesenchantcore.pickaxe.PickaxeManager; // Import PickaxeManager
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.logging.Level;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * PlaceholderAPI Expansion for EnchantCore.
 * Provides placeholders for pickaxe stats, player data, and enchant levels.
 */
public class PapiHook extends PlaceholderExpansion {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    private boolean isHooked = false;

    // --- Formatting Helpers (Static for efficiency) ---
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.0"); // For percentages
    private static final DecimalFormat BOOSTER_MULTIPLIER_FORMAT = new DecimalFormat("#0.0"); // For booster display
    private static final NumberFormat COMMA_SEPARATOR_FORMAT = NumberFormat.getNumberInstance(Locale.US); // For large numbers with commas

    // Suffixes for number formatting (K, M, B, T)
    private static final long K = 1_000L;
    private static final long M = 1_000_000L;
    private static final long B = 1_000_000_000L;
    private static final long T = 1_000_000_000_000L;
    // --- End Formatting Helpers ---

    public PapiHook(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    // --- Standard PlaceholderExpansion Methods ---
    /** Attempts to hook into PlaceholderAPI and register this expansion. */
    public boolean setupPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            isHooked = true;
            // Registration is handled by registerPlaceholders now for clarity
            // this.register(); // Don't register here, call registerPlaceholders separately
            logger.info("PlaceholderAPI found.");
            return true;
        }
        isHooked = false;
        logger.info("PlaceholderAPI not found, placeholders will be unavailable.");
        return false;
    }

    /** Registers this expansion with PlaceholderAPI if hooked. */
    public boolean registerPlaceholders() {
        if (isHooked) {
            try {
                boolean success = this.register(); // PAPI's register method
                if (!success) {
                    logger.warning("PlaceholderAPI registration failed via this.register(). Placeholders may not work.");
                }
                return success;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "An error occurred during PlaceholderAPI registration", e);
                return false;
            }
        }
        return false; // Not hooked
    }

    /** Checks if the hook to PlaceholderAPI is active. */
    public boolean isHooked() { return isHooked; }

    /** Convenience method to set placeholders using PAPI if available. */
    public String setPlaceholders(@Nullable OfflinePlayer player, @NotNull String text) {
        if (isHooked && text != null && player != null) { // Check player not null too
            try {
                return PlaceholderAPI.setPlaceholders(player, text);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error applying PAPI placeholders for text: \"" + text + "\", Player: " + player.getName(), e);
                return text; // Return original text on error
            }
        }
        return text; // Return original text if not hooked or input invalid
    }

    @Override @NotNull public String getIdentifier() { return "enchantcore"; } // Lowercase identifier
    @Override @NotNull public String getAuthor() { return String.join(", ", plugin.getDescription().getAuthors()); } // Join authors list
    @Override @NotNull public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; } // Keep expansion registered
    @Override public boolean canRegister() { return true; } // Required by PAPI

    // --- End Standard Methods ---


    /**
     * Handles specific placeholder requests for `%enchantcore_<identifier>%`.
     * @param player The player context (can be offline).
     * @param identifier The placeholder requested (e.g., "level", "tokens_formatted").
     * @return The processed placeholder value, or null if invalid.
     */
    @Override
    @Nullable
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (player == null) return null; // Need player context

        // --- Get Managers and Core Data ---
        // Retrieve managers - add null checks for safety
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        PickaxeManager pickaxeManager = plugin.getPickaxeManager();
        PickaxeConfig pickaxeConfig = plugin.getPickaxeConfig();
        EnchantRegistry enchantRegistry = plugin.getEnchantRegistry();

        if (playerDataManager == null || pickaxeManager == null || pickaxeConfig == null || enchantRegistry == null) {
            logger.severe("[PAPI ERROR] Core manager(s) null in onRequest for identifier: " + identifier);
            return "Error: Managers Null"; // Indicate internal error
        }

        // Load PlayerData (tries cache first, then file)
        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null && player.isOnline()) { // Only load from file if online and not cached
            playerData = playerDataManager.loadPlayerData(player.getUniqueId());
        }

        // Allow checking max level even if playerdata failed load (useful for static displays)
        String lowerIdentifier = identifier.toLowerCase();
        if (lowerIdentifier.equals("max_pickaxe_level")) {
            int maxLvlConf = pickaxeConfig.getMaxLevel();
            return (maxLvlConf > 0) ? String.valueOf(maxLvlConf) : "Unlimited";
        }

        // If PlayerData is still null, most placeholders cannot be resolved
        if (playerData == null) {
            // logger.warning("[PAPI] PlayerData null for player " + player.getName() + " requesting placeholder: " + identifier);
            return null; // Or return a default like "Loading..." or "N/A"? Null is standard.
        }

        // Get pickaxe only if player is online (needed for enchant level placeholders)
        ItemStack pickaxe = null;
        if (player.isOnline()) {
            pickaxe = pickaxeManager.findPickaxe(player.getPlayer()); // Find pickaxe in online player's inv
        }

        // Get current level and max level from data/config
        int currentLvl = playerData.getPickaxeLevel();
        int maxLvl = pickaxeConfig.getMaxLevel();

        // --- Optimization: Calculate required blocks ONCE ---
        long requiredForCurrent = -1, requiredForNext = -1;
        if (!lowerIdentifier.startsWith("enchant_")) { // Only calculate if needed by common placeholders
            requiredForCurrent = pickaxeManager.getBlocksRequiredForLevel(currentLvl);
            requiredForNext = (maxLvl > 0 && currentLvl >= maxLvl) ? Long.MAX_VALUE : pickaxeManager.getBlocksRequiredForLevel(currentLvl + 1);
        }
        // --- End Optimization ---

        // --- Placeholder Logic ---
        switch (lowerIdentifier) {
            // --- Block Progress Placeholders (Using pre-calculated values) ---
            case "progress": { // Replaces pickaxe_blocks_progress
                if (maxLvl > 0 && currentLvl >= maxLvl) return "Max"; // Already maxed
                if (requiredForCurrent < 0) return "Error"; // Error calculating requirement
                long blocksInLevel = Math.max(0, playerData.getBlocksMined() - requiredForCurrent);
                return COMMA_SEPARATOR_FORMAT.format(blocksInLevel);
            }
            case "needed": { // Replaces pickaxe_blocks_needed_for_level
                if (maxLvl > 0 && currentLvl >= maxLvl) return "Max";
                if (requiredForNext == Long.MAX_VALUE) return "Max"; // Next level is max/unreachable
                if (requiredForCurrent < 0 || requiredForNext < 0 || requiredForNext <= requiredForCurrent) return "Error"; // Calculation error
                long totalForLevel = Math.max(1, requiredForNext - requiredForCurrent); // Min 1 block needed
                return COMMA_SEPARATOR_FORMAT.format(totalForLevel);
            }
            case "progress_percentage": {
                if (maxLvl > 0 && currentLvl >= maxLvl) return "100.0"; // Maxed out
                if (requiredForNext == Long.MAX_VALUE) return "100.0"; // Effectively maxed if next is unreachable
                if (requiredForCurrent < 0 || requiredForNext < 0 || requiredForNext <= requiredForCurrent) return "Error"; // Calc error
                long blocksInLevel = Math.max(0, playerData.getBlocksMined() - requiredForCurrent);
                long totalForLevel = Math.max(1, requiredForNext - requiredForCurrent);
                double perc = (totalForLevel == 0) ? 100.0 : ((double) blocksInLevel / totalForLevel) * 100.0;
                return PERCENT_FORMAT.format(Math.min(100.0, Math.max(0.0, perc))); // Format and clamp [0, 100]
            }
            case "blocks_required": { // Total blocks for NEXT level
                if (maxLvl > 0 && currentLvl >= maxLvl) return "Max";
                // Use pre-calculated value
                return requiredForNext == Long.MAX_VALUE ? "Max" : COMMA_SEPARATOR_FORMAT.format(requiredForNext);
            }

            // --- Standard Placeholders ---
            case "level": return String.valueOf(currentLvl);
            case "blocks_mined": return COMMA_SEPARATOR_FORMAT.format(playerData.getBlocksMined());
            case "tokens_raw": return String.valueOf(playerData.getTokens());
            case "tokens_formatted": return formatNumberFixed(playerData.getTokens()); // Abbreviated (K, M, B, T)
            case "tokens_comma": return COMMA_SEPARATOR_FORMAT.format(playerData.getTokens()); // Comma separated
            // max_pickaxe_level handled above playerData check
            case "pickaxe_enchants_count": {
                // Requires online player and found pickaxe
                return (pickaxe != null) ? String.valueOf(pickaxeManager.getAllEnchantLevels(pickaxe).size()) : "0";
            }
            // Toggle Statuses
            case "messages_enabled_tf": return String.valueOf(playerData.isShowEnchantMessages());
            case "sounds_enabled_tf": return String.valueOf(playerData.isShowEnchantSounds());
            case "messages_enabled_status": return playerData.isShowEnchantMessages() ? "Enabled" : "Disabled"; // Configurable?
            case "sounds_enabled_status": return playerData.isShowEnchantSounds() ? "Enabled" : "Disabled"; // Configurable?
            // Booster Statuses
            case "blockbooster_active_tf": return String.valueOf(playerData.isBlockBoosterActive());
            case "blockbooster_active_status": return playerData.isBlockBoosterActive() ? "Active" : "Inactive"; // Configurable?
            case "blockbooster_multiplier": {
                double multi = playerData.getBlockBoosterMultiplier(); // Gets 1.0 if inactive
                return "x" + BOOSTER_MULTIPLIER_FORMAT.format(multi);
            }
            case "blockbooster_time_remaining": return String.valueOf(playerData.getBlockBoosterRemainingSeconds());
            case "blockbooster_time_formatted": return formatTime(playerData.getBlockBoosterRemainingSeconds());

            // --- Dynamic Enchant Placeholders ---
            default:
                // enchant_level_<key>
                if (lowerIdentifier.startsWith("enchant_level_")) {
                    String key = identifier.substring(14);
                    // Requires online player and found pickaxe
                    return (pickaxe != null) ? String.valueOf(pickaxeManager.getEnchantLevel(pickaxe, key)) : "0";
                }
                // enchant_max_level_<key>
                if (lowerIdentifier.startsWith("enchant_max_level_")) {
                    String key = identifier.substring(18);
                    EnchantmentWrapper ench = enchantRegistry.getEnchant(key); // Lookup enchant definition
                    return (ench != null && ench.getMaxLevel() > 0) ? String.valueOf(ench.getMaxLevel()) : "Unlimited";
                }
                // --- Add other dynamic placeholders here ---

                return null; // Unknown placeholder identifier
        }
    } // End of onRequest method


    // --- Helper Methods for Formatting ---

    /** Formats large numbers with suffixes K, M, B, T. */
    private String formatNumberFixed(long value) {
        if (value < K) return String.valueOf(value); // No suffix needed
        if (value < M) return formatWithSuffix(value, K, "K");
        if (value < B) return formatWithSuffix(value, M, "M");
        if (value < T) return formatWithSuffix(value, B, "B");
        return formatWithSuffix(value, T, "T");
    }

    /** Helper for formatNumberFixed */
    private String formatWithSuffix(long value, long divisor, String suffix) {
        // Check if the number is a whole multiple of the divisor (e.g., exactly 5K, 10M)
        if (value % divisor == 0) {
            return (value / divisor) + suffix; // Return integer + suffix (e.g., "5K")
        }
        // Otherwise, return with one decimal place (e.g., "5.2K")
        // Use Locale.US to ensure dot (.) as decimal separator
        return String.format(Locale.US, "%.1f%s", (double) value / divisor, suffix);
    }

    /** Formats total seconds into Hh MMm SSs format. */
    private String formatTime(int totalSeconds) {
        if (totalSeconds <= 0) return "0s";

        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) { // Include minutes if hours > 0 or minutes > 0
            // Format minutes with leading zero only if hours are present
            // sb.append(String.format(hours > 0 ? "%02d" : "%d", minutes)).append("m "); // Optional zero padding
            sb.append(minutes).append("m "); // Simpler: just minutes + m
        }
        // Always include seconds if > 0 or if hours/minutes are present
        if (seconds > 0 || sb.length() > 0) {
            // Format seconds with leading zero only if hours or minutes are present
            // sb.append(String.format(sb.length() > 0 ? "%02d" : "%d", seconds)).append("s"); // Optional zero padding
            sb.append(seconds).append("s"); // Simpler: just seconds + s
        } else if (sb.length() == 0) {
            // Ensure "0s" is returned if totalSeconds was > 0 but resulted in 0h 0m 0s somehow
            return "0s";
        }

        return sb.toString().trim(); // Trim trailing space if any
    }

} // End of PapiHook class