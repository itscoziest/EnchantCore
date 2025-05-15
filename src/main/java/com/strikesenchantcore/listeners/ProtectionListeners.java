package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.config.PickaxeConfig; // Keep import
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.PDCUtil;

import org.bukkit.GameRule; // Import GameRule
import org.bukkit.Location; // Import Location
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull; // Import NotNull
import org.jetbrains.annotations.Nullable; // <<<--- ADDED THIS IMPORT

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger; // Import Logger

/**
 * Listens for events related to item protection features like
 * keep-inventory, prevent-drop, prevent-store for the EnchantCore pickaxe.
 */
public class ProtectionListeners implements Listener {

    private final EnchantCore plugin;
    private final PickaxeConfig pickaxeConfig; // Cached instance
    private final MessageManager messageManager; // Cached instance
    private final Logger logger; // Cache logger

    public ProtectionListeners(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        // Cache manager/config instances
        this.pickaxeConfig = plugin.getPickaxeConfig();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger();

        // Validate mandatory instances
        if (this.pickaxeConfig == null || this.messageManager == null) {
            logger.severe("PickaxeConfig or MessageManager is null in ProtectionListeners! Protection features may not work.");
        }
    }

    /**
     * Handles keeping the EnchantCore pickaxe on player death if configured.
     */
    @EventHandler(priority = EventPriority.HIGH) // High priority to modify drops before other plugins potentially clear them
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Skip if config is null or feature disabled
        if (pickaxeConfig == null || !pickaxeConfig.isKeepInventory()) return;

        Player player = event.getEntity();
        List<ItemStack> itemsToKeep = new ArrayList<>();

