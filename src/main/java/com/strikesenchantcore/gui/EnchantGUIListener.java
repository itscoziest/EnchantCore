package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.util.ChatUtil; // Keep for sending error messages
import com.strikesenchantcore.util.PDCUtil;  // Keep for pickaxe check

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // Use EventPriority if needed, though default is fine here
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
import java.util.logging.Logger; // Import logger


public class EnchantGUIListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager;
    private final Logger logger; // Cache logger

    // Static map to temporarily store the specific pickaxe ItemStack associated with a player's open GUI instance.
    // This is necessary because the InventoryClickEvent doesn't directly know which item triggered the GUI opening.
    // It MUST be cleaned up in onInventoryClose to prevent memory leaks.
    private static final Map<UUID, ItemStack> openGuiPickaxes = new ConcurrentHashMap<>();

    public EnchantGUIListener(EnchantCore plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
        this.logger = plugin.getLogger();
    }

    /**
     * Stores the pickaxe context when a player opens the GUI.
     * Must be called right before player.openInventory(gui.getInventory());
     * @param playerUUID The player's UUID.
     * @param pickaxe The specific pickaxe ItemStack instance used to open the GUI.
     */
    public static void setPickaxeForGui(UUID playerUUID, ItemStack pickaxe) {
        openGuiPickaxes.put(playerUUID, pickaxe);
    }

    /**
     * Removes the pickaxe context when the player closes the GUI.
     * Essential for preventing memory leaks.
     * @param playerUUID The player's UUID.
     */
    public static void removePickaxeForGui(UUID playerUUID) {
        openGuiPickaxes.remove(playerUUID);
    }


    @EventHandler(ignoreCancelled = true) // Ignore clicks if event is already cancelled
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();

        // Check if the clicked inventory is our EnchantGUI
        if (holder instanceof EnchantGUI) {
            EnchantGUI gui = (EnchantGUI) holder;
            // Added Null Check for GUI instance safety
            if (gui == null) {
                logger.warning("InventoryClickEvent holder was instanceof EnchantGUI but gui was null!");
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

            // Always cancel interaction with the GUI inventory slots themselves
            event.setCancelled(true);

            // Only process clicks within the top GUI inventory
            if (event.getClickedInventory() == topInventory) {
                ItemStack clickedItem = event.getCurrentItem(); // Item in the clicked slot
                int slot = event.getSlot();

                // Retrieve the pickaxe associated with this specific GUI instance
                ItemStack pickaxeContext = openGuiPickaxes.get(playerUUID);

                // --- Re-verify Pickaxe Context and PlayerData ---
                if (pickaxeContext == null || pickaxeContext.getType() == Material.AIR || !PDCUtil.isEnchantCorePickaxe(pickaxeContext)) {
                    ChatUtil.sendMessage(player, "&cError: Pickaxe context lost or invalid. Please reopen the menu.");
                    player.closeInventory(); // Close broken GUI state
                    logger.warning("handleClick aborted for " + player.getName() + ": pickaxeContext was null, AIR, or not an EC Pickaxe in listener map.");
                    return;
                }
                PlayerData playerData = dataManager.getPlayerData(playerUUID);
                if (playerData == null) { // Try loading if missing (should ideally be loaded already)
                    playerData = dataManager.loadPlayerData(playerUUID);
                    if (playerData == null) { // Still null
                        ChatUtil.sendMessage(player, "&cError: Could not load your data for purchase. Please try again.");
                        player.closeInventory();
                        logger.warning("handleClick aborted for " + player.getName() + ": could not load PlayerData.");
                        return;
                    }
                }
                // --- End Verification ---


                // Call the GUI's handler method within a try-catch
                try {
                    gui.handleClick(player, slot, clickedItem, pickaxeContext, event.getClick());
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error handling GUI click for " + player.getName() + " in slot " + slot, e);
                    ChatUtil.sendMessage(player, "&cAn internal error occurred processing your click.");
                    player.closeInventory(); // Close GUI on error
                }
            }
            // Prevent shift-clicking from player inv INTO the top GUI
            else if (event.getClickedInventory() == event.getView().getBottomInventory() && event.isShiftClick()) {
                event.setCancelled(true);
            }
            // Allow interactions within the player's own inventory while GUI is open
            // (Handled by not cancelling if event.getClickedInventory() == bottomInventory and not shift-click)
        }
    }

    /**
     * Cleans up the pickaxe context map when the EnchantGUI is closed.
     * @param event The InventoryCloseEvent.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Check if the closed inventory was our EnchantGUI
        if (event.getInventory().getHolder() instanceof EnchantGUI) {
            // Remove the player's entry from the context map to prevent memory leaks
            UUID playerUUID = event.getPlayer().getUniqueId();
            ItemStack removed = openGuiPickaxes.remove(playerUUID); // Remove and get potentially removed item
            if (removed != null && plugin.getConfigManager().isDebugMode()) {
                logger.finest("Removed pickaxe context for " + event.getPlayer().getName() + " from listener map on GUI close.");
            }
        }
    }
}