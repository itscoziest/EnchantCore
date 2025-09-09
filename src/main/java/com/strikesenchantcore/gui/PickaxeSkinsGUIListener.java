package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.PDCUtil;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PickaxeSkinsGUIListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager;
    private final Logger logger;

    // This map is no longer needed for context, but is kept if your code relies on it
    // The new system in the main GUI listener is preferred
    private static final java.util.Map<java.util.UUID, ItemStack> openSkinsGuiPickaxes = new java.util.concurrent.ConcurrentHashMap<>();

    public PickaxeSkinsGUIListener(EnchantCore plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
        this.logger = plugin.getLogger();
    }

    // This method is kept for compatibility but is part of the old system
    public static void setPickaxeForSkinsGui(java.util.UUID playerUUID, ItemStack pickaxe) {
        openSkinsGuiPickaxes.put(playerUUID, pickaxe);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PickaxeSkinsGUI gui)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            // Get pickaxe directly from the GUI object - THIS IS THE FIX
            ItemStack pickaxeContext = gui.getPickaxe();

            if (pickaxeContext == null || pickaxeContext.getType() == Material.AIR || !PDCUtil.isEnchantCorePickaxe(pickaxeContext)) {
                ChatUtil.sendMessage(player, "&cError: Pickaxe context lost or invalid. Please reopen the menu.");
                player.closeInventory();
                return;
            }

            PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
            if (playerData == null && (playerData = dataManager.loadPlayerData(player.getUniqueId())) == null) {
                ChatUtil.sendMessage(player, "&cError: Could not load your data. Please try again.");
                player.closeInventory();
                return;
            }

            try {
                gui.handleClick(player, event.getSlot(), event.getCurrentItem(), event.getClick());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling PickaxeSkinsGUI click for " + player.getName(), e);
                ChatUtil.sendMessage(player, "&cAn internal error occurred.");
                player.closeInventory();
            }
        }
    }
}