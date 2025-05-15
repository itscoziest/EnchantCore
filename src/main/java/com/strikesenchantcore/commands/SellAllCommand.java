package com.strikesenchantcore.commands;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.AutoSellConfig;
import com.strikesenchantcore.config.MessageManager; // Import MessageManager
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.VaultHook;
import org.bukkit.Material;
import org.bukkit.Sound; // Import Sound
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger; // Import Logger

/**
 * Handles the /sellall command.
 */
public class SellAllCommand implements CommandExecutor {

    private final EnchantCore plugin;
    private final AutoSellConfig autoSellConfig; // Cached
    private final VaultHook vaultHook;          // Cached
    private final MessageManager messageManager; // Cached
    private final Logger logger;              // Cached

    // Default messages (used if keys missing from messages.yml)
    private static final String DEF_NO_PERMISSION = "&cYou do not have permission to use this command.";
    private static final String DEF_MUST_BE_PLAYER = "&cThis command can only be run by a player.";
    private static final String DEF_VAULT_DISABLED = "&cVault economy is not enabled. Cannot sell items.";
    private static final String DEF_NOTHING_TO_SELL = "&eYou have nothing in your inventory to sell.";
    private static final String DEF_SELL_SUCCESS = "&aSold inventory items for a total of &e%total_value%&a!";
    private static final String DEF_SELL_ERROR = "&cAn error occurred while trying to sell items.";

    public SellAllCommand(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        // Cache instances
        this.autoSellConfig = plugin.getAutoSellConfig();
        this.vaultHook = plugin.getVaultHook();
        this.messageManager = plugin.getMessageManager();

        // Validate dependencies
        if (this.autoSellConfig == null || this.vaultHook == null || this.messageManager == null) {
            logger.severe("AutoSellConfig, VaultHook, or MessageManager is null in SellAllCommand! Command may fail.");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Manager check
        if (autoSellConfig == null || vaultHook == null || messageManager == null) {
            ChatUtil.sendMessage(sender, "&cCommand error: Core components not initialized.");
            return true;
        }

        if (!(sender instanceof Player player)) { // Use pattern variable binding
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.sellall.must_be_player", DEF_MUST_BE_PLAYER));
            return true;
        }

        // Check permission
        if (!player.hasPermission("enchantcore.sellall")) {
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.sellall.no_permission", DEF_NO_PERMISSION));
            return true;
        }

        // Check Vault
        if (!vaultHook.isEnabled()) {
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.sellall.vault_disabled", DEF_VAULT_DISABLED));
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents(); // Gets all slots, including armor/offhand
        double totalValue = 0;
        int itemsSoldCount = 0;
        Map<Integer, ItemStack> itemsToRemove = new HashMap<>(); // Stores Slot index -> Item to remove

        // Iterate through all inventory slots
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];

            // Skip empty slots and armor/offhand slots
            // Armor slots are 36-39, Offhand is 40 in PlayerInventory#getContents
            if (item == null || item.getType() == Material.AIR || (i >= 36 && i <= 40)) {
                continue;
            }

            // Check if item has a sell price configured
            double price = autoSellConfig.getSellPrice(item.getType());
            if (price > 0) {
                // Add value * item amount to total
                totalValue += price * item.getAmount();
                itemsSoldCount += item.getAmount();
                // Mark this slot index for removal
                itemsToRemove.put(i, item);
            }
        }

        // If nothing has value, inform the player
        if (itemsToRemove.isEmpty()) {
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.sellall.nothing_to_sell", DEF_NOTHING_TO_SELL));
            return true;
        }

        // Attempt to deposit money FIRST
        if (vaultHook.deposit(player, totalValue)) {
            // If deposit succeeds, remove the items from inventory
            for (Map.Entry<Integer, ItemStack> entry : itemsToRemove.entrySet()) {
                inventory.setItem(entry.getKey(), null); // Set slot to null to remove item
            }
            // Send success message (use VaultHook formatting)
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.sellall.success", DEF_SELL_SUCCESS)
                    .replace("%total_value%", vaultHook.format(totalValue)));
            // Optional: Play sound
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } catch (Exception e) {
                // Ignore sound errors silently or log warning if needed
                // logger.log(Level.WARNING, "Error playing SellAll sound for " + player.getName(), e);
            }
        } else {
            // Deposit failed
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.sellall.error", DEF_SELL_ERROR));
            logger.warning("SellAll failed to deposit " + totalValue + " for " + player.getName());
        }

        return true;
    }
}