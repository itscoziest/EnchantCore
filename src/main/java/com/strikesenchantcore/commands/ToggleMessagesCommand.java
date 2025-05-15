package com.strikesenchantcore.commands;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.MessageManager; // Import MessageManager
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger; // Import Logger

public class ToggleMessagesCommand implements CommandExecutor {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager; // Cached
    private final MessageManager messageManager; // Cached
    private final Logger logger; // Cached

    // Default messages (if keys missing from messages.yml)
    private static final String DEF_MUST_BE_PLAYER = "&cThis command can only be run by a player.";
    private static final String DEF_NO_PERMISSION = "&cYou do not have permission to use this command.";
    private static final String DEF_DATA_ERROR = "&cCould not load your data. Please try again.";
    private static final String DEF_MESSAGES_ENABLED = "&aEnchantment activation messages enabled.";
    private static final String DEF_MESSAGES_DISABLED = "&cEnchantment activation messages disabled.";


    public ToggleMessagesCommand(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger();

        if (this.dataManager == null || this.messageManager == null) {
            logger.severe("PlayerDataManager or MessageManager is null in ToggleMessagesCommand!");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Manager check
        if (dataManager == null || messageManager == null) {
            ChatUtil.sendMessage(sender, "&cCommand error: Core components not initialized.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.togglemessages.must_be_player", DEF_MUST_BE_PLAYER));
            return true;
        }
        if (!player.hasPermission("enchantcore.togglemessages.use")) {
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.togglemessages.no_permission", DEF_NO_PERMISSION));
            return true;
        }

        // Get or load player data
        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) {
            playerData = dataManager.loadPlayerData(player.getUniqueId());
        }
        if (playerData == null) { // Still null after load attempt
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.togglemessages.data_error", DEF_DATA_ERROR));
            logger.warning("Could not load PlayerData for " + player.getName() + " in /ectoggle");
            return true;
        }

        // Toggle the setting
        boolean currentSetting = playerData.isShowEnchantMessages();
        playerData.setShowEnchantMessages(!currentSetting);
        // Save asynchronously
        dataManager.savePlayerData(playerData, true);

        // Send confirmation message using MessageManager
        String messageKey = playerData.isShowEnchantMessages() ? "commands.togglemessages.enabled" : "commands.togglemessages.disabled";
        String defaultMessage = playerData.isShowEnchantMessages() ? DEF_MESSAGES_ENABLED : DEF_MESSAGES_DISABLED;
        ChatUtil.sendMessage(player, messageManager.getMessage(messageKey, defaultMessage));

        return true;
    }
}