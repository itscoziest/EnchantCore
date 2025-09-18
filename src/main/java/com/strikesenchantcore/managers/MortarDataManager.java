package com.strikesenchantcore.managers;

import com.strikesenchantcore.EnchantCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles saving and loading mortar data to/from files
 * This integrates with the existing PlayerDataManager system
 */
public class MortarDataManager {

    private final EnchantCore plugin;
    private final File mortarDataFolder;

    public MortarDataManager(EnchantCore plugin) {
        this.plugin = plugin;
        this.mortarDataFolder = new File(plugin.getDataFolder(), "mortar_data");

        if (!mortarDataFolder.exists()) {
            mortarDataFolder.mkdirs();
        }
    }

    /**
     * Saves mortar data for a player
     */
    public void saveMortarData(UUID playerId, MortarManager.MortarData mortarData) {
        File playerFile = new File(mortarDataFolder, playerId.toString() + ".yml");
        FileConfiguration config = new YamlConfiguration();

        // Save basic mortar data
        config.set("mortar.level", mortarData.getLevel());
        config.set("mortar.last_activation", mortarData.getLastActivation());
        config.set("mortar.boost_end_time", mortarData.getBoostEndTime());
        config.set("mortar.boost_multiplier", mortarData.getBoostMultiplier());

        // Save upgrade levels
        for (MortarManager.MortarUpgrade upgrade : MortarManager.MortarUpgrade.values()) {
            int level = mortarData.getUpgradeLevel(upgrade);
            if (level > 0) {
                config.set("upgrades." + upgrade.name().toLowerCase(), level);
            }
        }

        try {
            config.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save mortar data for " + playerId, e);
        }
    }

    /**
     * Loads mortar data for a player
     */
    public MortarManager.MortarData loadMortarData(UUID playerId) {
        File playerFile = new File(mortarDataFolder, playerId.toString() + ".yml");
        MortarManager.MortarData mortarData = new MortarManager.MortarData();

        if (!playerFile.exists()) {
            return mortarData; // Return default/empty data
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

            // Load basic mortar data
            mortarData.setLevel(config.getInt("mortar.level", 0));
            mortarData.setLastActivation(config.getLong("mortar.last_activation", 0));

            long boostEndTime = config.getLong("mortar.boost_end_time", 0);
            double boostMultiplier = config.getDouble("mortar.boost_multiplier", 1.0);
            if (boostEndTime > System.currentTimeMillis()) {
                mortarData.setActiveBoost(boostEndTime, boostMultiplier);
            }

            // Load upgrade levels
            if (config.contains("upgrades")) {
                for (String key : config.getConfigurationSection("upgrades").getKeys(false)) {
                    try {
                        MortarManager.MortarUpgrade upgrade = MortarManager.MortarUpgrade.valueOf(key.toUpperCase());
                        int level = config.getInt("upgrades." + key, 0);
                        if (level > 0) {
                            mortarData.setUpgradeLevel(upgrade, level);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Unknown mortar upgrade: " + key + " for player " + playerId);
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load mortar data for " + playerId, e);
        }

        return mortarData;
    }

    /**
     * Deletes mortar data for a player
     */
    public void deleteMortarData(UUID playerId) {
        File playerFile = new File(mortarDataFolder, playerId.toString() + ".yml");
        if (playerFile.exists()) {
            playerFile.delete();
        }
    }

    /**
     * Alternative: Integration with existing PlayerData system
     * Add these methods to your existing PlayerDataManager instead
     */

    public static void saveToPlayerData(org.bukkit.configuration.ConfigurationSection section, MortarManager.MortarData mortarData) {
        section.set("mortar.level", mortarData.getLevel());
        section.set("mortar.last_activation", mortarData.getLastActivation());
        section.set("mortar.boost_end_time", mortarData.getBoostEndTime());
        section.set("mortar.boost_multiplier", mortarData.getBoostMultiplier());

        // Clear existing upgrades
        section.set("mortar.upgrades", null);

        // Save current upgrades
        for (MortarManager.MortarUpgrade upgrade : MortarManager.MortarUpgrade.values()) {
            int level = mortarData.getUpgradeLevel(upgrade);
            if (level > 0) {
                section.set("mortar.upgrades." + upgrade.name().toLowerCase(), level);
            }
        }
    }

    public static MortarManager.MortarData loadFromPlayerData(org.bukkit.configuration.ConfigurationSection section) {
        MortarManager.MortarData mortarData = new MortarManager.MortarData();

        if (section.contains("mortar")) {
            mortarData.setLevel(section.getInt("mortar.level", 0));
            mortarData.setLastActivation(section.getLong("mortar.last_activation", 0));

            long boostEndTime = section.getLong("mortar.boost_end_time", 0);
            double boostMultiplier = section.getDouble("mortar.boost_multiplier", 1.0);
            if (boostEndTime > System.currentTimeMillis()) {
                mortarData.setActiveBoost(boostEndTime, boostMultiplier);
            }

            // Load upgrades
            if (section.contains("mortar.upgrades")) {
                org.bukkit.configuration.ConfigurationSection upgradesSection = section.getConfigurationSection("mortar.upgrades");
                if (upgradesSection != null) {
                    for (String key : upgradesSection.getKeys(false)) {
                        try {
                            MortarManager.MortarUpgrade upgrade = MortarManager.MortarUpgrade.valueOf(key.toUpperCase());
                            int level = upgradesSection.getInt(key, 0);
                            if (level > 0) {
                                mortarData.setUpgradeLevel(upgrade, level);
                            }
                        } catch (IllegalArgumentException e) {
                            // Skip unknown upgrades
                        }
                    }
                }
            }
        }

        return mortarData;
    }
}