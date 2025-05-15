package com.strikesenchantcore.enchants;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.EnchantManager; // Import EnchantManager
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull; // Import NotNull
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap; // Ensures insertion order is maintained
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger

/**
 * Loads, stores, and provides access to all defined EnchantmentWrapper objects
 * based on the configuration retrieved from EnchantManager.
 */
public class EnchantRegistry {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    // Use LinkedHashMap to preserve the order from enchants.yml if needed for GUI ordering etc.
    private final Map<String, EnchantmentWrapper> registeredEnchants = new LinkedHashMap<>();

    public EnchantRegistry(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Clears existing enchantments and loads/reloads all enabled enchantments
     * from the configuration provided by EnchantManager.
     * Should be called on plugin enable and reload (triggered by ConfigManager).
     */
    public void loadEnchantsFromConfig() {
        registeredEnchants.clear(); // Clear previous entries before reload
        final boolean debug = plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode(); // Check debug status

        // Get the 'enchants' section from the loaded enchants.yml via EnchantManager
        EnchantManager enchantManager = plugin.getEnchantManager();
        if (enchantManager == null) {
            logger.severe("Cannot load enchants: EnchantManager instance is null!");
            return;
        }
        ConfigurationSection enchantsSection = enchantManager.getEnchantsSection();

        if (enchantsSection == null) {
            logger.severe("The 'enchants' section is missing in enchants.yml! No enchantments loaded.");
            return; // Cannot proceed without the main section
        }

        logger.info("Loading enchantments from configuration...");
        int loadedCount = 0;
        int disabledCount = 0;

        // Iterate through each key under the 'enchants' section (e.g., 'efficiency', 'explosive')
        for (String configKey : enchantsSection.getKeys(false)) {
            ConfigurationSection enchantConfig = enchantsSection.getConfigurationSection(configKey);
            if (enchantConfig == null) {
                logger.warning("Invalid configuration structure for enchantment key '" + configKey + "' in enchants.yml (expected a section, found something else?). Skipping.");
                continue;
            }

            try {
                // Create a wrapper object for this enchantment's configuration
                EnchantmentWrapper wrapper = new EnchantmentWrapper(plugin, configKey, enchantConfig);

                // Only register if the enchant is marked as enabled in its config
                if (wrapper.isEnabled()) {
                    // Use the unique RawName (e.g., "efficiency") as the map key for reliable lookup
                    String rawNameKey = wrapper.getRawName().toLowerCase(); // Use lowercase for case-insensitive lookup

                    if (registeredEnchants.containsKey(rawNameKey)) {
                        // Warn if an enchant with the same RawName is already registered
                        logger.warning("Duplicate RawName '" + rawNameKey + "' detected! Config key '" + configKey
                                + "' is overwriting previous entry. Check enchants.yml for potential conflicts.");
                    }
                    registeredEnchants.put(rawNameKey, wrapper);
                    loadedCount++;
                } else {
                    // Keep track of disabled enchants for logging purposes
                    disabledCount++;
                    if (debug) {
                        logger.info("[Debug] Enchantment '" + wrapper.getRawName() + "' (Config Key: " + configKey + ") is disabled in config.");
                    }
                }
            } catch (Exception e) {
                // Catch potential errors during individual enchantment wrapper creation/loading
                logger.log(Level.SEVERE, "Failed to load enchantment configuration for key: '" + configKey + "'", e);
                // Continue loading other enchants
            }
        }

        logger.info("Loaded " + loadedCount + " enabled enchantments (" + disabledCount + " disabled).");
    }

    /**
     * Gets an enabled enchantment by its RawName (case-insensitive).
     * RawName is the unique identifier, usually specified in the enchant's config or defaults to the config key.
     *
     * @param rawName The RawName of the enchantment (e.g., "efficiency", "explosive").
     * @return The EnchantmentWrapper if found and enabled, or null otherwise.
     */
    @Nullable
    public EnchantmentWrapper getEnchant(@Nullable String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return null;
        }
        // Lookup is case-insensitive using the lowercase rawName key stored in the map
        return registeredEnchants.get(rawName.toLowerCase());
    }

    /**
     * Gets an unmodifiable view of the collection of all loaded and enabled enchantments.
     * Prevents external modification of the internal registry.
     * The order may reflect the order in enchants.yml if loaded correctly.
     *
     * @return An unmodifiable collection of enabled EnchantmentWrapper objects. Never null.
     */
    @NotNull
    public Collection<EnchantmentWrapper> getAllEnchants() {
        // Return an unmodifiable view to prevent accidental changes from outside this class
        return Collections.unmodifiableCollection(registeredEnchants.values());
    }

    /**
     * Gets an unmodifiable view of the map of all loaded and enabled enchantments, keyed by lowercase RawName.
     * Prevents external modification of the internal map.
     *
     * @return An unmodifiable map where the key is the lowercase RawName and the value is the EnchantmentWrapper. Never null.
     */
    @NotNull
    public Map<String, EnchantmentWrapper> getEnchantsMap() {
        // Return an unmodifiable view
        return Collections.unmodifiableMap(registeredEnchants);
    }
}