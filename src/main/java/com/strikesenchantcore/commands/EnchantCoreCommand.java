package com.strikesenchantcore.commands;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.ConfigManager;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.config.PickaxeConfig;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.enchants.EnchantRegistry;
import com.strikesenchantcore.pickaxe.PickaxeManager;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.PDCUtil;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound; // Import Sound
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger

public class EnchantCoreCommand implements CommandExecutor {

    // Cached Managers & Logger
    private final EnchantCore plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final PickaxeManager pickaxeManager;
    private final PlayerDataManager playerDataManager;
    private final EnchantRegistry enchantRegistry;
    private final PickaxeConfig pickaxeConfig;

    public EnchantCoreCommand(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        // Initialize managers from plugin instance
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        this.pickaxeManager = plugin.getPickaxeManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.enchantRegistry = plugin.getEnchantRegistry();
        this.pickaxeConfig = plugin.getPickaxeConfig();

        // Validate mandatory managers
        if (configManager == null || messageManager == null || pickaxeManager == null ||
                playerDataManager == null || enchantRegistry == null || pickaxeConfig == null) {
            logger.severe("One or more managers are null in EnchantCoreCommand! Commands may not function correctly.");
            // Optional: throw exception? For commands, logging might be sufficient.
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Ensure managers are available (extra safety)
        if (configManager == null || messageManager == null || pickaxeManager == null ||
                playerDataManager == null || enchantRegistry == null || pickaxeConfig == null) {
            ChatUtil.sendMessage(sender, "&cCommand error: Core components not initialized. Please contact an admin.");
            logger.severe("EnchantCoreCommand executed but managers were null!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Use switch expression for cleaner handling (Java 14+) - Revert to classic switch if needed
        switch (subCommand) {
            case "reload"       -> handleReload(sender);
            case "give"         -> handleGive(sender, args, label);
            case "givemax"      -> handleGiveMax(sender, args, label);
            case "setlevel"     -> handleSetLevel(sender, args, label);
            case "addblocks"    -> handleAddBlocks(sender, args, label);
            default             -> sendUsage(sender);
        }
        return true;
    }

    /** Sends the command usage message from messages.yml */
    private void sendUsage(CommandSender sender) {
        List<String> usage = messageManager.getMessageList("commands.enchantcore.usage");
        // Provide a hardcoded default if messages.yml is broken
        if (usage.isEmpty()) {
            ChatUtil.sendMessage(sender, "&cUsage: /enchantcore <reload|give|givemax|setlevel|addblocks> [args]");
            return;
        }
        for (String line : usage) {
            // MessageManager already colors these lines
            sender.sendMessage(line); // Send raw message
        }
    }

    /** Handles the /ec reload subcommand */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("enchantcore.admin")) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.no_permission"));
            return;
        }
        try {
            configManager.reloadConfigs(); // This should handle reloading all necessary configs
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.reload_success"));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during configuration reload triggered by command", e);
            ChatUtil.sendMessage(sender, "&cAn error occurred during reload. Check console logs.");
        }
    }

    /** Handles the /ec give <player> subcommand */
    private void handleGive(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("enchantcore.admin")) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.no_permission"));
            return;
        }
        if (args.length < 2) {
            ChatUtil.sendMessage(sender, "&cUsage: /" + label + " give <player>");
            return;
        }

        Player targetPlayer = Bukkit.getPlayerExact(args[1]);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.player_not_found"));
            return;
        }

        // Create a base default pickaxe
        ItemStack pickaxe = pickaxeManager.createDefaultPickaxe();
        if (pickaxe == null) { // Check if creation failed
            logger.severe("handleGive: pickaxeManager.createDefaultPickaxe() returned null!");
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.give.error_create_default"));
            return;
        }

        // IMPORTANT: Update the pickaxe with the targetPlayer context BEFORE giving it.
        // This applies placeholders like %player% in the name/lore specific to the recipient.
        try {
            pickaxe = pickaxeManager.updatePickaxe(pickaxe, targetPlayer);
            if (pickaxe == null) throw new IllegalStateException("updatePickaxe returned null after creation");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "handleGive: Failed to update pickaxe for target player " + targetPlayer.getName(), e);
            ChatUtil.sendMessage(sender, "&cError preparing pickaxe for target player. Check console.");
            return;
        }


        // Give the fully prepared item to the player
        giveItemToPlayer(sender, targetPlayer, pickaxe,
                messageManager.getMessage("commands.enchantcore.give.success"),
                messageManager.getMessage("commands.enchantcore.give.target_received"));
    }

    /** Handles the /ec givemax <player> subcommand */
    private void handleGiveMax(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("enchantcore.admin")) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.no_permission"));
            return;
        }
        if (args.length < 2) {
            ChatUtil.sendMessage(sender, "&cUsage: /" + label + " givemax <player>");
            return;
        }
        Player targetPlayer = Bukkit.getPlayerExact(args[1]);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.player_not_found"));
            return;
        }

        // Create a base pickaxe to modify
        ItemStack pickaxe = pickaxeManager.createDefaultPickaxe();
        if (pickaxe == null) {
            logger.severe("handleGiveMax: pickaxeManager.createDefaultPickaxe() returned null!");
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.givemax.error_create_base"));
            return;
        }

        // --- Apply Max Stats via PDC ---
        // Set Pickaxe Level to Max
        int maxPLevel = pickaxeConfig.getMaxLevel();
        if (maxPLevel <= 0) { // If Max-Level is 0 or negative, treat as effectively unlimited (use a high value)
            maxPLevel = 1000; // Sensible fallback "max" if not explicitly defined or unlimited
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.givemax.warn_no_max_level").replace("%level%", String.valueOf(maxPLevel)));
        }
        PDCUtil.setPickaxeLevel(pickaxe, maxPLevel);

        // Set Blocks Mined to the amount required for that Max Level
        long blocksForMax = pickaxeManager.getBlocksRequiredForLevel(maxPLevel);
        if (blocksForMax == Long.MAX_VALUE) { // Avoid potential issues with literally MAX_VALUE
            blocksForMax = Long.MAX_VALUE -1; // Use a very large number instead
        } else if (blocksForMax < 0) { // Handle potential error from getBlocksRequiredForLevel
            logger.warning("handleGiveMax: getBlocksRequiredForLevel returned negative value for level " + maxPLevel + ". Setting blocks to 0.");
            blocksForMax = 0;
        }
        PDCUtil.setPickaxeBlocksMined(pickaxe, blocksForMax);

        // Set All Enabled Enchant Levels to Max
        for (EnchantmentWrapper enchant : enchantRegistry.getAllEnchants()) {
            if (enchant.isEnabled()) { // Only max out enabled enchants
                int maxELevel = enchant.getMaxLevel();
                if (maxELevel > 0) { // Only set if enchant has a defined max level > 0
                    if (!pickaxeManager.setEnchantLevel(pickaxe, enchant.getRawName(), maxELevel)) {
                        logger.warning("handleGiveMax: Failed to set max level (" + maxELevel + ") for enchant " + enchant.getRawName());
                        // Continue attempting others
                    }
                }
            }
        }
        // --- End Apply Max Stats ---


        // IMPORTANT: Update the pickaxe fully (name, lore, vanilla enchants) AFTER setting all PDC values
        // and BEFORE giving it to the player. Pass targetPlayer for context.
        try {
            pickaxe = pickaxeManager.updatePickaxe(pickaxe, targetPlayer);
            if (pickaxe == null) throw new IllegalStateException("updatePickaxe returned null after maxing stats");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "handleGiveMax: Failed to update maxed pickaxe for target player " + targetPlayer.getName(), e);
            ChatUtil.sendMessage(sender, "&cError preparing maxed pickaxe for target player. Check console.");
            return;
        }


        // Give the maxed-out, updated pickaxe
        giveItemToPlayer(sender, targetPlayer, pickaxe,
                messageManager.getMessage("commands.enchantcore.givemax.success"),
                messageManager.getMessage("commands.enchantcore.givemax.target_received"));
    }


    /** Handles the /ec setlevel <player> <level> subcommand */
    private void handleSetLevel(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("enchantcore.admin")) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.no_permission"));
            return;
        }
        if (args.length < 3) {
            ChatUtil.sendMessage(sender, "&cUsage: /" + label + " setlevel <player> <level>");
            return;
        }

        // Use OfflinePlayer to allow targeting offline players
        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(args[1]);
        // Check if player exists (has played before or is online)
        if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline()) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.player_not_found").replace("%player%", args[1]));
            return;
        }
        Player targetOnlinePlayer = targetOfflinePlayer.isOnline() ? targetOfflinePlayer.getPlayer() : null; // Get online player if possible

        // Parse level argument
        int level;
        try {
            level = Integer.parseInt(args[2]);
            if (level <= 0) throw new NumberFormatException("Level must be positive");
        } catch (NumberFormatException e) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.invalid_number"));
            return;
        }
        // Consider adding check against pickaxeConfig.getMaxLevel() ? Optional admin override?

        // Load PlayerData (essential for offline players, good practice for online too)
        PlayerData playerData = playerDataManager.loadPlayerData(targetOfflinePlayer.getUniqueId());
        if (playerData == null) {
            // loadPlayerData should ideally handle creating defaults, so this might indicate a deeper issue
            logger.warning("handleSetLevel: Failed to load/create PlayerData for " + targetOfflinePlayer.getName());
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.setlevel.data_error").replace("%player%", targetOfflinePlayer.getName()));
            return;
        }

        // Update PlayerData
        playerData.setPickaxeLevel(level);
        // Set blocks mined to the exact amount required TO REACH this level
        long blocksForNewLevel = pickaxeManager.getBlocksRequiredForLevel(level);
        if (blocksForNewLevel < 0) { // Handle potential error from calculation
            logger.warning("handleSetLevel: getBlocksRequiredForLevel returned invalid value for level " + level + ". Setting blocks to 0.");
            blocksForNewLevel = 0;
        }
        playerData.setBlocksMined(blocksForNewLevel);
        // Save the updated PlayerData (asynchronously)
        playerDataManager.savePlayerData(playerData, true);

        // If player is online, update their held pickaxe as well
        if (targetOnlinePlayer != null) {
            ItemStack pickaxe = pickaxeManager.findPickaxe(targetOnlinePlayer);
            if (pickaxe != null) {
                // Update PDC on the item
                PDCUtil.setPickaxeLevel(pickaxe, level);
                PDCUtil.setPickaxeBlocksMined(pickaxe, blocksForNewLevel);
                // Update item visuals (lore, name etc.)
                try {
                    pickaxeManager.updatePickaxe(pickaxe, targetOnlinePlayer);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "handleSetLevel: Failed to update pickaxe visuals for " + targetOnlinePlayer.getName(), e);
                    // Inform sender, but data was still set
                    ChatUtil.sendMessage(sender, "&cError updating online player's pickaxe visuals, but their data was set.");
                }

                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.setlevel.success")
                        .replace("%player%", targetOnlinePlayer.getName())
                        .replace("%level%", String.valueOf(level)));
            } else {
                // Player is online but doesn't have the pickaxe in inventory
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.setlevel.no_pickaxe").replace("%player%", targetOnlinePlayer.getName()));
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.setlevel.data_updated_offline")); // Clarify data was still set
            }
        } else { // Player is offline
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.setlevel.success_offline")
                    .replace("%player%", targetOfflinePlayer.getName())
                    .replace("%level%", String.valueOf(level)));
        }
    }


    /** Handles the /ec addblocks <player> <amount> subcommand */
    private void handleAddBlocks(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("enchantcore.admin")) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.no_permission"));
            return;
        }
        if (args.length < 3) {
            ChatUtil.sendMessage(sender, "&cUsage: /" + label + " addblocks <player> <amount>");
            return;
        }

        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(args[1]);
        if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline()) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.player_not_found").replace("%player%", args[1]));
            return;
        }
        Player targetOnlinePlayer = targetOfflinePlayer.isOnline() ? targetOfflinePlayer.getPlayer() : null;

        // Parse amount argument
        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount <= 0) throw new NumberFormatException("Amount must be positive");
        } catch (NumberFormatException e) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.invalid_number"));
            return;
        }

        // Load PlayerData
        PlayerData playerData = playerDataManager.loadPlayerData(targetOfflinePlayer.getUniqueId());
        if (playerData == null) {
            logger.warning("handleAddBlocks: Failed to load/create PlayerData for " + targetOfflinePlayer.getName());
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.addblocks.data_error").replace("%player%", targetOfflinePlayer.getName()));
            return;
        }

        // Add blocks to PlayerData (handles potential overflow)
        playerData.addBlocksMined(amount);
        // Save updated PlayerData (asynchronously)
        playerDataManager.savePlayerData(playerData, true);

        // If player is online, update their held pickaxe
        if (targetOnlinePlayer != null) {
            ItemStack pickaxe = pickaxeManager.findPickaxe(targetOnlinePlayer);
            if (pickaxe != null) {
                // Update item PDC with the new total block count from PlayerData
                PDCUtil.setPickaxeBlocksMined(pickaxe, playerData.getBlocksMined());

                // Check for level up using the updated PlayerData and pickaxe
                // This will update PlayerData's level if needed and also call updatePickaxe internally
                // if the player is online AND leveled up.
                boolean leveledUp = pickaxeManager.checkForLevelUp(targetOnlinePlayer, playerData, pickaxe);

                // Explicitly update pickaxe visuals *after* potential level up check.
                // This ensures the lore shows the correct block count even if no level up occurred.
                try {
                    pickaxeManager.updatePickaxe(pickaxe, targetOnlinePlayer);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "handleAddBlocks: Failed to update pickaxe visuals for " + targetOnlinePlayer.getName(), e);
                    ChatUtil.sendMessage(sender, "&cError updating online player's pickaxe visuals, but their data was set.");
                }

                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.addblocks.success")
                        .replace("%player%", targetOnlinePlayer.getName())
                        .replace("%amount%", String.valueOf(amount)));
                if (leveledUp) {
                    // Level up message is handled by checkForLevelUp/sendLevelUpFeedback
                }

            } else {
                // Player online but no pickaxe found
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.addblocks.no_pickaxe").replace("%player%", targetOnlinePlayer.getName()));
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.addblocks.data_updated_offline"));
            }
        } else {
            // Player is offline, PlayerData was saved. Level up will happen naturally later.
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.addblocks.success_offline")
                    .replace("%player%", targetOfflinePlayer.getName())
                    .replace("%amount%", String.valueOf(amount)));
        }
    }

    /**
     * Helper method to give an item to a player, handling inventory space and messaging.
     * @param sender The command sender (for feedback).
     * @param targetPlayer The player receiving the item.
     * @param item The ItemStack to give (already fully updated).
     * @param successMessageFormat Message format for the sender on success.
     * @param targetReceivedMessage Message for the target player on success.
     */
    private void giveItemToPlayer(@NotNull CommandSender sender, @NotNull Player targetPlayer, @NotNull ItemStack item, @NotNull String successMessageFormat, @NotNull String targetReceivedMessage) {
        PlayerInventory inventory = targetPlayer.getInventory();
        // AddItem returns a map of items that didn't fit (Slot index -> ItemStack)
        Map<Integer, ItemStack> leftover = inventory.addItem(item); // Item is already prepared

        if (!leftover.isEmpty()) {
            // Inventory was full, drop the first leftover item (usually the only one if giving one pickaxe)
            ItemStack dropItem = leftover.get(0);
            if (dropItem != null) {
                targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), dropItem);
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.enchantcore.give.inventory_full_sender").replace("%player%", targetPlayer.getName()));
                ChatUtil.sendMessage(targetPlayer, messageManager.getMessage("commands.enchantcore.give.inventory_full_target"));
            } else {
                // Should not happen if leftover map is not empty, but log just in case
                logger.warning("giveItemToPlayer: Leftover map was not empty, but item at index 0 was null for player " + targetPlayer.getName());
                ChatUtil.sendMessage(sender, "&cInventory full, but failed to drop leftover item for " + targetPlayer.getName());
            }
        } else {
            // Item was added successfully
            ChatUtil.sendMessage(sender, successMessageFormat.replace("%player%", targetPlayer.getName()));
            ChatUtil.sendMessage(targetPlayer, targetReceivedMessage);
            // Optional: Play sound for target player?
            targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        }
    }
}