package com.strikesenchantcore.util;

import com.strikesenchantcore.EnchantCore;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for ItemsAdder integration.
 * Handles creation of ItemStack objects from both vanilla materials and ItemsAdder custom items.
 */
public class ItemsAdderUtil {

    private final EnchantCore plugin;
    private final Logger logger;
    private static boolean itemsAdderAvailable = false;

    static {
        try {
            Class.forName("dev.lone.itemsadder.api.CustomStack");
            itemsAdderAvailable = true;
        } catch (ClassNotFoundException e) {
            itemsAdderAvailable = false;
        }
    }

    public ItemsAdderUtil(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        if (itemsAdderAvailable) {
            logger.info("ItemsAdder integration enabled!");
        } else {
            logger.info("ItemsAdder not found - using vanilla materials only.");
        }
    }

    /**
     * Checks if ItemsAdder is available on the server.
     * @return true if ItemsAdder is loaded and available
     */
    public static boolean isItemsAdderAvailable() {
        return itemsAdderAvailable;
    }

    /**
     * Creates an ItemStack from either a vanilla Material or ItemsAdder custom item.
     *
     * @param materialName The material name (e.g., "DIAMOND_PICKAXE")
     * @param itemsAdderID The ItemsAdder item ID (e.g., "cyber_set:cyber_purple_pickaxe"), can be null
     * @return ItemStack created from the appropriate source, or null if both fail
     */
    @Nullable
    public ItemStack createItemStack(@NotNull String materialName, @Nullable String itemsAdderID) {
        // Try ItemsAdder first if ID is provided and ItemsAdder is available
        logger.info("DEBUG: ItemsAdder available: " + itemsAdderAvailable + ", ID provided: " + itemsAdderID);
        if (itemsAdderID != null && !itemsAdderID.trim().isEmpty() && itemsAdderAvailable) {
            try {
                logger.info("DEBUG: Attempting to get CustomStack for: " + itemsAdderID.trim());
                CustomStack customStack = CustomStack.getInstance(itemsAdderID.trim());
                if (customStack != null) {
                    ItemStack itemStack = customStack.getItemStack();
                    if (itemStack != null) {
                        logger.info("DEBUG: Successfully created ItemStack from ItemsAdder: " + itemsAdderID + ", Material: " + itemStack.getType());
                        return itemStack;
                    } else {
                        logger.warning("DEBUG: CustomStack getItemStack() returned null for: " + itemsAdderID);
                    }
                } else {
                    logger.warning("DEBUG: CustomStack.getInstance() returned null for: " + itemsAdderID);
                }
                logger.warning("ItemsAdder item '" + itemsAdderID + "' not found or invalid. Falling back to vanilla material.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error creating ItemsAdder item '" + itemsAdderID + "'. Falling back to vanilla material: " + e.getMessage());
            }
        } else {
            logger.info("DEBUG: Skipping ItemsAdder, using vanilla fallback");
        }

        // Fallback to vanilla Material
        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            if (material == Material.AIR) {
                logger.warning("Material cannot be AIR. Returning null.");
                return null;
            }
            ItemStack itemStack = new ItemStack(material);
            logger.fine("Created ItemStack from vanilla material: " + materialName);
            return itemStack;
        } catch (IllegalArgumentException e) {
            logger.severe("Invalid vanilla material name: " + materialName + ". Cannot create ItemStack.");
            return null;
        }
    }
}