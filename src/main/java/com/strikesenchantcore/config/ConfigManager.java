package com.strikesenchantcore.config;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.enchants.EnchantRegistry; // Import EnchantRegistry
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger

/**
 * Manages loading config.yml, including CurrencyType and AutoSell interval.
 * Also coordinates the loading and reloading of other configuration managers.
 */
public class ConfigManager {

    // Enum for currency selection
    public enum CurrencyType { TOKENS, VAULT, GEMS, POINTS }

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    private File configFile;
    private FileConfiguration config;

    // --- Cached Settings ---
    private boolean debugMode = false;
    private CurrencyType currencyType = CurrencyType.TOKENS; // Default to TOKENS
    private int autoSellSummaryIntervalSeconds = 0; // Default to 0 (disabled)
    // --- End Cached Settings ---

    public ConfigManager(EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        setup(); // Create file/load initial config on startup
    }

    /**
     * Sets up the config file object and loads the configuration initially.
     * Creates default config.yml if it doesn't exist.
     */
    private void setup() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false); // Copy default from JAR
            logger.info("Created default config.yml");
        }
        config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Could not load config.yml during initial setup!", e);
            // Plugin might be in a partially broken state here. Consider fallback defaults.
        }
    }

    /**
     * Loads settings from config.yml into memory and triggers load methods
     * for other relevant configuration managers.
     */
    public void loadConfigs() {
        // Ensure file exists, attempt setup if not (e.g., deleted after startup)
        if (!configFile.exists()) {
            logger.warning("config.yml not found! Attempting to recreate...");
            setup();
            // If setup fails again, config will be null/empty, defaults will be used below.
        }

        try {
            config.load(configFile); // Reload from disk
            logger.info("Loaded settings from config.yml.");
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Could not reload config.yml! Using potentially stale values.", e);
            // Continue with potentially old config values if load fails, but log severity.
            // Avoid returning here, try to load other configs anyway if possible.
        }


        if (plugin.getSkinConfig() != null) {
            plugin.getSkinConfig().loadConfig();
        }

        // --- Load Core Settings ---
        debugMode = config.getBoolean("Settings.Debug", false);
        String currencyString = config.getString("Settings.CurrencyType", "TOKENS").toUpperCase();
        try {
            currencyType = CurrencyType.valueOf(currencyString);
        } catch (IllegalArgumentException e) {
            currencyType = CurrencyType.TOKENS; // Default on invalid value
            logger.warning("Invalid CurrencyType '" + currencyString + "' in config.yml. Defaulting to TOKENS.");
        }

        // Load AutoSell Interval
        // Use getInt which defaults to 0 if path is invalid or value is not an int
        autoSellSummaryIntervalSeconds = config.getInt("AutoSell.Summary-Interval", 0);
        if (autoSellSummaryIntervalSeconds < 0) {
            logger.warning("AutoSell Summary-Interval cannot be negative. Setting to 0 (disabled).");
            autoSellSummaryIntervalSeconds = 0;
        }
        // --- End Core Settings ---


        // --- Trigger Loading for Other Config Managers ---
        // Use try-catch for each dependent load to prevent one failure stopping others.
        // Add null checks for the manager instances themselves.
        PickaxeConfig pickaxeConf = plugin.getPickaxeConfig();
        if (pickaxeConf != null) {
            try { pickaxeConf.load(); } catch (Exception e) { logger.log(Level.SEVERE, "Error loading pickaxe.yml", e); }
        } else { logger.severe("Cannot load pickaxe.yml: PickaxeConfig instance is null!"); }

        EnchantManager enchantMgr = plugin.getEnchantManager();
        if (enchantMgr != null) {
            try { enchantMgr.load(); } catch (Exception e) { logger.log(Level.SEVERE, "Error loading enchants.yml", e); }
        } else { logger.severe("Cannot load enchants.yml: EnchantManager instance is null!"); }

        AutoSellConfig autoSellConf = plugin.getAutoSellConfig();
        if (autoSellConf != null) {
            try { autoSellConf.load(); } catch (Exception e) { logger.log(Level.SEVERE, "Error loading autosell.yml", e); }
        } else { logger.severe("Cannot load autosell.yml: AutoSellConfig instance is null!"); }
        // --- End Triggering ---

        logger.info("Core configurations processed. Debug: " + debugMode + ", Currency: " + currencyType + ", AutoSell Interval: " + autoSellSummaryIntervalSeconds + "s.");
        if (debugMode) { logger.info("[Debug] Debug mode is ENABLED."); }
    }

    /**
     * Reloads all configuration files managed by EnchantCore.
     * This includes config.yml, messages.yml, enchants.yml, pickaxe.yml, autosell.yml.
     */
    public void reloadConfigs() {
        logger.info("Reloading all EnchantCore configurations...");
        long startTime = System.nanoTime();

        // 1. Reload config.yml and trigger dependent loads (pickaxe, enchants, autosell)
        loadConfigs();

        // 2. Reload messages.yml
        MessageManager msgManager = plugin.getMessageManager();
        if (msgManager != null) {
            try { msgManager.reloadMessages(); } catch (Exception e) { logger.log(Level.SEVERE, "Error reloading messages.yml", e); }
        } else { logger.severe("Cannot reload messages.yml: MessageManager instance is null!"); }

        // 3. Reload enchantment definitions from enchants.yml into the registry
        EnchantRegistry enchRegistry = plugin.getEnchantRegistry();
        if (enchRegistry != null) {
            try { enchRegistry.loadEnchantsFromConfig(); } catch (Exception e) { logger.log(Level.SEVERE, "Error reloading enchantment definitions into registry", e); }
        } else { logger.severe("Cannot reload enchantment definitions: EnchantRegistry instance is null!"); }

        double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
        logger.info(String.format("All configurations reloaded (%.2f ms).", durationMs));
    }

    // --- Getters for Cached Settings ---
    public boolean isDebugMode() { return debugMode; }
    @NotNull public CurrencyType getCurrencyType() { return currencyType; } // Should always have a default
    public int getAutoSellSummaryIntervalSeconds() { return autoSellSummaryIntervalSeconds; }

    /**
     * Provides direct access to the loaded config.yml FileConfiguration.
     * Use cautiously; prefer specific getters where possible.
     * @return The FileConfiguration for config.yml, or null if loading failed critically.
     */
    @Nullable
    public FileConfiguration getConfig() { return config; }
}