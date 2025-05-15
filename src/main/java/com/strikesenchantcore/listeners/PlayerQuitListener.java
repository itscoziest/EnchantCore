package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerDataManager; // Import PlayerDataManager
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull; // Import NotNull
import java.util.logging.Logger; // Import Logger

/**
 * Listens for player quit events to handle necessary cleanup,
 * primarily unloading player data from the cache after saving it.
 */
public class PlayerQuitListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager playerDataManager; // Cached instance
    private final Logger logger; // Cached logger

    public PlayerQuitListener(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager(); // Get manager instance
        this.logger = plugin.getLogger();

        if (this.playerDataManager == null) {
            logger.severe("PlayerDataManager is null in PlayerQuitListener! Player data may not save on quit.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Ensure PlayerDataManager is available
        if (playerDataManager == null) {
            logger.severe("Cannot unload data for " + event.getPlayer().getName() + " on quit: PlayerDataManager is null!");
            return;
        }

        // Unload player data asynchronously (saves data first, then removes from cache)
        try {
            playerDataManager.unloadPlayerData(event.getPlayer().getUniqueId(), true);
            // Debug logging for unload is handled within PlayerDataManager.unloadPlayerData
        } catch (Exception e) {
            // Log any unexpected errors during the unload process
            logger.severe("Error unloading PlayerData for " + event.getPlayer().getName() + " on quit: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for detailed debugging
        }
    }
}