package com.strikesenchantcore.data;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.PickaxeConfig;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull; // Added for NotNull annotation
import org.jetbrains.annotations.Nullable; // Added for Nullable annotation

import org.bukkit.OfflinePlayer; // For OfflinePlayer class
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files; // For renaming files
import java.nio.file.StandardCopyOption; // For renaming files
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger; // Added Logger import

/**
 * Manages loading/saving PlayerData from individual YAML files.
 * Includes caching, asynchronous saving, and auto-saving.
 */
public class PlayerDataManager {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    private final File dataFolder; // Folder where player data is stored ("plugins/EnchantCore/playerdata")
    // Thread-safe cache for online/recently accessed player data
    private final ConcurrentHashMap<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();
    private BukkitTask autoSaveTask;
    // Auto-save interval in server ticks (20 ticks = 1 second)
    private static final long AUTO_SAVE_INTERVAL_TICKS = 5 * 60 * 20; // 5 minutes

    public PlayerDataManager(EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        // Define the player data subfolder
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        // Create the folder if it doesn't exist
        if (!dataFolder.exists()) {
            if (!dataFolder.mkdirs()) {
                logger.severe("Could not create playerdata directory! Path: " + dataFolder.getAbsolutePath());
                // Optional: Disable plugin or features if data storage fails?
            } else {
                logger.info("Created playerdata directory.");
            }
        }
        startAutoSaveTask(); // Start the periodic save task
    }

