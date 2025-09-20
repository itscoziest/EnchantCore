package com.strikesenchantcore.data;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.PickaxeConfig;
import com.strikesenchantcore.managers.AttachmentManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.OfflinePlayer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages loading/saving PlayerData from individual YAML files.
 * Includes caching, asynchronous saving, and auto-saving.
 */
public class PlayerDataManager {

    private final EnchantCore plugin;
    private final Logger logger;
    private final File dataFolder;
    private final ConcurrentHashMap<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();
    private BukkitTask autoSaveTask;
    private static final long AUTO_SAVE_INTERVAL_TICKS = 5 * 60 * 20; // 5 minutes

    public PlayerDataManager(EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            if (!dataFolder.mkdirs()) {
                logger.severe("Could not create playerdata directory! Path: " + dataFolder.getAbsolutePath());
            } else {
                logger.info("Created playerdata directory.");
            }
        }
        startAutoSaveTask();
    }

    @Nullable
    public PlayerData loadPlayerData(@NotNull UUID playerUUID) {
        if (playerDataCache.containsKey(playerUUID)) {
            return playerDataCache.get(playerUUID);
        }

        final boolean debug = plugin.getConfigManager().isDebugMode();
        File playerFile = getPlayerFile(playerUUID);
        PlayerData data;
        PickaxeConfig pConfig = plugin.getPickaxeConfig();

        if (pConfig == null) {
            logger.severe("[PlayerData] Cannot load data for " + playerUUID + ": PickaxeConfig is null!");
            return createDefaultPlayerData(playerUUID);
        }

        if (playerFile.exists()) {
            YamlConfiguration playerConfig = new YamlConfiguration();
            try {
                playerConfig.load(playerFile);
                int level = playerConfig.getInt("pickaxe.level", pConfig.getFirstJoinLevel());
                long blocks = playerConfig.getLong("pickaxe.blocks_mined", pConfig.getFirstJoinBlocksMined());
                boolean showMessages = playerConfig.getBoolean("settings.showEnchantMessages", true);
                boolean showSounds = playerConfig.getBoolean("settings.showEnchantSounds", true);
                long tokens = playerConfig.getLong("currency.tokens", 0L);
                long gems = playerConfig.getLong("currency.gems", 0L);
                long boosterEndTime = playerConfig.getLong("boosters.block.endTime", 0L);
                double boosterMultiplier = playerConfig.getDouble("boosters.block.multiplier", 1.0);

                data = new PlayerData(playerUUID, level, blocks);
                data.setShowEnchantMessages(showMessages);
                data.setShowEnchantSounds(showSounds);
                data.setTokens(tokens);
                data.setGems(gems);
                data.setBlockBoosterEndTime(boosterEndTime);
                data.setBlockBoosterMultiplier(boosterMultiplier);

                // Load crystal data
                ConfigurationSection crystalsSection = playerConfig.getConfigurationSection("crystals");
                if (crystalsSection != null) {
                    ConfigurationSection storageSection = crystalsSection.getConfigurationSection("storage");
                    if (storageSection != null) {
                        Map<String, Integer> crystalStorage = new HashMap<>();
                        for (String key : storageSection.getKeys(false)) {
                            crystalStorage.put(key, storageSection.getInt(key));
                        }
                        data.setCrystalStorage(crystalStorage);
                    }

                    ConfigurationSection equippedSection = crystalsSection.getConfigurationSection("equipped");
                    if (equippedSection != null) {
                        Map<Integer, String> equippedCrystals = new HashMap<>();
                        for (String key : equippedSection.getKeys(false)) {
                            try {
                                int slot = Integer.parseInt(key);
                                String crystalType = equippedSection.getString(key);
                                if (crystalType != null && !crystalType.isEmpty()) {
                                    equippedCrystals.put(slot, crystalType);
                                }
                            } catch (NumberFormatException e) {
                                // Skip invalid keys
                            }
                        }
                        data.setEquippedCrystals(equippedCrystals);
                    }
                }

                // Load mortar data - ADD THIS SECTION
                data.setMortarLevel(playerConfig.getInt("mortar.level", 0));
                data.setMortarLastActivation(playerConfig.getLong("mortar.lastActivation", 0L));
                data.setMortarBoostEndTime(playerConfig.getLong("mortar.boostEndTime", 0L));
                data.setMortarBoostMultiplier(playerConfig.getDouble("mortar.boostMultiplier", 1.0));

                // Load mortar upgrades
                ConfigurationSection mortarUpgradesSection = playerConfig.getConfigurationSection("mortar.upgrades");
                if (mortarUpgradesSection != null) {
                    for (String key : mortarUpgradesSection.getKeys(false)) {
                        int mortarLevel = mortarUpgradesSection.getInt(key, 0);
                        if (mortarLevel > 0) {
                            data.setMortarUpgradeLevel(key, mortarLevel);
                        }
                    }
                }

                loadAttachmentData(playerConfig, playerUUID); // ADDED THIS LINE

                if (debug) logger.fine("[PlayerData] Loaded data for " + playerUUID + " from file.");

            } catch (IOException | InvalidConfigurationException e) {
                logger.log(Level.SEVERE, "Could not load player data file: " + playerFile.getName() + ". File might be corrupted.", e);
                File corruptFile = new File(dataFolder, playerUUID.toString() + ".yml.corrupt");
                try {
                    Files.createDirectories(corruptFile.getParentFile().toPath());
                    Files.move(playerFile.toPath(), corruptFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.warning("Renamed corrupted data file for " + playerUUID + " to " + corruptFile.getName());
                } catch (IOException renameError) {
                    logger.log(Level.SEVERE, "Failed to rename corrupted data file for " + playerUUID + ": " + renameError.getMessage());
                }
                data = createDefaultPlayerData(playerUUID);
                savePlayerData(data, false);
            }
        } else {
            if (debug) logger.fine("[PlayerData] No data file found for " + playerUUID + ". Creating default data.");
            data = createDefaultPlayerData(playerUUID);
            savePlayerData(data, false);
        }

        if (data != null) {
            playerDataCache.put(playerUUID, data);
        }
        return data;
    }

    @NotNull
    private PlayerData createDefaultPlayerData(@NotNull UUID playerUUID) {
        PickaxeConfig pConfig = plugin.getPickaxeConfig();
        int defaultLevel = 1;
        long defaultBlocks = 0L;
        if (pConfig != null) {
            defaultLevel = pConfig.getFirstJoinLevel();
            defaultBlocks = pConfig.getFirstJoinBlocksMined();
        } else {
            logger.warning("[PlayerData] PickaxeConfig was null during createDefaultPlayerData for " + playerUUID + ". Using hardcoded defaults (Lvl 1, Blocks 0).");
        }
        return new PlayerData(playerUUID, defaultLevel, defaultBlocks);
    }

    public void savePlayerData(@Nullable PlayerData data, boolean async) {
        if (data == null) return;

        final UUID playerUUID = data.getPlayerUUID();
        final boolean debug = plugin.getConfigManager().isDebugMode();

        Runnable saveTask = () -> {
            File playerFile = getPlayerFile(playerUUID);
            YamlConfiguration playerConfig = new YamlConfiguration();

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            playerConfig.set("player_name", offlinePlayer.getName() != null ? offlinePlayer.getName() : playerUUID.toString());
            playerConfig.set("pickaxe.level", data.getPickaxeLevel());
            playerConfig.set("pickaxe.blocks_mined", data.getBlocksMined());
            playerConfig.set("settings.showEnchantMessages", data.isShowEnchantMessages());
            playerConfig.set("settings.showEnchantSounds", data.isShowEnchantSounds());
            playerConfig.set("currency.tokens", data.getTokens());
            playerConfig.set("currency.gems", data.getGems());
            playerConfig.set("boosters.block.endTime", data.getBlockBoosterEndTime());
            playerConfig.set("boosters.block.multiplier", data.getRawBlockBoosterMultiplier());

            // --- FIXED: Call the dedicated method to save crystal data ---
            saveCrystalData(playerConfig, data);
            // --- END FIXED ---

            // Save mortar data - ADD THIS SECTION
            playerConfig.set("mortar.level", data.getMortarLevel());
            playerConfig.set("mortar.lastActivation", data.getMortarLastActivation());
            playerConfig.set("mortar.boostEndTime", data.getMortarBoostEndTime());
            playerConfig.set("mortar.boostMultiplier", data.getMortarBoostMultiplier());

            // Save mortar upgrades
            Map<String, Integer> mortarUpgrades = data.getMortarUpgrades();
            if (!mortarUpgrades.isEmpty()) {
                for (Map.Entry<String, Integer> entry : mortarUpgrades.entrySet()) {
                    playerConfig.set("mortar.upgrades." + entry.getKey(), entry.getValue());
                }
            } else {
                playerConfig.set("mortar.upgrades", null);
            }

            saveAttachmentData(playerConfig, data.getPlayerUUID()); // ADDED THIS LINE

            try {
                if (!dataFolder.exists()) {
                    if (!dataFolder.mkdirs()) {
                        logger.severe("Could not create playerdata directory during save! Path: " + dataFolder.getAbsolutePath());
                        return;
                    }
                }
                playerConfig.set("test.timestamp", System.currentTimeMillis());
                playerConfig.save(playerFile);
                if (debug && async) logger.finest("[PlayerData] Async save complete for " + playerUUID);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not save player data for UUID: " + playerUUID + " (Name: " + (offlinePlayer.getName() != null ? offlinePlayer.getName() : "N/A") + ") to file: " + playerFile.getName(), e);
            }
        };

        if (async) {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, saveTask);
            } else {
                if (debug) logger.warning("[PlayerData] Plugin disabled, cannot schedule async save for " + playerUUID);
            }
        } else {
            saveTask.run();
        }
    }

    public void saveAllPlayerData(boolean syncOnDisable) {
        if (playerDataCache.isEmpty()) return;

        int cacheSize = playerDataCache.size();
        logger.info("Saving data for " + cacheSize + " cached players...");
        long startTime = System.currentTimeMillis();

        boolean performAsync = !syncOnDisable;

        for (Map.Entry<UUID, PlayerData> entry : playerDataCache.entrySet()) {
            if (entry.getValue() != null) {
                savePlayerData(entry.getValue(), performAsync);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Player data saving triggered (" + (performAsync ? "asynchronously" : "synchronously") + "). Count: " + cacheSize + ". Approx time if sync: " + duration + "ms.");
    }

    private void saveCrystalData(FileConfiguration config, PlayerData playerData) {
        Map<String, Integer> crystals = playerData.getCrystalStorage();
        Map<Integer, String> equipped = playerData.getEquippedCrystals();

        // Clear existing crystal data to prevent orphaned entries
        config.set("crystals", null);

        if (crystals != null && !crystals.isEmpty()) {
            config.set("crystals.storage", crystals);
        }

        if (equipped != null && !equipped.isEmpty()) {
            // To ensure keys are saved as strings in YAML
            Map<String, String> equippedStringKeys = new HashMap<>();
            equipped.forEach((slot, type) -> equippedStringKeys.put(String.valueOf(slot), type));
            config.set("crystals.equipped", equippedStringKeys);
        }

        if (plugin.getConfigManager().isDebugMode()) {
            logger.info("DEBUG: Saved crystal data - Storage: " + crystals + ", Equipped: " + equipped);
        }
    }

    public void debugSaveTest(UUID playerId) {
        PlayerData data = getPlayerData(playerId);
        if (data == null) {
            logger.info("DEBUG: No player data found for " + playerId);
            return;
        }

        logger.info("DEBUG: Saving player data for " + playerId);
        logger.info("DEBUG: Mortar level: " + data.getMortarLevel());
        logger.info("DEBUG: Tokens: " + data.getTokens());

        savePlayerData(data, false); // Force sync save
        logger.info("DEBUG: Save completed");
    }

    private void saveAttachmentData(ConfigurationSection section, UUID playerId) {
        plugin.getLogger().info("DEBUG: saveAttachmentData called for player " + playerId);
        AttachmentManager attachmentManager = plugin.getAttachmentManager();
        if (attachmentManager == null) return;

        AttachmentManager.AttachmentStorage storage = attachmentManager.getPlayerStorage(playerId);

        // Clear existing attachment data
        section.set("attachments", null);

        // Save stored attachments
        Map<Integer, Integer> attachments = storage.getAllAttachments();
        for (Map.Entry<Integer, Integer> entry : attachments.entrySet()) {
            if (entry.getValue() > 0) {
                section.set("attachments.stored.tier_" + entry.getKey(), entry.getValue());
            }
        }

        // Save equipped attachments
        Map<Integer, Integer> equipped = storage.getEquippedMap();
        for (Map.Entry<Integer, Integer> entry : equipped.entrySet()) {
            section.set("attachments.equipped.slot_" + entry.getKey(), entry.getValue());
        }
    }

    private void loadAttachmentData(ConfigurationSection section, UUID playerId) {
        AttachmentManager attachmentManager = plugin.getAttachmentManager();
        if (attachmentManager == null || !section.contains("attachments")) return;

        AttachmentManager.AttachmentStorage storage = attachmentManager.getPlayerStorage(playerId);

        // Load stored attachments
        if (section.contains("attachments.stored")) {
            ConfigurationSection storedSection = section.getConfigurationSection("attachments.stored");
            for (String key : storedSection.getKeys(false)) {
                if (key.startsWith("tier_")) {
                    try {
                        int tier = Integer.parseInt(key.substring(5));
                        int amount = storedSection.getInt(key, 0);
                        if (amount > 0) {
                            storage.addAttachment(tier, amount);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid attachment tier in save data: " + key);
                    }
                }
            }
        }

        // Load equipped attachments
        if (section.contains("attachments.equipped")) {
            ConfigurationSection equippedSection = section.getConfigurationSection("attachments.equipped");
            for (String key : equippedSection.getKeys(false)) {
                if (key.startsWith("slot_")) {
                    try {
                        int slot = Integer.parseInt(key.substring(5));
                        int tier = equippedSection.getInt(key, 0);
                        if (tier > 0 && slot >= 0 && slot < AttachmentManager.MAX_EQUIPPED_ATTACHMENTS) {
                            storage.setEquippedAttachment(slot, tier);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid attachment slot in save data: " + key);
                    }
                }
            }
        }
    }

    @Nullable
    public PlayerData getPlayerData(@NotNull UUID playerUUID) {
        if (playerDataCache.containsKey(playerUUID)) {
            return playerDataCache.get(playerUUID);
        }
        return loadPlayerData(playerUUID);
    }

    public void unloadPlayerData(@NotNull UUID playerUUID, boolean saveBeforeUnload) {
        PlayerData data = playerDataCache.get(playerUUID);
        if (data != null && saveBeforeUnload) {
            savePlayerData(data, true);
        }
        playerDataCache.remove(playerUUID);
        if (plugin.getConfigManager().isDebugMode()) {
            logger.fine("[PlayerData] Unloaded data for " + playerUUID + " (Save before unload: " + saveBeforeUnload + ")");
        }
    }

    public void loadOnlinePlayers() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!playerDataCache.containsKey(player.getUniqueId())) {
                loadPlayerData(player.getUniqueId());
                count++;
            }
        }
        if (count > 0) {
            logger.info("Loaded data for " + count + " newly joined/online players.");
        }
    }

    @NotNull
    private File getPlayerFile(@NotNull UUID playerUUID) {
        return new File(dataFolder, playerUUID.toString() + ".yml");
    }

    private void startAutoSaveTask() {
        stopAutoSaveTask();

        if (AUTO_SAVE_INTERVAL_TICKS <= 0) {
            logger.info("Player data auto-saving is disabled (interval <= 0).");
            return;
        }

        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!playerDataCache.isEmpty()) {
                    if (plugin.getConfigManager().isDebugMode()) {
                        logger.fine("[Debug] Auto-saving player data (" + playerDataCache.size() + " players)...");
                    }
                    saveAllPlayerData(false);
                }
            }
        }.runTaskTimerAsynchronously(plugin, AUTO_SAVE_INTERVAL_TICKS, AUTO_SAVE_INTERVAL_TICKS);
        logger.info("Player data auto-save task started (Interval: " + (AUTO_SAVE_INTERVAL_TICKS / 20.0) + " seconds).");
    }

    public void stopAutoSaveTask() {
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            try {
                autoSaveTask.cancel();
            } catch (IllegalStateException ignore) {}
            autoSaveTask = null;
            logger.info("Player data auto-save task stopped.");
        }
    }
}