        // Iterate through the drops list safely
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            // Check if the item is a valid EC Pickaxe
            if (PDCUtil.isEnchantCorePickaxe(item)) {
                // Add a clone to our temporary list and remove original from drops
                itemsToKeep.add(item.clone());
                iterator.remove();
                if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check configManager
                    logger.fine("[Protection] Keeping pickaxe " + item.getType() + " for " + player.getName() + " on death.");
                }
            }
        }

        // If we removed any pickaxes from drops, schedule task to give them back
        if (!itemsToKeep.isEmpty()) {
            // Schedule task to run 1 tick after the death event fully processes
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Check player is still online when the task runs (respawn)
                if (player.isOnline()) {
                    // Add items back to inventory
                    var leftovers = player.getInventory().addItem(itemsToKeep.toArray(new ItemStack[0]));
                    // If inventory is full, drop the remaining items at the player's location
                    if (!leftovers.isEmpty()) {
                        Location loc = player.getLocation();
                        if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check configManager
                            logger.fine("[Protection] Player " + player.getName() + "'s inventory full on respawn, dropping " + leftovers.size() + " pickaxe(s).");
                        }
                        for (ItemStack leftover : leftovers.values()) {
                            player.getWorld().dropItemNaturally(loc, leftover);
                        }
                    } else if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check configManager
                        logger.fine("[Protection] Returned " + itemsToKeep.size() + " pickaxe(s) to " + player.getName() + " on respawn.");
                    }
                } else if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check configManager
                    logger.fine("[Protection] Player " + player.getName() + " logged off before pickaxe could be returned after death.");
                }
            }, 1L);
        }
    }


    /**
     * Prevents dropping the EnchantCore pickaxe (e.g., via 'Q' key) if configured.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        // Skip if config is null or feature disabled
        if (pickaxeConfig == null || !pickaxeConfig.isPreventDrop() || messageManager == null) return;

        Item itemEntity = event.getItemDrop();
        if (itemEntity == null) return; // Should not happen, but safety check
        ItemStack itemStack = itemEntity.getItemStack();

        // Check if the dropped item is an EC Pickaxe
        if (PDCUtil.isEnchantCorePickaxe(itemStack)) {
            event.setCancelled(true); // Prevent the drop
            // Send feedback message using MessageManager
            ChatUtil.sendMessage(event.getPlayer(), messageManager.getMessage("listeners.protection.cannot_drop", "&cYou cannot drop this pickaxe!"));
            if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check configManager
                logger.fine("[Protection] Prevented drop for pickaxe " + itemStack.getType() + " by " + event.getPlayer().getName());
            }
        }
    }


    /**
     * Prevents storing the EnchantCore pickaxe in disallowed inventories or moving it
     * out of the player inventory based on configuration.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Skip if config is null or managers are null
        if (pickaxeConfig == null || messageManager == null) return;
        // Get config settings once
        boolean preventStore = pickaxeConfig.isPreventStore();
        boolean allowMove = pickaxeConfig.isAllowInventoryMove();
        // If no relevant protection is enabled, exit early
        if (!preventStore) return; // allowMove only matters if preventStore is true

        // Ensure we have a player
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clickedItem = event.getCurrentItem(); // Item being clicked ON in the inventory slot
        ItemStack cursorItem = event.getCursor();      // Item being held ON the cursor

        boolean isPickaxeOnCursor = PDCUtil.isEnchantCorePickaxe(cursorItem);
        boolean isPickaxeInSlot = PDCUtil.isEnchantCorePickaxe(clickedItem);

        // If neither the cursor nor the clicked slot involves our pickaxe, ignore
        if (!isPickaxeOnCursor && !isPickaxeInSlot) return;

        Inventory clickedInv = event.getClickedInventory(); // The inventory where the click happened
        Inventory topInv = event.getView().getTopInventory(); // The top inventory view
        InventoryAction action = event.getAction(); // The type of click action

        boolean cancelled = false; // Flag to track if we cancelled the event

        // --- Prevent-Store Logic ---
        // Scenario 1: Trying to place pickaxe (on cursor) INTO a non-player inventory.
        if (isPickaxeOnCursor && clickedInv != null && clickedInv.getType() != InventoryType.PLAYER) {
            // Check if it's one of the explicitly disallowed types
            if (isBlockedInventoryType(clickedInv.getType())) {
                cancelled = true;
                ChatUtil.sendMessage(player, messageManager.getMessage("listeners.protection.cannot_place", "&cCannot place pickaxe here!"));
            }
            // Allow placing into Crafting Result slot? Usually handled by server, but block just in case?
            else if (clickedInv.getType() == InventoryType.CRAFTING && event.getSlotType() == InventoryType.SlotType.RESULT) {
                cancelled = true; // Prevent placing into crafting result
                // No message needed here?
            }
            // Allow placing into player's own crafting input grid (part of top inv view when player inv open)
            else if (topInv.getType() == InventoryType.CRAFTING && clickedInv == topInv && event.getSlotType() == InventoryType.SlotType.CRAFTING) {
                // Allow placing into crafting grid - do nothing here, let allowMove handle un-cancelling later if needed
            }
            // Block placing into any other non-player inventory by default if preventStore=true
            else {
                cancelled = true;
                ChatUtil.sendMessage(player, messageManager.getMessage("listeners.protection.cannot_store", "&cCannot store pickaxe here!"));
            }
        }

        // Scenario 2: Trying to move pickaxe (in slot) OUT of player inventory via specific actions.
        if (!cancelled && isPickaxeInSlot && clickedInv != null && clickedInv.getType() == InventoryType.PLAYER) {
            // Shift-Click: Moving from player inv to another open inventory (e.g., chest)
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                // If the target inventory isn't the player's own inventory (check top inv)
                if (topInv != null && topInv.getType() != InventoryType.PLAYER && topInv.getType() != InventoryType.CRAFTING) {
                    cancelled = true;
                    ChatUtil.sendMessage(player, messageManager.getMessage("listeners.protection.cannot_store", "&cCannot store pickaxe here!"));
                }
            }
            // Hotbar Swap (Pressing number key while hovering over pickaxe): Moves pickaxe to hotbar slot, potentially replacing item there.
            // This is generally allowed within the player inventory, handled by allowMove logic below.
            else if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
                // This action itself stays within player inventory, so usually allowed by allowMove.
                // No cancellation needed here based *only* on preventStore.
            }
            // Picking up the item to the cursor is handled by Scenario 1 when placing it back down.
        }
        // --- End Prevent-Store Logic ---


        // --- Allow-Inventory-Move Logic ---
        // If the event was cancelled by the preventStore logic above, check if allowMove permits it.
        if (cancelled && allowMove) {
            // Check if the action results in the pickaxe staying *within* the player's inventory space.
            // This logic attempts to allow internal rearrangements while still preventing external storage.

            // Case A: Placing pickaxe from cursor INTO player inventory slot.
            if (isPickaxeOnCursor && clickedInv != null && clickedInv.getType() == InventoryType.PLAYER) {
                // If the target slot is valid within player inv (e.g., not RESULT, ARMOR depends on config?)
                if (event.getSlotType() != InventoryType.SlotType.RESULT) { // Don't allow placing into result slots
                    cancelled = false; // Un-cancel the event
                }
            }
            // Case B: Picking up pickaxe from player slot TO cursor.
            else if (isPickaxeInSlot && clickedInv != null && clickedInv.getType() == InventoryType.PLAYER &&
                    (action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF ||
                            action == InventoryAction.PICKUP_SOME || action == InventoryAction.PICKUP_ONE)) {
                cancelled = false; // Allow picking up to cursor
            }
            // Case C: Swapping item on cursor with pickaxe in player slot.
            else if (isPickaxeInSlot && clickedInv != null && clickedInv.getType() == InventoryType.PLAYER && action == InventoryAction.SWAP_WITH_CURSOR) {
                cancelled = false; // Allow swapping within player inv
            }
            // Case D: Hotbar swap actions (Number keys, Offhand swap F) - these usually stay within player inv.
            else if ((isPickaxeInSlot || isPickaxeOnCursor) && // Pickaxe involved
                    (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD)) {
                // Check if the click originated within the player's inventory view (bottom or top if crafting)
                if (clickedInv != null && (clickedInv.getType() == InventoryType.PLAYER || clickedInv.getType() == InventoryType.CRAFTING)) {
                    cancelled = false; // Allow hotbar swaps within player view
                }
            }
            // Case E: Shift-click (MOVE_TO_OTHER_INVENTORY) FROM player inventory.
            // If it was cancelled above, it was trying to move OUT. If allowMove is true,
            // we might reconsider IF the destination is *also* the player inventory (e.g. armor -> main inv).
            // However, reliably detecting the *target* inventory of a shift-click is complex.
            // The safest approach here is: if preventStore cancelled it, allowMove does NOT override it
            // for MOVE_TO_OTHER_INVENTORY actions, because we assume preventStore correctly identified
            // an attempt to move it *outside* the player's direct inventory space.
            // The other cases (A, B, C, D) handle explicit internal moves.

            if (cancelled && plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check configManager
                logger.fine("[Protection] Click cancelled by preventStore, allowMove did not override. Action: " + action + " ClickedInv: " + (clickedInv != null ? clickedInv.getType() : "null"));
            } else if (!cancelled && plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check configManager
                logger.finest("[Protection] Click un-cancelled by allowMove. Action: " + action + " ClickedInv: " + (clickedInv != null ? clickedInv.getType() : "null"));
            }

        } // End if (cancelled && allowMove)


        // Finally, apply the cancellation state
        if (cancelled) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents storing the EnchantCore pickaxe in disallowed inventories via dragging.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        // Skip if config is null or feature disabled
        if (pickaxeConfig == null || !pickaxeConfig.isPreventStore() || messageManager == null) return;

        // Check the item being dragged
        ItemStack draggedItem = event.getOldCursor(); // Item on cursor at the start of the drag
        if (!PDCUtil.isEnchantCorePickaxe(draggedItem)) return; // Not dragging our pickaxe

        // Ensure player exists
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (topInv == null) return; // Should not happen
        InventoryType topInvType = topInv.getType();
        int topInvSize = topInv.getSize();

        // Check if *any* slot the item is dragged over is disallowed
        for (int slot : event.getRawSlots()) {
            // Is the slot in the top inventory view?
            if (slot < topInvSize) {
                // Allow dragging within player's crafting grid (part of top view when player inv is open)
                if (topInvType == InventoryType.CRAFTING && slot < 5) { // Slots 0-4 are crafting grid + result
                    // Allow dragging into input slots (1-4), block result slot (0)
                    if (slot == 0) { // Crafting Result slot
                        event.setCancelled(true);
                        ChatUtil.sendMessage(player, messageManager.getMessage("listeners.protection.cannot_place", "&cCannot place pickaxe here!"));
                        if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check
                            logger.fine("[Protection] Prevented drag into crafting result slot by " + player.getName());
                        }
                        return; // Cancelled
                    }
                    // Otherwise (slots 1-4), allow drag - continue checking other slots in the drag
                    continue;
                }

                // If it's not the player's inventory or crafting grid, check if it's a blocked type
                if (topInvType != InventoryType.PLAYER) {
                    if (isBlockedInventoryType(topInvType)) {
                        event.setCancelled(true);
                        ChatUtil.sendMessage(player, messageManager.getMessage("listeners.protection.cannot_place", "&cCannot place pickaxe here!"));
                        if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check
                            logger.fine("[Protection] Prevented drag into blocked inventory type " + topInvType + " by " + player.getName());
                        }
                        return; // Cancelled
                    } else {
                        // If preventStore is true, block dragging into *any* non-player inventory by default
                        // unless specifically allowed (like crafting grid above)
                        event.setCancelled(true);
                        ChatUtil.sendMessage(player, messageManager.getMessage("listeners.protection.cannot_store", "&cCannot store pickaxe here!"));
                        if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugMode()) { // Null check
                            logger.fine("[Protection] Prevented drag into non-player inventory type " + topInvType + " by " + player.getName());
                        }
                        return; // Cancelled
                    }
                }
            }
            // If slot >= topInvSize, it's in the player's inventory (bottom view), which is allowed.
        }
        // If loop completes without cancellation, the drag is permitted.
    }

    /**
     * Helper method to check if an inventory type is one where the pickaxe should explicitly NOT be placed.
     * @param invType The InventoryType to check. Can be null.
     * @return True if placing the pickaxe here should be blocked, false otherwise.
     */
    private boolean isBlockedInventoryType(@Nullable InventoryType invType) { // Marked param as @Nullable
        if (invType == null) return false; // Treat null type as not explicitly blocked

        // Using Java 17 compatible switch statement
        switch (invType) {
            case ANVIL:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case GRINDSTONE:
            case MERCHANT:
            case ENCHANTING:
            case BEACON:
            case CARTOGRAPHY:
            case LOOM:
            case STONECUTTER:
            case SMITHING:
            case BREWING:
                return true; // These are explicitly blocked
            // Allow in CHEST, DISPENSER, DROPPER, HOPPER, BARREL, SHULKER_BOX etc. by default
            // (but preventStore might block them anyway if not PLAYER inventory)
            default:
                return false; // Not in the blocked list
        }
    }
}