    /**
     * Loads player data for a given UUID. Checks cache first, then loads from file.
     * If the file doesn't exist, creates default data. If the file is corrupt,
     * attempts to rename it and creates default data.
     *
     * @param playerUUID The UUID of the player.
     * @return The PlayerData object, or null if UUID is null.
     */
    @Nullable
    public PlayerData loadPlayerData(@NotNull UUID playerUUID) {
        // Return immediately if already cached
        if (playerDataCache.containsKey(playerUUID)) {
            return playerDataCache.get(playerUUID);
        }

        final boolean debug = plugin.getConfigManager().isDebugMode();
        File playerFile = getPlayerFile(playerUUID);
        PlayerData data;
        PickaxeConfig pConfig = plugin.getPickaxeConfig(); // Get defaults config

        if (pConfig == null) {
            logger.severe("[PlayerData] Cannot load data for " + playerUUID + ": PickaxeConfig is null!");
            return createDefaultPlayerData(playerUUID); // Return default even if we can't fully initialize? Or null? Returning default seems safer.
        }

        if (playerFile.exists()) {
            YamlConfiguration playerConfig = new YamlConfiguration();
            try {
                playerConfig.load(playerFile); // Load the YAML file
                // Read data, using PickaxeConfig defaults as fallbacks
                int level = playerConfig.getInt("pickaxe.level", pConfig.getFirstJoinLevel());
                long blocks = playerConfig.getLong("pickaxe.blocks_mined", pConfig.getFirstJoinBlocksMined());
                boolean showMessages = playerConfig.getBoolean("settings.showEnchantMessages", true);
                boolean showSounds = playerConfig.getBoolean("settings.showEnchantSounds", true);
                long tokens = playerConfig.getLong("currency.tokens", 0L);
                long gems = playerConfig.getLong("currency.gems", 0L); // ADD THIS LINE
                long boosterEndTime = playerConfig.getLong("boosters.block.endTime", 0L);
                double boosterMultiplier = playerConfig.getDouble("boosters.block.multiplier", 1.0);

                // Create the PlayerData object
                data = new PlayerData(playerUUID, level, blocks);
                data.setShowEnchantMessages(showMessages);
                data.setShowEnchantSounds(showSounds);
                data.setTokens(tokens);
                data.setGems(gems);
                // Restore booster state (will auto-deactivate if end time is past)
                data.blockBoosterEndTime = boosterEndTime;
                data.blockBoosterMultiplier = boosterMultiplier;
                if (debug) logger.fine("[PlayerData] Loaded data for " + playerUUID + " from file.");

            } catch (IOException | InvalidConfigurationException e) {
                logger.log(Level.SEVERE, "Could not load player data file: " + playerFile.getName() + ". File might be corrupted.", e);
                // --- Attempt to Backup Corrupted File ---
                File corruptFile = new File(dataFolder, playerUUID.toString() + ".yml.corrupt");
                try {
                    // Ensure parent directory exists (should already, but safe check)
                    Files.createDirectories(corruptFile.getParentFile().toPath());
                    // Rename the bad file
                    Files.move(playerFile.toPath(), corruptFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.warning("Renamed corrupted data file for " + playerUUID + " to " + corruptFile.getName());
                } catch (IOException renameError) {
                    logger.log(Level.SEVERE, "Failed to rename corrupted data file for " + playerUUID + ": " + renameError.getMessage());
                }
                // --- End Backup ---
                data = createDefaultPlayerData(playerUUID); // Create default data after backup attempt
                savePlayerData(data, false); // Save the new default data immediately (synchronously)
            }
        } else {
            // File doesn't exist, create new default data
            if (debug) logger.fine("[PlayerData] No data file found for " + playerUUID + ". Creating default data.");
            data = createDefaultPlayerData(playerUUID);
            savePlayerData(data, false); // Save defaults synchronously for new players
        }

        // Add the loaded/created data to the cache
        if (data != null) { // Should always be non-null here due to createDefaultPlayerData
            playerDataCache.put(playerUUID, data);
        }
        return data;
    }

    /**
     * Creates a default PlayerData object using settings from PickaxeConfig.
     * @param playerUUID The player's UUID.
     * @return A new PlayerData object with default values.
     */
    @NotNull // Ensure this method always returns a valid object
    private PlayerData createDefaultPlayerData(@NotNull UUID playerUUID) {
        PickaxeConfig pConfig = plugin.getPickaxeConfig();
        int defaultLevel = 1;
        long defaultBlocks = 0L;
        // Safely get defaults from config, fallback to hardcoded defaults if config is null
        if (pConfig != null) {
            defaultLevel = pConfig.getFirstJoinLevel();
            defaultBlocks = pConfig.getFirstJoinBlocksMined();
        } else {
            logger.warning("[PlayerData] PickaxeConfig was null during createDefaultPlayerData for " + playerUUID + ". Using hardcoded defaults (Lvl 1, Blocks 0).");
        }
        // PlayerData constructor initializes toggles and booster defaults
        return new PlayerData(playerUUID, defaultLevel, defaultBlocks);
    }

    /**
     * Saves PlayerData to its corresponding YAML file. Can be run synchronously or asynchronously.
     * @param data The PlayerData to save.
     * @param async True to save asynchronously, false to save synchronously.
     */
    public void savePlayerData(@Nullable PlayerData data, boolean async) {
        if (data == null) return; // Don't try to save null data

        final UUID playerUUID = data.getPlayerUUID(); // Get UUID for logging/filename
        final boolean debug = plugin.getConfigManager().isDebugMode();

        // Define the save logic as a Runnable
        Runnable saveTask = () -> {
            File playerFile = getPlayerFile(playerUUID);
            YamlConfiguration playerConfig = new YamlConfiguration();

            // --- Populate the Configuration ---
            // Store name for readability, get offline player to ensure name is available even if offline
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            playerConfig.set("player_name", offlinePlayer.getName() != null ? offlinePlayer.getName() : playerUUID.toString());
            playerConfig.set("pickaxe.level", data.getPickaxeLevel());
            playerConfig.set("pickaxe.blocks_mined", data.getBlocksMined());
            playerConfig.set("settings.showEnchantMessages", data.isShowEnchantMessages());
            playerConfig.set("settings.showEnchantSounds", data.isShowEnchantSounds());
            playerConfig.set("currency.tokens", data.getTokens());
            playerConfig.set("currency.gems", data.getGems());
            // Save booster info even if inactive (preserves remaining time if server restarts)
            playerConfig.set("boosters.block.endTime", data.blockBoosterEndTime);
            playerConfig.set("boosters.block.multiplier", data.blockBoosterMultiplier);
            // --- End Populate ---

            try {
                // Ensure the data folder exists before saving
                if (!dataFolder.exists()) {
                    if (!dataFolder.mkdirs()) {
                        logger.severe("Could not create playerdata directory during save! Path: " + dataFolder.getAbsolutePath());
                        return; // Stop save if directory creation fails
                    }
                }
                playerConfig.save(playerFile); // Save the data to file
                if (debug && async) logger.finest("[PlayerData] Async save complete for " + playerUUID);
            } catch (IOException e) {
                // Log save errors clearly, including player identifier
                logger.log(Level.SEVERE, "Could not save player data for UUID: " + playerUUID + " (Name: " + (offlinePlayer.getName() != null ? offlinePlayer.getName() : "N/A") + ") to file: " + playerFile.getName(), e);
            }
        };

        // Execute the save task either sync or async
        if (async) {
            if (plugin.isEnabled()) { // Check if plugin is still enabled before scheduling async task
                Bukkit.getScheduler().runTaskAsynchronously(plugin, saveTask);
            } else {
                if (debug) logger.warning("[PlayerData] Plugin disabled, cannot schedule async save for " + playerUUID);
                // Consider running synchronously if disabled? Might be risky during shutdown.
                // saveTask.run(); // Option: Force sync save if disabled (use with caution)
            }
        } else {
            saveTask.run(); // Run synchronously
        }
    }

    /**
     * Saves data for all currently cached players. Typically used during auto-save or shutdown.
     * Saves synchronously if called during shutdown, otherwise respects the async flag.
     * @param syncOnDisable If true, forces synchronous saving (for use in onDisable).
     */
    public void saveAllPlayerData(boolean syncOnDisable) {
        if (playerDataCache.isEmpty()) return; // Nothing to save

        int cacheSize = playerDataCache.size();
        logger.info("Saving data for " + cacheSize + " cached players...");
        long startTime = System.currentTimeMillis();

        // Determine if saving should be async based on context
        boolean performAsync = !syncOnDisable;

        // Iterate over cached data and save each entry
        // Use entrySet for potentially slightly better performance than values()
        for (Map.Entry<UUID, PlayerData> entry : playerDataCache.entrySet()) {
            if (entry.getValue() != null) {
                savePlayerData(entry.getValue(), performAsync);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Player data saving triggered (" + (performAsync ? "asynchronously" : "synchronously") + "). Count: " + cacheSize + ". Approx time if sync: " + duration + "ms.");
        // Note: If async, the actual saving might take longer to complete.
    }

    /**
     * Gets the cached PlayerData for a given UUID. Does not load from file if not cached.
     * @param playerUUID The player's UUID.
     * @return The cached PlayerData, or null if not in cache.
     */
    @Nullable
    public PlayerData getPlayerData(@NotNull UUID playerUUID) {
        return playerDataCache.get(playerUUID);
    }

    /**
     * Removes player data from the cache. Optionally saves before unloading.
     * Typically called when a player quits.
     * @param playerUUID The UUID of the player to unload.
     * @param saveBeforeUnload True to save data asynchronously before removing from cache.
     */
    public void unloadPlayerData(@NotNull UUID playerUUID, boolean saveBeforeUnload) {
        PlayerData data = playerDataCache.get(playerUUID); // Get data before removing
        if (data != null && saveBeforeUnload) {
            savePlayerData(data, true); // Save asynchronously is usually fine on quit
        }
        playerDataCache.remove(playerUUID); // Remove from cache regardless of save success
        if (plugin.getConfigManager().isDebugMode()) {
            logger.fine("[PlayerData] Unloaded data for " + playerUUID + " (Save before unload: " + saveBeforeUnload + ")");
        }
    }

    /**
     * Loads data for all currently online players if not already cached.
     * Useful after plugin reloads or startups.
     */
    public void loadOnlinePlayers() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!playerDataCache.containsKey(player.getUniqueId())) {
                loadPlayerData(player.getUniqueId()); // Load data if not in cache
                count++;
            }
        }
        if (count > 0) {
            logger.info("Loaded data for " + count + " newly joined/online players.");
        }
    }

