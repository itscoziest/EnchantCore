package com.strikesenchantcore.commands;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger; // Import Logger

public class ToggleSoundsCommand implements CommandExecutor {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager; // Cached
    private final MessageManager messageManager; // Cached
    private final Logger logger; // Cached

    // Defaults already exist in MessageManager section for this command, but keep hardcoded ones as ultimate fallback
    private static final String DEF_MUST_BE_PLAYER = "&cThis command can only be run by a player.";
    private static final String DEF_NO_PERMISSION = "&cYou do not have permission to use this command.";
    private static final String DEF_DATA_ERROR = "&cCould not load your data. Please try again.";
    private static final String DEF_SOUNDS_ENABLED = "&aEnchantment activation sounds enabled.";
    private static final String DEF_SOUNDS_DISABLED = "&cEnchantment activation sounds disabled.";


    public ToggleSoundsCommand(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger();

        if (this.dataManager == null || this.messageManager == null) {
            logger.severe("PlayerDataManager or MessageManager is null in ToggleSoundsCommand!");
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
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.must_be_player", DEF_MUST_BE_PLAYER)); // Use common message key
            return true;
        }
        if (!player.hasPermission("enchantcore.togglesounds.use")) {
            // Use specific no_permission key from messages.yml if defined, else use common one
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.togglesounds.no_permission", messageManager.getMessage("common.no_permission", DEF_NO_PERMISSION)));
            return true;
        }

        // Get or load player data
        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) {
            playerData = dataManager.loadPlayerData(player.getUniqueId());
        }
        if (playerData == null) { // Still null after load attempt
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.togglesounds.data_error", DEF_DATA_ERROR));
            logger.warning("Could not load PlayerData for " + player.getName() + " in /togglesounds");
            return true;
        }

        // Toggle the setting
        boolean currentSetting = playerData.isShowEnchantSounds();
        playerData.setShowEnchantSounds(!currentSetting);
        // Save asynchronously
        dataManager.savePlayerData(playerData, true);

        // Send confirmation message using MessageManager
        if (playerData.isShowEnchantSounds()) {
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.togglesounds.enabled", DEF_SOUNDS_ENABLED));
        } else {
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.togglesounds.disabled", DEF_SOUNDS_DISABLED));
        }
        return true;
    }
}