package com.strikesenchantcore.util;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.PickaxeConfig; // Import PickaxeConfig
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.enchants.EnchantRegistry; // Import EnchantRegistry
import org.bukkit.Material; // Import Material
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull; // Import NotNull
import org.jetbrains.annotations.Nullable; // Import Nullable

import java.util.UUID; // Keep UUID import if needed for other keys potentially
import java.util.logging.Logger; // Import Logger

/**
 * Utility class for interacting with Bukkit's PersistentDataContainer API,
 * specifically tailored for EnchantCore data storage on ItemStacks.
 */
public class PDCUtil {

    // Private constructor to prevent instantiation of utility class
    private PDCUtil() {}

    // Helper method to safely get the plugin instance
    @NotNull
    private static EnchantCore getPlugin() {
        // getInstance() throws an exception if called too early/late, which is intended
        // to catch programming errors.
        return EnchantCore.getInstance();
    }

    // Helper method to get the logger safely
    @NotNull
    private static Logger getLogger() {
        return getPlugin().getLogger();
    }


    // --- Key Definitions ---
    // It's generally safe to create these keys on demand using methods.
    @NotNull
    public static NamespacedKey getPickaxeTagKey() {
        return new NamespacedKey(getPlugin(), "enchantcore_pickaxe");
    }
    @NotNull
    public static NamespacedKey getPickaxeLevelKey() {
        return new NamespacedKey(getPlugin(), "pickaxe_level");
    }
    @NotNull
    public static NamespacedKey getPickaxeBlocksMinedKey() {
        return new NamespacedKey(getPlugin(), "pickaxe_blocks_mined");
    }
    // Note: Enchantment-specific keys are generated via EnchantmentWrapper.getPdcLevelKey()

    // --- Generic PDC Accessor Methods ---

    public static boolean setString(@Nullable ItemStack item, @NotNull NamespacedKey key, @NotNull String value) {
        if (item == null || item.getType() == Material.AIR) return false; // Check AIR too
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return item.setItemMeta(meta); // Return success of setting meta
    }