    /**
     * Gets the File object representing the player's data file.
     * @param playerUUID The player's UUID.
     * @return The File object.
     */
    @NotNull
    private File getPlayerFile(@NotNull UUID playerUUID) {
        // Construct file path: /plugins/EnchantCore/playerdata/uuid.yml
        return new File(dataFolder, playerUUID.toString() + ".yml");
    }

    /**
     * Starts the asynchronous auto-save task. Cancels any existing task first.
     */
    private void startAutoSaveTask() {
        stopAutoSaveTask(); // Ensure any previous task is stopped

        if (AUTO_SAVE_INTERVAL_TICKS <= 0) {
            logger.info("Player data auto-saving is disabled (interval <= 0).");
            return;
        }

        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Only save if the cache is not empty
                if (!playerDataCache.isEmpty()) {
                    if (plugin.getConfigManager().isDebugMode()) {
                        logger.fine("[Debug] Auto-saving player data (" + playerDataCache.size() + " players)...");
                    }
                    // Call saveAll, allowing async saving within the task
                    saveAllPlayerData(false); // false = allow async saves
                }
            }
            // Run task asynchronously, with initial delay and repeating period
        }.runTaskTimerAsynchronously(plugin, AUTO_SAVE_INTERVAL_TICKS, AUTO_SAVE_INTERVAL_TICKS);
        logger.info("Player data auto-save task started (Interval: " + (AUTO_SAVE_INTERVAL_TICKS / 20.0) + " seconds).");
    }

    /**
     * Stops the auto-save task if it's running.
     */
    public void stopAutoSaveTask() {
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            try {
                autoSaveTask.cancel();
            } catch (IllegalStateException ignore) {} // Ignore if already cancelled
            autoSaveTask = null;
            logger.info("Player data auto-save task stopped.");
        }
    }
}