package com.strikesenchantcore.util;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.PickaxeConfig;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.enchants.EnchantRegistry;
import com.strikesenchantcore.pickaxe.PickaxeManager;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PapiHook extends PlaceholderExpansion {

    private final EnchantCore plugin;
    private final Logger logger;
    private boolean isHooked = false;

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.0");
    private static final DecimalFormat BOOSTER_MULTIPLIER_FORMAT = new DecimalFormat("#0.0");
    private static final NumberFormat COMMA_SEPARATOR_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private static final long K = 1_000L;
    private static final long M = 1_000_000L;
    private static final long B = 1_000_000_000L;
    private static final long T = 1_000_000_000_000L;

    public PapiHook(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public boolean setupPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            isHooked = true;
            logger.info("PlaceholderAPI found.");
            return true;
        }
        isHooked = false;
        logger.info("PlaceholderAPI not found, placeholders will be unavailable.");
        return false;
    }

    public boolean registerPlaceholders() {
        if (isHooked) {
            try {
                boolean success = this.register();
                if (!success) {
                    logger.warning("PlaceholderAPI registration failed via this.register(). Placeholders may not work.");
                }
                return success;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "An error occurred during PlaceholderAPI registration", e);
                return false;
            }
        }
        return false;
    }

    public boolean isHooked() { return isHooked; }

    public String setPlaceholders(@Nullable OfflinePlayer player, @NotNull String text) {
        if (isHooked && text != null && player != null) {
            try {
                return PlaceholderAPI.setPlaceholders(player, text);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error applying PAPI placeholders for text: \"" + text + "\", Player: " + player.getName(), e);
                return text;
            }
        }
        return text;
    }

    @Override @NotNull public String getIdentifier() { return "enchantcore"; }
    @Override @NotNull public String getAuthor() { return String.join(", ", plugin.getDescription().getAuthors()); }
    @Override @NotNull public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    @Nullable
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (player == null) return null;

        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        PickaxeManager pickaxeManager = plugin.getPickaxeManager();
        PickaxeConfig pickaxeConfig = plugin.getPickaxeConfig();
        EnchantRegistry enchantRegistry = plugin.getEnchantRegistry();

        if (playerDataManager == null || pickaxeManager == null || pickaxeConfig == null || enchantRegistry == null) {
            return "Error: Managers Null";
        }

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null && player.isOnline()) {
            playerData = playerDataManager.loadPlayerData(player.getUniqueId());
        }

        String lowerIdentifier = identifier.toLowerCase();
        if (lowerIdentifier.equals("max_pickaxe_level")) {
            int maxLvlConf = pickaxeConfig.getMaxLevel();
            return (maxLvlConf > 0) ? String.valueOf(maxLvlConf) : "Unlimited";
        }

        if (playerData == null) {
            return null;
        }

        ItemStack pickaxe = null;
        if (player.isOnline() && player.getPlayer() != null) {
            pickaxe = pickaxeManager.findPickaxe(player.getPlayer());
        }

        int currentLvl = playerData.getPickaxeLevel();
        int maxLvl = pickaxeConfig.getMaxLevel();

        long requiredForCurrent = pickaxeManager.getBlocksRequiredForLevel(currentLvl);
        long requiredForNext = (maxLvl > 0 && currentLvl >= maxLvl) ? Long.MAX_VALUE : pickaxeManager.getBlocksRequiredForLevel(currentLvl + 1);

        switch (lowerIdentifier) {
            // --- Overcharge Placeholders ---
            case "overcharge_charge":
                return COMMA_SEPARATOR_FORMAT.format(playerData.getOverchargeCharge());
            case "overcharge_required": {
                if (pickaxe == null) return "0";
                int level = pickaxeManager.getEnchantLevel(pickaxe, "overcharge");
                if (level <= 0) return "0";
                EnchantmentWrapper ench = enchantRegistry.getEnchant("overcharge");
                if (ench == null || !ench.isEnabled() || ench.getCustomSettings() == null) return "0";

                ConfigurationSection s = ench.getCustomSettings();
                int base = s.getInt("BlocksToChargeBase", 500);
                int decrease = s.getInt("BlocksToChargeDecreasePerLevel", 10);
                int required = Math.max(1, base - (decrease * (level - 1)));
                return COMMA_SEPARATOR_FORMAT.format(required);
            }
            case "overcharge_status": {
                if (pickaxe == null) return "N/A";
                int level = pickaxeManager.getEnchantLevel(pickaxe, "overcharge");
                if (level <= 0) return "N/A";

                if (System.currentTimeMillis() < playerData.getOverchargeFireCooldownEnd()) {
                    return "Cooldown";
                }

                EnchantmentWrapper ench = enchantRegistry.getEnchant("overcharge");
                if (ench == null || !ench.isEnabled() || ench.getCustomSettings() == null) return "N/A";

                ConfigurationSection s = ench.getCustomSettings();
                int base = s.getInt("BlocksToChargeBase", 500);
                int decrease = s.getInt("BlocksToChargeDecreasePerLevel", 10);
                int required = Math.max(1, base - (decrease * (level - 1)));

                if (playerData.getOverchargeCharge() >= required) {
                    return "Ready";
                }
                return "Charging";
            }
            case "overcharge_cooldown_remaining": {
                long cooldownEnd = playerData.getOverchargeFireCooldownEnd();
                if (System.currentTimeMillis() >= cooldownEnd) {
                    return "0";
                }
                long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(cooldownEnd - System.currentTimeMillis()) + 1;
                return String.valueOf(remainingSeconds);
            }


            // --- Block Progress Placeholders ---
            case "progress": {
                if (maxLvl > 0 && currentLvl >= maxLvl) return "Max";
                if (requiredForCurrent < 0) return "Error";
                long blocksInLevel = Math.max(0, playerData.getBlocksMined() - requiredForCurrent);
                return COMMA_SEPARATOR_FORMAT.format(blocksInLevel);
            }
            case "needed": {
                if (maxLvl > 0 && currentLvl >= maxLvl) return "Max";
                if (requiredForNext == Long.MAX_VALUE) return "Max";
                if (requiredForCurrent < 0 || requiredForNext < 0 || requiredForNext <= requiredForCurrent) return "Error";
                long totalForLevel = Math.max(1, requiredForNext - requiredForCurrent);
                return COMMA_SEPARATOR_FORMAT.format(totalForLevel);
            }
            case "progress_percentage": {
                if (maxLvl > 0 && currentLvl >= maxLvl) return "100.0";
                if (requiredForNext == Long.MAX_VALUE) return "100.0";
                if (requiredForCurrent < 0 || requiredForNext < 0 || requiredForNext <= requiredForCurrent) return "Error";
                long blocksInLevel = Math.max(0, playerData.getBlocksMined() - requiredForCurrent);
                long totalForLevel = Math.max(1, requiredForNext - requiredForCurrent);
                double perc = (totalForLevel == 0) ? 100.0 : ((double) blocksInLevel / totalForLevel) * 100.0;
                return PERCENT_FORMAT.format(Math.min(100.0, Math.max(0.0, perc)));
            }
            case "blocks_required": {
                if (maxLvl > 0 && currentLvl >= maxLvl) return "Max";
                return requiredForNext == Long.MAX_VALUE ? "Max" : COMMA_SEPARATOR_FORMAT.format(requiredForNext);
            }

            // --- Standard Placeholders ---
            case "level": return String.valueOf(currentLvl);
            case "blocks_mined": return COMMA_SEPARATOR_FORMAT.format(playerData.getBlocksMined());
            case "tokens_raw": return String.valueOf(playerData.getTokens());
            case "tokens_formatted": return formatNumberFixed(playerData.getTokens());
            case "tokens_comma": return COMMA_SEPARATOR_FORMAT.format(playerData.getTokens());
            case "pickaxe_enchants_count": {
                return (pickaxe != null) ? String.valueOf(pickaxeManager.getAllEnchantLevels(pickaxe).size()) : "0";
            }
            case "messages_enabled_tf": return String.valueOf(playerData.isShowEnchantMessages());
            case "sounds_enabled_tf": return String.valueOf(playerData.isShowEnchantSounds());
            case "messages_enabled_status": return playerData.isShowEnchantMessages() ? "Enabled" : "Disabled";
            case "sounds_enabled_status": return playerData.isShowEnchantSounds() ? "Enabled" : "Disabled";
            case "blockbooster_active_tf": return String.valueOf(playerData.isBlockBoosterActive());
            case "blockbooster_active_status": return playerData.isBlockBoosterActive() ? "Active" : "Inactive";
            case "blockbooster_multiplier": {
                double multi = playerData.getBlockBoosterMultiplier();
                return "x" + BOOSTER_MULTIPLIER_FORMAT.format(multi);
            }
            case "blockbooster_time_remaining": return String.valueOf(playerData.getBlockBoosterRemainingSeconds());
            case "blockbooster_time_formatted": return formatTime(playerData.getBlockBoosterRemainingSeconds());

            default:
                if (lowerIdentifier.startsWith("enchant_level_")) {
                    String key = identifier.substring(14);
                    return (pickaxe != null) ? String.valueOf(pickaxeManager.getEnchantLevel(pickaxe, key)) : "0";
                }
                if (lowerIdentifier.startsWith("enchant_max_level_")) {
                    String key = identifier.substring(18);
                    EnchantmentWrapper ench = enchantRegistry.getEnchant(key);
                    return (ench != null && ench.getMaxLevel() > 0) ? String.valueOf(ench.getMaxLevel()) : "Unlimited";
                }
                // --- Pinata Health Placeholder ---
                if (lowerIdentifier.equals("enchant_pinata_health")) {
                    if (pickaxe == null) return "0";
                    int level = pickaxeManager.getEnchantLevel(pickaxe, "lootpinata");
                    if (level <= 0) return "0";
                    EnchantmentWrapper ench = enchantRegistry.getEnchant("lootpinata");
                    if (ench == null || !ench.isEnabled() || ench.getCustomSettings() == null) return "0";
                    ConfigurationSection s = ench.getCustomSettings();
                    int base = s.getInt("PinataHealthBase", 10);
                    int increase = s.getInt("PinataHealthIncreasePerLevel", 2);
                    return String.valueOf(base + (increase * (level - 1)));
                }
                return null;
        }
    }

    private String formatNumberFixed(long value) {
        if (value < K) return String.valueOf(value);
        if (value < M) return formatWithSuffix(value, K, "K");
        if (value < B) return formatWithSuffix(value, M, "M");
        if (value < T) return formatWithSuffix(value, B, "B");
        return formatWithSuffix(value, T, "T");
    }

    private String formatWithSuffix(long value, long divisor, String suffix) {
        if (value % divisor == 0) {
            return (value / divisor) + suffix;
        }
        return String.format(Locale.US, "%.1f%s", (double) value / divisor, suffix);
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() > 0) sb.append(seconds).append("s");
        else if (sb.length() == 0) return "0s";
        return sb.toString().trim();
    }
}