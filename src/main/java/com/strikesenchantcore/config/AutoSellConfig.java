package com.strikesenchantcore.config;

import com.strikesenchantcore.EnchantCore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull; // Import NotNull
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger

/**
 * Manages loading and accessing item sell prices from autosell.yml.
 */
public class AutoSellConfig {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    private File autoSellFile;
    private FileConfiguration autoSellConfig;

    // Cache sell prices: Material -> Price per item
    private final Map<Material, Double> sellPrices = new HashMap<>();
    // Default price for items not explicitly listed in the config
    private double defaultPrice = 0.0;

    public AutoSellConfig(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        setup();
        // load() is called by ConfigManager after construction
    }

    /**
     * Sets up the file and configuration object. Creates default if missing.
     */
    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        autoSellFile = new File(plugin.getDataFolder(), "autosell.yml");
        if (!autoSellFile.exists()) {
            plugin.saveResource("autosell.yml", false); // Copy default from JAR
            logger.info("Created default autosell.yml");
        }

        autoSellConfig = new YamlConfiguration();
        try {
            autoSellConfig.load(autoSellFile);
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Could not load autosell.yml during setup!", e);
        }
    }

    /**
     * Loads or reloads sell prices from autosell.yml into the cached map.
     * Called by ConfigManager.
     */
    public void load() {
        // Ensure file exists, attempt setup if not
        if (!autoSellFile.exists()) {
            logger.warning("autosell.yml not found! Attempting to recreate...");
            setup();
        } else {
            // Reload from disk if file exists
            try {
                autoSellConfig.load(autoSellFile);
            } catch (IOException | InvalidConfigurationException e) {
                logger.log(Level.SEVERE, "Could not reload autosell.yml! Using potentially stale prices.", e);
                // Avoid clearing prices if reload fails, keep using old ones
                return; // Stop loading process if reload fails
            }
        }

        // Clear old prices before loading new ones
        sellPrices.clear();
        int loadedCount = 0;
        final boolean debug = plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode(); // Check if debug enabled

        // Load prices from the "Prices" section
        ConfigurationSection pricesSection = autoSellConfig.getConfigurationSection("Prices");
        if (pricesSection != null) {
            for (String materialNameKey : pricesSection.getKeys(false)) {
                Material material = null;
                try {
                    // Use matchMaterial for flexibility (e.g., legacy support if needed)
                    material = Material.matchMaterial(materialNameKey.toUpperCase());

                    if (material != null && material != Material.AIR) { // Check if valid material and not AIR
                        double price = pricesSection.getDouble(materialNameKey, 0.0);
                        if (price > 0) {
                            sellPrices.put(material, price);
                            loadedCount++;
                            if(debug) logger.finest("[AutoSell] Loaded price for " + material.name() + ": " + price);
                        } else {
                            logger.warning("[AutoSell] Invalid price (<= 0) for material '" + materialNameKey + "' in autosell.yml. Skipping.");
                        }
                    } else {
                        // *** REMOVED isBlock() check to allow any material ***
                        logger.warning("[AutoSell] Invalid or unknown material name '" + materialNameKey + "' in autosell.yml. Skipping.");
                    }
                } catch (Exception e) { // Catch broader exceptions during parsing
                    logger.log(Level.SEVERE, "[AutoSell] Error processing material entry '" + materialNameKey + "' (Resolved as: " + (material != null ? material.name() : "null") + ") in autosell.yml.", e);
                }
            }
        } else {
            logger.warning("'Prices' section missing in autosell.yml. No specific item prices loaded.");
        }

        // Load default price (0.0 if not specified or invalid)
        defaultPrice = autoSellConfig.getDouble("DefaultPrice", 0.0);
        if (defaultPrice < 0) {
            logger.warning("DefaultPrice in autosell.yml cannot be negative. Setting to 0.0.");
            defaultPrice = 0.0;
        }

        logger.info("Loaded " + loadedCount + " item prices from autosell.yml. Default price: " + defaultPrice);
    }

    /**
     * Gets the configured sell price for a specific material.
     *
     * @param material The material to check the price for. Can be null.
     * @return The configured sell price, or the default price if not listed, or 0.0 if material is null or default price is non-positive.
     */
    public double getSellPrice(@Nullable Material material) {
        if (material == null || material == Material.AIR) {
            return 0.0; // Cannot sell null or air
        }
        // Return the specific price if found, otherwise return the default price (which might be 0)
        return sellPrices.getOrDefault(material, defaultPrice);
    }

    /**
     * Provides direct access to the FileConfiguration object for autosell.yml.
     * Use cautiously; prefer getSellPrice().
     *
     * @return The FileConfiguration for autosell.yml, or null if setup failed.
     */
    @Nullable
    public FileConfiguration getConfig() {
        return autoSellConfig;
    }
}