package com.strikesenchantcore.config;

import com.strikesenchantcore.EnchantCore;
// Uses ColorUtils now via ChatUtil delegation or directly
import com.strikesenchantcore.util.ColorUtils;
import com.strikesenchantcore.util.ChatUtil; // Keep for utility if needed elsewhere

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull; // Import NotNull
import org.jetbrains.annotations.Nullable; // Import Nullable

import java.io.File;
import java.io.IOException;
import java.util.*; // Import necessary java.util classes
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger

/**
 * Manages loading and accessing settings from the 'EnchantGUI' section of enchants.yml.
 * Also provides access to the main 'enchants' configuration section for the EnchantRegistry.
 * Stores raw format strings (with color codes); translation is handled by consumers.
 */
public class EnchantManager {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    private File enchantsFile;
    private FileConfiguration enchantsConfig;

    // --- Cached GUI Settings (Raw formats, colors translated on use) ---
    private String guiTitleFormat = "&1&lPickaxe Enchantments"; // Default title format
    private int guiSize = 54; // Default size
    private boolean guiFillEmptySlots = true; // Default filler enabled
    private Material guiFillerMaterial = Material.GRAY_STAINED_GLASS_PANE; // Default filler material
    private String guiFillerNameFormat = " "; // Default filler name format
    private int guiFillerCustomModelData = 0; // Default filler model data
    // Stores the raw ConfigurationSections for static GUI items defined under EnchantGUI.Items
    private Map<String, ConfigurationSection> guiStaticItems = new HashMap<>();
    // --- End GUI Settings ---

    public EnchantManager(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        setup();
        // Note: load() is typically called by ConfigManager after all managers are constructed
    }

