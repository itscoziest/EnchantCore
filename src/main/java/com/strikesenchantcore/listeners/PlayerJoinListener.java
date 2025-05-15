package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.config.PickaxeConfig;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.pickaxe.PickaxeManager;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.PDCUtil; // Keep if needed for other checks

import org.bukkit.Location; // For dropping items
import org.bukkit.Sound; // For item pickup sound
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerJoinListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager playerDataManager;
    private final PickaxeManager pickaxeManager;
    private final PickaxeConfig pickaxeConfig;
    private final MessageManager messageManager;
    private final Logger logger;

    public PlayerJoinListener(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        // Cache managers/configs
        this.playerDataManager = plugin.getPlayerDataManager();
        this.pickaxeManager = plugin.getPickaxeManager();
        this.pickaxeConfig = plugin.getPickaxeConfig();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger();

        // Validate core dependencies
        if (playerDataManager == null || pickaxeManager == null || pickaxeConfig == null || messageManager == null) {
            logger.severe("One or more required managers/configs are null in PlayerJoinListener! Functionality may be impaired.");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL) // Normal priority is usually fine for join events
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // --- Load Player Data ---
        // Ensure managers are ready
        if (playerDataManager == null || pickaxeManager == null || pickaxeConfig == null || messageManager == null) {
            logger.severe("Cannot process PlayerJoinEvent for " + player.getName() + ": Required managers/configs are null.");
            return;
        }
        // Load data (creates default if first join) - This also ensures data is cached
        try {
            playerDataManager.loadPlayerData(player.getUniqueId());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load PlayerData for " + player.getName() + " on join.", e);
            // Should we prevent giving the pickaxe if data fails to load? Probably safer.
            return;
        }
        // --- End Load Player Data ---


        // --- First Join Pickaxe Logic ---
        if (!pickaxeConfig.isFirstJoinEnabled()) {
            return; // Feature disabled in config
        }

        boolean shouldGivePickaxe = false;
        // Check if we need to verify if the player already has a pickaxe
        if (pickaxeConfig.isFirstJoinCheckExisting()) {
            // If checkExisting is true, only give if they *don't* have one
            if (pickaxeManager.findPickaxe(player) == null) {
                shouldGivePickaxe = true;
                if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) {
                    logger.fine("[JoinListener] Player " + player.getName() + " does not have an existing pickaxe. Giving first join pickaxe.");
                }
            } else if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) {
                logger.fine("[JoinListener] Player " + player.getName() + " already has an EnchantCore pickaxe. Skipping first join give (CheckExisting=true).");
            }
        } else {
            // If checkExisting is false, always give the pickaxe on join (unless feature disabled)
            // This might give multiple pickaxes if the player loses their first one.
            // Consider adding a check for player.hasPlayedBefore() here if you ONLY want it on absolute first join
            // if (!player.hasPlayedBefore()) {
            //     shouldGivePickaxe = true;
            // }
            // For now, respecting only the checkExisting flag:
            shouldGivePickaxe = true;
            if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) {
                logger.fine("[JoinListener] CheckExisting is false. Giving first join pickaxe to " + player.getName());
            }
        }

        if (shouldGivePickaxe) {
            // Create the pickaxe configured for first join
            ItemStack firstPickaxe = pickaxeManager.createFirstJoinPickaxe(player);

            if (firstPickaxe == null) {
                logger.severe("Failed to create the First Join Pickaxe for player: " + player.getName());
                ChatUtil.sendMessage(player, "&cCould not create your starting pickaxe. Please contact an admin.");
                return;
            }

            // Give the item using robust logic
            giveItemToPlayer(player, firstPickaxe);
        }
        // --- End First Join Pickaxe Logic ---
    }

    /**
     * Helper method to give the first join pickaxe, handling inventory space.
     * Adapted from EnchantCoreCommand.
     * @param targetPlayer The player receiving the item.
     * @param item The ItemStack to give.
     */
    private void giveItemToPlayer(@NotNull Player targetPlayer, @NotNull ItemStack item) {
        PlayerInventory inventory = targetPlayer.getInventory();
        // AddItem returns a map of items that didn't fit (Slot index -> ItemStack)
        Map<Integer, ItemStack> leftover = inventory.addItem(item); // Item is already prepared by createFirstJoinPickaxe

        if (!leftover.isEmpty()) {
            // Inventory was full, drop the leftover item(s)
            ItemStack dropItem = leftover.get(0); // Usually just the one pickaxe
            if (dropItem != null) {
                Location dropLoc = targetPlayer.getLocation();
                targetPlayer.getWorld().dropItemNaturally(dropLoc, dropItem);
                // Send messages from messages.yml
                ChatUtil.sendMessage(targetPlayer, messageManager.getMessage("pickaxe.give_inventory_full", "&cYour inventory was full, the starting pickaxe was dropped nearby!"));
                if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check configManager
                    logger.fine("[JoinListener] Player " + targetPlayer.getName() + "'s inventory full, dropped starting pickaxe.");
                }
            } else {
                logger.warning("giveItemToPlayer (JoinListener): Leftover map not empty, but item at index 0 was null for player " + targetPlayer.getName());
            }
        } else {
            // Item was added successfully
            ChatUtil.sendMessage(targetPlayer, messageManager.getMessage("pickaxe.give_received_starting", "&aYou received the starting EnchantCore Pickaxe!"));
            // Play sound
            try {
                targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            } catch (Exception e) {
                // Ignore sound errors silently? Or log warning?
                if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) {
                    logger.log(Level.WARNING, "Error playing item pickup sound for " + targetPlayer.getName() + " on join.", e);
                }
            }
        }
    }
}