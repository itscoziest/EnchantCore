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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PickaxeSkinsGUIListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager;
    private final Logger logger;

    // Static map to store pickaxe context for PickaxeSkinsGUI instances
    private static final Map<UUID, ItemStack> openSkinsGuiPickaxes = new ConcurrentHashMap<>();

    public PickaxeSkinsGUIListener(EnchantCore plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
        this.logger = plugin.getLogger();
    }

    /**
     * Stores the pickaxe context when a player opens the PickaxeSkinsGUI.
     * Must be called right before player.openInventory(gui.getInventory());
     */
    public static void setPickaxeForSkinsGui(UUID playerUUID, ItemStack pickaxe) {
        openSkinsGuiPickaxes.put(playerUUID, pickaxe);
    }

    /**
     * Removes the pickaxe context when the player closes the PickaxeSkinsGUI.
     */
    public static void removePickaxeForSkinsGui(UUID playerUUID) {
        openSkinsGuiPickaxes.remove(playerUUID);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();

        // Check if the clicked inventory is our PickaxeSkinsGUI
        if (holder instanceof PickaxeSkinsGUI) {
            PickaxeSkinsGUI gui = (PickaxeSkinsGUI) holder;

            if (gui == null) {
                logger.warning("InventoryClickEvent holder was instanceof PickaxeSkinsGUI but gui was null!");
                event.setCancelled(true);
                return;
            }

            // Ensure clicker is a player
            if (!(event.getWhoClicked() instanceof Player)) {
                event.setCancelled(true);
                return;
            }
            Player player = (Player) event.getWhoClicked();
            UUID playerUUID = player.getUniqueId();

            // Always cancel interaction with the GUI inventory slots
            event.setCancelled(true);

            // Only process clicks within the top GUI inventory
            if (event.getClickedInventory() == topInventory) {
                ItemStack clickedItem = event.getCurrentItem();
                int slot = event.getSlot();

                // Retrieve the pickaxe associated with this GUI instance
                ItemStack pickaxeContext = openSkinsGuiPickaxes.get(playerUUID);

                // Verify pickaxe context and PlayerData
                if (pickaxeContext == null || pickaxeContext.getType() == Material.AIR || !PDCUtil.isEnchantCorePickaxe(pickaxeContext)) {
                    ChatUtil.sendMessage(player, "&cError: Pickaxe context lost or invalid. Please reopen the menu.");
                    player.closeInventory();
                    logger.warning("PickaxeSkinsGUI click aborted for " + player.getName() + ": pickaxeContext was null, AIR, or not an EC Pickaxe.");
                    return;
                }

                PlayerData playerData = dataManager.getPlayerData(playerUUID);
                if (playerData == null) {
                    playerData = dataManager.loadPlayerData(playerUUID);
                    if (playerData == null) {
                        ChatUtil.sendMessage(player, "&cError: Could not load your data. Please try again.");
                        player.closeInventory();
                        logger.warning("PickaxeSkinsGUI click aborted for " + player.getName() + ": could not load PlayerData.");
                        return;
                    }
                }

                // Call the GUI's handler method
                try {
                    gui.handleClick(player, slot, clickedItem, event.getClick());
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error handling PickaxeSkinsGUI click for " + player.getName() + " in slot " + slot, e);
                    ChatUtil.sendMessage(player, "&cAn internal error occurred processing your click.");
                    player.closeInventory();
                }
            }
            // Prevent shift-clicking from player inventory INTO the GUI
            else if (event.getClickedInventory() == event.getView().getBottomInventory() && event.isShiftClick()) {
                event.setCancelled(true);
            }
        }
    }




    /**
     * Cleans up the pickaxe context map when the PickaxeSkinsGUI is closed.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PickaxeSkinsGUI) {
            UUID playerUUID = event.getPlayer().getUniqueId();
            ItemStack removed = openSkinsGuiPickaxes.remove(playerUUID);
            if (removed != null && plugin.getConfigManager().isDebugMode()) {
                logger.finest("Removed pickaxe context for " + event.getPlayer().getName() + " from PickaxeSkinsGUI listener map on GUI close.");
            }
        }
    }
}