    @Nullable
    public static String getString(@Nullable ItemStack item, @NotNull NamespacedKey key) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static boolean setInt(@Nullable ItemStack item, @NotNull NamespacedKey key, int value) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        return item.setItemMeta(meta);
    }

    // Default value is returned if item/meta is null OR key is missing
    public static int getInt(@Nullable ItemStack item, @NotNull NamespacedKey key, int defaultValue) {
        if (item == null || item.getType() == Material.AIR) return defaultValue;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return defaultValue;
        return meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, defaultValue);
    }

    public static boolean setLong(@Nullable ItemStack item, @NotNull NamespacedKey key, long value) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, value);
        return item.setItemMeta(meta);
    }

    public static long getLong(@Nullable ItemStack item, @NotNull NamespacedKey key, long defaultValue) {
        if (item == null || item.getType() == Material.AIR) return defaultValue;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return defaultValue;
        return meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, defaultValue);
    }

    public static boolean setByte(@Nullable ItemStack item, @NotNull NamespacedKey key, byte value) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, value);
        return item.setItemMeta(meta);
    }

    public static byte getByte(@Nullable ItemStack item, @NotNull NamespacedKey key, byte defaultValue) {
        if (item == null || item.getType() == Material.AIR) return defaultValue;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return defaultValue;
        return meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.BYTE, defaultValue);
    }

    public static boolean hasKey(@Nullable ItemStack item, @NotNull NamespacedKey key, @NotNull PersistentDataType<?, ?> type) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key, type);
    }

    // --- Specific EnchantCore Pickaxe Methods ---

    /**
     * Checks if an ItemStack is a valid EnchantCore pickaxe by checking for its specific tag.
     * @param item The ItemStack to check (can be null).
     * @return True if it's a valid EnchantCore pickaxe, false otherwise.
     */
    public static boolean isEnchantCorePickaxe(@Nullable ItemStack item) {
        // Check tag key exists and its value is 1 (byte)
        return hasKey(item, getPickaxeTagKey(), PersistentDataType.BYTE) &&
                getByte(item, getPickaxeTagKey(), (byte) 0) == (byte) 1;
    }

    /**
     * Tags an ItemStack as an EnchantCore pickaxe.
     * Also initializes level and blocks mined PDC values if they don't exist,
     * using defaults from PickaxeConfig to ensure essential data is present.
     * @param item The ItemStack to tag (modified directly).
     * @return True if tagging (and setting meta) was successful, false otherwise.
     */
    public static boolean tagAsEnchantCorePickaxe(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        PickaxeConfig pConfig = getPlugin().getPickaxeConfig(); // Get config for defaults
        if (pConfig == null) {
            getLogger().severe("Cannot tag pickaxe: PickaxeConfig is null!");
            return false; // Cannot proceed without defaults
        }

        // Set the main tag
        pdc.set(getPickaxeTagKey(), PersistentDataType.BYTE, (byte) 1);

        // Initialize level and blocks mined IF THEY DON'T EXIST to prevent data inconsistency.
        // Uses the defaults defined for the first join pickaxe in pickaxe.yml.
        if (!pdc.has(getPickaxeLevelKey(), PersistentDataType.INTEGER)) {
            pdc.set(getPickaxeLevelKey(), PersistentDataType.INTEGER, pConfig.getFirstJoinLevel());
            if (getPlugin().getConfigManager().isDebugMode()) {
                getLogger().fine("Initialized pickaxe level PDC for item " + item.getType() + " to default: " + pConfig.getFirstJoinLevel());
            }
        }
        if (!pdc.has(getPickaxeBlocksMinedKey(), PersistentDataType.LONG)) {
            pdc.set(getPickaxeBlocksMinedKey(), PersistentDataType.LONG, pConfig.getFirstJoinBlocksMined());
            if (getPlugin().getConfigManager().isDebugMode()) {
                getLogger().fine("Initialized pickaxe blocks mined PDC for item " + item.getType() + " to default: " + pConfig.getFirstJoinBlocksMined());
            }
        }

        return item.setItemMeta(meta); // Apply changes
    }

    /**
     * Gets the level stored in the pickaxe's PDC.
     * Returns the configured default level (from PickaxeConfig's FirstJoinLevel) if the item
     * isn't a valid pickaxe or the level key is missing.
     * @param pickaxe The pickaxe ItemStack (can be null).
     * @return The pickaxe level, or the default if unavailable.
     */
    public static int getPickaxeLevel(@Nullable ItemStack pickaxe) {
        PickaxeConfig pConfig = getPlugin().getPickaxeConfig();
        int defaultValue = (pConfig != null) ? pConfig.getFirstJoinLevel() : 1; // Fallback default if config fails

        if (!isEnchantCorePickaxe(pickaxe)) { // Includes null/air check via isEnchantCorePickaxe
            return defaultValue;
        }
        // Use the same default value if the key is present but somehow invalid, or just missing
        return getInt(pickaxe, getPickaxeLevelKey(), defaultValue);
    }

    /**
     * Sets the level in the pickaxe's PDC. Only applies if it's a valid EC pickaxe.
     * @param pickaxe The pickaxe ItemStack (can be null).
     * @param level The level to set.
     */
    public static void setPickaxeLevel(@Nullable ItemStack pickaxe, int level) {
        if (isEnchantCorePickaxe(pickaxe)) { // Includes null/air check
            setInt(pickaxe, getPickaxeLevelKey(), Math.max(1, level)); // Ensure level is at least 1
        }
    }

    /**
     * Gets the blocks mined count stored in the pickaxe's PDC.
     * Returns the configured default blocks (from PickaxeConfig's FirstJoinBlocksMined) if the item
     * isn't a valid pickaxe or the blocks key is missing.
     * @param pickaxe The pickaxe ItemStack (can be null).
     * @return The blocks mined count, or the default if unavailable.
     */
    public static long getPickaxeBlocksMined(@Nullable ItemStack pickaxe) {
        PickaxeConfig pConfig = getPlugin().getPickaxeConfig();
        long defaultValue = (pConfig != null) ? pConfig.getFirstJoinBlocksMined() : 0L; // Fallback default

        if (!isEnchantCorePickaxe(pickaxe)) { // Includes null/air check
            return defaultValue;
        }
        return getLong(pickaxe, getPickaxeBlocksMinedKey(), defaultValue);
    }

    /**
     * Sets the blocks mined count in the pickaxe's PDC. Only applies if it's a valid EC pickaxe.
     * @param pickaxe The pickaxe ItemStack (can be null).
     * @param count The block count to set.
     */
    public static void setPickaxeBlocksMined(@Nullable ItemStack pickaxe, long count) {
        if (isEnchantCorePickaxe(pickaxe)) { // Includes null/air check
            setLong(pickaxe, getPickaxeBlocksMinedKey(), Math.max(0L, count)); // Ensure count is non-negative
        }
    }

    /**
     * Removes EnchantCore specific tags (core tag, level, blocks, all enchant levels)
     * from an ItemMeta's PersistentDataContainer.
     * Useful for display items in GUI to prevent them being treated as real pickaxes by mistake.
     * @param meta The ItemMeta to modify (can be null).
     */
    public static void removePickaxeTags(@Nullable ItemMeta meta) {
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Remove core tags
        pdc.remove(getPickaxeTagKey());
        pdc.remove(getPickaxeLevelKey());
        pdc.remove(getPickaxeBlocksMinedKey());

        // Also remove all known enchantment level keys
        EnchantCore pluginInstance = getPlugin(); // Get instance once
        EnchantRegistry registry = pluginInstance.getEnchantRegistry(); // Get registry
        if (registry != null) { // Check if registry is available
            for (EnchantmentWrapper enchant : registry.getAllEnchants()) {
                if (enchant != null) {
                    NamespacedKey key = enchant.getPdcLevelKey();
                    if (key != null) { // Check if key is valid
                        pdc.remove(key); // Remove the specific enchant level key
                    }
                }
            }
        } else {
            getLogger().warning("Cannot remove enchant level tags in removePickaxeTags: EnchantRegistry is null.");
        }
    }

    /**
     * Gets a user-friendly display name for an ItemStack.
     * Uses custom display name if set, otherwise uses the Material name.
     * Strips color codes.
     * @param item The ItemStack (can be null).
     * @return A clean string representation of the item's name, or "Unknown Item" if null/air.
     */
    @NotNull
    public static String getItemName(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "Unknown Item";
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            // Check meta is not null and has display name
            if (meta != null && meta.hasDisplayName()) {
                String displayName = meta.getDisplayName();
                // Ensure display name isn't null/empty before stripping color
                if (displayName != null && !displayName.isEmpty()) {
                    return ChatUtil.stripColor(displayName);
                }
            }
        }
        // Fallback to material name, make it title case
        String materialName = item.getType().toString().toLowerCase().replace("_", " ");
        // Simple title case logic
        StringBuilder titleCase = new StringBuilder();
        boolean nextTitleCase = true;
        for (char c : materialName.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                nextTitleCase = true;
                titleCase.append(c);
            } else if (nextTitleCase) {
                titleCase.append(Character.toTitleCase(c));
                nextTitleCase = false;
            } else {
                titleCase.append(Character.toLowerCase(c));
            }
        }
        return titleCase.toString();
    }
}