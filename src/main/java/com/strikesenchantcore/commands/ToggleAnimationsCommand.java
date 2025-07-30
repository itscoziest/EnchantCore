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

import java.util.logging.Logger;

public class ToggleAnimationsCommand implements CommandExecutor {

    private final PlayerDataManager dataManager;
    private final MessageManager messageManager;
    private final Logger logger;

    public ToggleAnimationsCommand(@NotNull EnchantCore plugin) {
        this.dataManager = plugin.getPlayerDataManager();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("common.must_be_player", "&cThis command can only be run by a player."));
            return true;
        }

        if (!player.hasPermission("enchantcore.toggleanimations.use")) {
            ChatUtil.sendMessage(player, messageManager.getMessage("common.no_permission", "&cYou do not have permission."));
            return true;
        }

        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) {
            playerData = dataManager.loadPlayerData(player.getUniqueId());
        }

        if (playerData == null) {
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.toggleanimations.data_error", "&cCould not load your data. Please try again."));
            logger.warning("Could not load PlayerData for " + player.getName() + " in /toggleanimations");
            return true;
        }

        // Toggle the setting
        playerData.setShowEnchantAnimations(!playerData.isShowEnchantAnimations());
        dataManager.savePlayerData(playerData, true); // Save the change

        // Send confirmation message
        if (playerData.isShowEnchantAnimations()) {
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.toggleanimations.enabled", "&aEnchantment animations enabled."));
        } else {
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.toggleanimations.disabled", "&cEnchantment animations disabled."));
        }
        return true;
    }
}
