package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.MessageManager; // Keep import
import com.strikesenchantcore.gui.EnchantGUI;
import com.strikesenchantcore.gui.EnchantGUIListener;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.ColorUtils; // Keep for potential future use? Not directly used now.
import com.strikesenchantcore.util.PDCUtil;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;

import org.bukkit.Material;
import org.bukkit.block.Block; // Import Block
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger

public class PlayerInteractListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager; // Cache instance
    private final MessageManager messageManager; // Cache instance
    private final Logger logger; // Cache logger

    public PlayerInteractListener(EnchantCore plugin) {
        this.plugin = plugin;
        // Cache manager instances on creation
        this.dataManager = plugin.getPlayerDataManager();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger();

        // Validate mandatory managers
        if (this.dataManager == null || this.messageManager == null) {
            logger.severe("PlayerDataManager or MessageManager is null in PlayerInteractListener! GUI opening may fail.");
            // Consider if the listener should unregister itself or throw an error
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Ensure managers are available (extra safety check)
        if (dataManager == null || messageManager == null) {
            // Log an error here if needed, but constructor should handle initial check
            return;
        }

        Player player = event.getPlayer();
        Action action = event.getAction();

        // We only care about right-clicks (air or block) performed with the main hand
        if ((action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if the item is a valid EnchantCore pickaxe
        if (PDCUtil.isEnchantCorePickaxe(item)) {

            // --- Nuke Active Check ---
            // Prevent opening GUI or doing anything else if Nuke is active for this player
            if (BlockBreakListener.nukeActivePlayers.contains(player.getUniqueId())) {
                // Persistent title is already handled by Nuke activation logic in BlockBreakListener
                // Optionally send a chat message for clarity
                ChatUtil.sendMessage(player, messageManager.getMessage("listeners.nuke.interact_denied", "&cYou cannot use pickaxe abilities while Nuke is active!"));
                event.setCancelled(true); // Cancel the interaction event
                return; // Stop further processing
            }
            // --- END Nuke Active Check ---


            // --- Interactable Block Check ---
            // If right-clicking a block...
            if (action == Action.RIGHT_CLICK_BLOCK) {
                Block clickedBlock = event.getClickedBlock();
                // ...and the block is interactable...
                if (clickedBlock != null && clickedBlock.getType().isInteractable()) {
                    // ...and the player is NOT sneaking...
                    if (!player.isSneaking()) {
                        // ...and it's a common block we want to allow normal interaction with...
                        if (isCommonInteractable(clickedBlock.getType())) {
                            // ...then do nothing here, allowing the default interaction.
                            return;
                        }
                    }
                    // If sneaking while clicking an interactable block, or clicking a non-common interactable,
                    // we *will* proceed to open the GUI below (event cancellation handles it).
                }
            }
            // --- End Interactable Block Check ---


            // --- Open GUI Logic ---
            // Prevent default item use (like placing blocks if hand was technically empty before event?)
            // and cancel the event to ensure GUI opens cleanly without side effects.
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);

            // Permission Check
            if (!player.hasPermission("enchantcore.gui.open")) {
                ChatUtil.sendMessage(player, messageManager.getMessage("gui.no_permission", "&cNo permission to open enchant menu."));
                return;
            }

            // Fetch PlayerData (load if not cached)
            PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
            if (playerData == null) {
                playerData = dataManager.loadPlayerData(player.getUniqueId());
                if (playerData == null) { // Still null after load attempt
                    ChatUtil.sendMessage(player, messageManager.getMessage("gui.data_error", "&cCould not load your data to open the menu."));
                    logger.warning("Failed to load PlayerData for " + player.getName() + " when opening GUI.");
                    return;
                }
            }

            // Open the Enchant GUI
            try {
                // Pass the specific ItemStack instance that was clicked with
                EnchantGUI gui = new EnchantGUI(plugin, player, playerData, item);
                // Store the pickaxe context for the listener to use on click events
                EnchantGUIListener.setPickaxeForGui(player.getUniqueId(), item);
                // Open the inventory for the player
                gui.open();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error opening EnchantGUI for player " + player.getName(), e);
                ChatUtil.sendMessage(player, messageManager.getMessage("gui.open_error", "&cAn error occurred while opening the enchant menu."));
            }
            // --- End Open GUI Logic ---

        } // End if isEnchantCorePickaxe
    } // End onPlayerInteract

    /**
     * Helper method to identify common blocks that should allow normal right-click interaction
     * even when holding the pickaxe (unless sneaking). Prevents GUI opening accidentally.
     * Uses Java 17 compatible if/else if structure.
     * @param material The Material of the clicked block.
     * @return True if it's a common interactable block, false otherwise.
     */
    private boolean isCommonInteractable(Material material) {
        // Check Material's built-in interactable flag first for efficiency
        if (material == null || !material.isInteractable()) {
            return false;
        }
        String name = material.toString();

        // --- Reverted to if/else if and String.contains ---
        if (name.contains("CHEST") || name.equals("BARREL") || name.contains("SHULKER_BOX") || // Storage
                name.equals("CRAFTING_TABLE") || name.equals("FURNACE") || name.equals("BLAST_FURNACE") || // Crafting/Smelting
                name.equals("SMOKER") || name.equals("ANVIL") || name.equals("ENCHANTING_TABLE") ||
                name.equals("BREWING_STAND") || name.equals("GRINDSTONE") || name.equals("LOOM") ||
                name.equals("CARTOGRAPHY_TABLE") || name.equals("STONECUTTER") || name.equals("SMITHING_TABLE") ||
                name.equals("LECTERN") || name.equals("BEACON") || name.equals("JUKEBOX") || name.equals("NOTE_BLOCK") || // Stations
                name.contains("DOOR") || name.contains("GATE") || name.contains("TRAPDOOR") || // Doors/Gates
                name.contains("BUTTON") || name.equals("LEVER") || name.contains("PRESSURE_PLATE") || // Simple Redstone Activators
                name.equals("COMPARATOR") || name.equals("REPEATER") || name.equals("DAYLIGHT_DETECTOR") || // Redstone components
                name.equals("TRIPWIRE_HOOK") || name.contains("_BED") || name.equals("BELL") || // Other misc interactables
                name.equals("RESPAWN_ANCHOR") || name.equals("COMPOSTER") || name.equals("CAULDRON")) // More misc
        {
            return true;
        } else {
            return false; // Default to false if not in the list
        }
        // --- End reverted logic ---
    }
} // End PlayerInteractListener class