    /**
     * Sets up the enchants.yml file object and configuration instance.
     * Creates the default file if it doesn't exist.
     */
    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs(); // Ensure parent data folder exists
        }
        enchantsFile = new File(plugin.getDataFolder(), "enchants.yml");
        if (!enchantsFile.exists()) {
            plugin.saveResource("enchants.yml", false); // Copy default from JAR
            logger.info("Created default enchants.yml");
        }

        enchantsConfig = new YamlConfiguration();
        try {
            enchantsConfig.load(enchantsFile);
            logger.info("Initial load of enchants.yml successful.");
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Could not load enchants.yml during setup!", e);
            // Configuration might be invalid, proceed with defaults where possible
        }
    }

    /**
     * Loads GUI settings values from the enchants.yml file into memory.
     * Called by ConfigManager.loadConfigs().
     */
    public void load() {
        // Ensure file exists, attempt setup if not (e.g., deleted after startup)
        if (!enchantsFile.exists()) {
            logger.warning("enchants.yml not found! Attempting to recreate...");
            setup(); // This will load it if created successfully
            // If setup fails again, enchantsConfig might be empty or null, defaults below will be used.
        } else {
            // If file exists, reload it from disk
            try {
                enchantsConfig.load(enchantsFile);
                logger.info("Loaded/Reloaded settings from enchants.yml.");
            } catch (IOException | InvalidConfigurationException e) {
                logger.log(Level.SEVERE, "Could not reload enchants.yml! Using potentially stale values for GUI.", e);
                // Don't return; proceed with potentially old values or defaults.
            }
        }


        // Load GUI Settings section
        ConfigurationSection guiSection = enchantsConfig.getConfigurationSection("EnchantGUI");

        if (guiSection != null) {
            // Store titles/names WITH format codes; translate when needed using ColorUtils
            guiTitleFormat = guiSection.getString("Title", "&1&lPickaxe Enchantments");
            guiSize = guiSection.getInt("Size", 54);
            // Validate GUI size (must be multiple of 9, between 9 and 54)
            if (guiSize <= 0 || guiSize > 54 || guiSize % 9 != 0) {
                logger.warning("Invalid GUI Size (" + guiSize + ") in enchants.yml. Must be multiple of 9 up to 54. Using 54.");
                guiSize = 54; // Force default valid size
            }

            guiFillEmptySlots = guiSection.getBoolean("FillEmptySlots", true);
            try {
                String matName = guiSection.getString("FillerMaterial", "GRAY_STAINED_GLASS_PANE").toUpperCase();
                guiFillerMaterial = Material.valueOf(matName);
                if (guiFillerMaterial == Material.AIR) {
                    logger.warning("FillerMaterial cannot be AIR in enchants.yml. Using GRAY_STAINED_GLASS_PANE.");
                    guiFillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid FillerMaterial in enchants.yml: " + guiSection.getString("FillerMaterial") + ". Using GRAY_STAINED_GLASS_PANE.");
                guiFillerMaterial = Material.GRAY_STAINED_GLASS_PANE; // Fallback
            }
            guiFillerNameFormat = guiSection.getString("FillerName", " "); // Store raw name format
            guiFillerCustomModelData = guiSection.getInt("FillerCustomModelData", 0);

            // Load static GUI items configuration sections
            guiStaticItems.clear(); // Clear previous static items
            ConfigurationSection itemsSection = guiSection.getConfigurationSection("Items");
            if (itemsSection != null) {
                for (String key : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                    if (itemSec != null) {
                        guiStaticItems.put(key, itemSec); // Store the whole section for the GUI builder
                    } else {
                        logger.warning("Invalid configuration for static GUI item '" + key + "' under EnchantGUI.Items in enchants.yml.");
                    }
                }
                if (plugin.getConfigManager().isDebugMode()) {
                    logger.fine("Loaded " + guiStaticItems.size() + " static GUI item definitions.");
                }
            }

        } else {
            // GUI section missing, use hardcoded defaults and log a warning
            logger.warning("'EnchantGUI' section missing in enchants.yml. Using default GUI settings.");
            // Reset all GUI settings to their defaults
            guiTitleFormat = "&1&lPickaxe Enchantments";
            guiSize = 54;
            guiFillEmptySlots = true;
            guiFillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
            guiFillerNameFormat = " ";
            guiFillerCustomModelData = 0;
            guiStaticItems.clear();
        }

        // Note: Loading of individual enchantments definitions is handled by EnchantRegistry
        // which calls getEnchantsSection() on this manager.
        logger.info("EnchantManager processed GUI settings from enchants.yml.");
    }

    /**
     * Gets the main "enchants" configuration section from the loaded enchants.yml.
     * This section contains the definitions for all individual enchantments.
     * Assumes the configuration has already been loaded by load().
     *
     * @return The ConfigurationSection for "enchants", or null if the config wasn't loaded or the section is missing.
     */
    @Nullable
    public ConfigurationSection getEnchantsSection() {
        if (enchantsConfig == null) {
            logger.severe("getEnchantsSection called but enchantsConfig is null! Was setup/load skipped or failed?");
            return null;
        }
        // *** REMOVED Redundant File Load ***
        // The config should be in memory from the initial load or reload triggered by ConfigManager.
        return enchantsConfig.getConfigurationSection("enchants");
    }

    // --- Getters for GUI settings ---
    // Note: Getters return the values as stored (potentially with format codes).
    // The GUI class should use ColorUtils.translateColors() when displaying them.

    public String getGuiTitleFormat() { return guiTitleFormat; }
    public int getGuiSize() { return guiSize; }
    public boolean isGuiFillEmptySlots() { return guiFillEmptySlots; }
    @NotNull public Material getGuiFillerMaterial() { return guiFillerMaterial; } // Should have a default
    public String getGuiFillerNameFormat() { return guiFillerNameFormat; }
    public int getGuiFillerCustomModelData() { return guiFillerCustomModelData; }

    /**
     * Gets the configuration sections for static items defined under EnchantGUI.Items.
     * The key is the identifier used in enchants.yml (e.g., 'border', 'player_info').
     * Assumes the configuration has been loaded.
     *
     * @return An unmodifiable map of static item identifiers to their ConfigurationSections.
     */
    @NotNull
    public Map<String, ConfigurationSection> getGuiStaticItems() {
        // Return an unmodifiable view to prevent external modification
        return Collections.unmodifiableMap(guiStaticItems);
    }

    /**
     * Provides direct access to the loaded FileConfiguration object for enchants.yml.
     * Use cautiously; prefer specific getter methods or getEnchantsSection().
     * Assumes the configuration has been loaded.
     *
     * @return The FileConfiguration for enchants.yml, or null if setup/load failed.
     */
    @Nullable
    public FileConfiguration getConfig() {
        return enchantsConfig;
    }
} // End of EnchantManager class