package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.ConfigManager;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.config.SkinConfig;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.pickaxe.PickaxeManager;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.ColorUtils;
import com.strikesenchantcore.util.PDCUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PickaxeSkinsGUI implements InventoryHolder {

    private final EnchantCore plugin;
    private final Logger logger;
    private final Player player;
    private final PlayerData playerData;
    private final ItemStack pickaxe;
    private final Inventory inventory;

    // Cached managers
    private final PickaxeManager pickaxeManager;
    private final PlayerDataManager playerDataManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final SkinConfig skinConfig;

    // PDC Key for storing current skin
    private final NamespacedKey SKIN_KEY;

    public PickaxeSkinsGUI(@NotNull EnchantCore plugin, @NotNull Player player, @NotNull PlayerData playerData, @NotNull ItemStack pickaxe) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.player = player;
        this.playerData = playerData;
        this.pickaxe = pickaxe;

        // Initialize cached managers
        this.pickaxeManager = plugin.getPickaxeManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        this.skinConfig = plugin.getSkinConfig();

        // Create PDC key for skin storage
        this.SKIN_KEY = new NamespacedKey(plugin, "pickaxe_skin");

        // Validate managers
        if (pickaxeManager == null || playerDataManager == null || configManager == null ||
                messageManager == null || skinConfig == null) {
            logger.severe("One or more managers are null during PickaxeSkinsGUI creation!");
            throw new IllegalStateException("Cannot create PickaxeSkinsGUI: required managers are null");
        }

        // Create inventory
        String title = skinConfig.getGuiTitle();
        this.inventory = Bukkit.createInventory(this, skinConfig.getGuiSize(), title);

        populateGUI();
    }

    /**
     * Populates the GUI with skin items and back button
     */
    private void populateGUI() {
        // Fill background
        fillBackground();

        // Add skin items from config
        addSkinItems();

        // Add back button
        addBackButton();

        addPickaxeShowcase();
    }

    /**
     * Fills empty slots with filler items
     */
    private void fillBackground() {
        if (!skinConfig.isFillerEnabled()) return;

        ItemStack filler = createGuiItem(
                skinConfig.getFillerMaterial(),
                skinConfig.getFillerName(),
                null,
                skinConfig.getFillerModelData(),
                false
        );

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null && i != skinConfig.getBackButtonSlot()) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Adds skin items from the skins.yml configuration
     */
    private void addSkinItems() {
        String currentSkin = getCurrentSkin();

        // Add all configured skins
        for (SkinConfig.SkinData skinData : skinConfig.getSkins().values()) {
            addSkinItem(skinData, currentSkin);
        }
    }

    /**
     * Adds a "Remove Skin" button to restore default pickaxe appearance
     */
    private void addRemoveSkinButton(String currentSkin) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Restore your pickaxe to its");
        lore.add("&7default appearance from config");
        lore.add("");

        boolean isDefault = "none".equals(currentSkin);
        if (isDefault) {
            lore.add("&a&l✓ Default Appearance");
        } else {
            lore.add("&e&l► Click to Remove Skin");
        }

        ItemStack item = createGuiItem(Material.BARRIER, "&c&lRemove Skin", lore, 0, isDefault);

        // Store special identifier for remove skin
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey skinIdKey = new NamespacedKey(plugin, "skin_id");
            meta.getPersistentDataContainer().set(skinIdKey, PersistentDataType.STRING, "remove_skin");
            item.setItemMeta(meta);
        }

        // Place at slot 49 (center bottom, like your enchant GUI info item)
        inventory.setItem(49, item);
    }





    /**
     * Adds the pickaxe showcase item (like in the main enchant GUI)
     */
    private void addPickaxeShowcase() {
        try {
            pickaxeManager.updatePickaxe(this.pickaxe, player);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating pickaxe meta before creating showcase item for " + player.getName(), e);
            return;
        }

        ItemStack showcasePickaxe = this.pickaxe.clone();
        ItemMeta meta = showcasePickaxe.getItemMeta();
        if (meta == null) {
            logger.warning("Failed to get ItemMeta from cloned pickaxe for showcase item (Player: " + player.getName() + ")");
            return;
        }

        List<String> currentLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!currentLore.isEmpty() && currentLore.get(currentLore.size()-1) != null && !currentLore.get(currentLore.size()-1).trim().isEmpty()) {
            currentLore.add("");
        }

        // Add current skin info
        String currentSkin = getCurrentSkin();
        if (!"none".equals(currentSkin)) {
            SkinConfig.SkinData skinData = skinConfig.getSkin(currentSkin);
            if (skinData != null) {
                currentLore.add(ColorUtils.translateColors("&6Current Skin: " + skinData.getName()));
            } else {
                currentLore.add(ColorUtils.translateColors("&6Current Skin: &7Unknown"));
            }
        } else {
            currentLore.add(ColorUtils.translateColors("&6Current Skin: &7Default"));
        }

        meta.setLore(currentLore);

        if (!showcasePickaxe.setItemMeta(meta)) {
            logger.warning("Failed to set ItemMeta for showcase item clone (Player: " + player.getName() + ")");
        }

        // Place in center bottom area (slot 49)
        inventory.setItem(49, showcasePickaxe);
    }








    /**
     * Adds a single skin item to the GUI from SkinData
     */
    private void addSkinItem(SkinConfig.SkinData skinData, String currentSkin) {
        List<String> lore = new ArrayList<>(skinData.getLore());

        lore.add(""); // Empty line

        // Check if player has permission
        boolean hasPermission = !skinData.hasPermission() || player.hasPermission(skinData.getPermission());
        boolean isCurrentSkin = skinData.getId().equals(currentSkin);

        if (isCurrentSkin) {
            lore.add("&a&l✓ Currently Selected");
        } else if (hasPermission) {
            lore.add("&e&l► Click to Select");
        } else {
            lore.add("&c&l✗ No Permission");
        }

        // Create the display item (use ItemsAdder if available)
        ItemStack item;
        if (skinData.hasItemsAdderID()) {
            item = plugin.getItemsAdderUtil().createItemStack(
                    skinData.getMaterial().name(),
                    skinData.getItemsAdderID()
            );
            if (item == null) {
                // Fallback to vanilla material
                item = new ItemStack(skinData.getMaterial());
            }
        } else {
            item = new ItemStack(skinData.getMaterial());
        }

        // Apply custom properties to the display item
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(skinData.getName());

            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ColorUtils.translateColors(line));
            }
            meta.setLore(coloredLore);

            // Apply custom model data if not using ItemsAdder
            if (!skinData.hasItemsAdderID() && skinData.getCustomModelData() > 0) {
                meta.setCustomModelData(skinData.getCustomModelData());
            }

            // Add flags to hide attributes and other stuff
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_POTION_EFFECTS);

            // Add fake enchantment for glow effect if currently selected
            if (isCurrentSkin) {
                meta.addEnchant(Enchantment.LURE, 1, true);
            }

            // Store skin ID for click handling
            NamespacedKey skinIdKey = new NamespacedKey(plugin, "skin_id");
            meta.getPersistentDataContainer().set(skinIdKey, PersistentDataType.STRING, skinData.getId());

            if (skinData.hasPermission()) {
                NamespacedKey permissionKey = new NamespacedKey(plugin, "skin_permission");
                meta.getPersistentDataContainer().set(permissionKey, PersistentDataType.STRING, skinData.getPermission());
            }

            item.setItemMeta(meta);
        }

        inventory.setItem(skinData.getSlot(), item);
    }

    /**
     * Adds the back button to return to the enchant GUI
     */
    private void addBackButton() {
        ItemStack backButton = createGuiItem(
                skinConfig.getBackButtonMaterial(),
                skinConfig.getBackButtonName(),
                skinConfig.getBackButtonLore(),
                skinConfig.getBackButtonModelData(),
                false
        );
        inventory.setItem(skinConfig.getBackButtonSlot(), backButton);
    }

    /**
     * Gets the current skin applied to the pickaxe
     */
    private String getCurrentSkin() {
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) {
            return "none";
        }

        // Use PDCUtil.getString which should work with your version
        String skinValue = PDCUtil.getString(pickaxe, SKIN_KEY);
        return skinValue != null ? skinValue : "none";
    }

    /**
     * Applies a skin to the pickaxe
     */
    private boolean applySkin(String skinId, SkinConfig.SkinData skinData) {
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) {
            return false;
        }

        // Get current pickaxe data BEFORE making changes
        int currentLevel = PDCUtil.getPickaxeLevel(pickaxe);
        long currentBlocks = PDCUtil.getPickaxeBlocksMined(pickaxe);
        java.util.Map<String, Integer> currentEnchants = pickaxeManager.getAllEnchantLevels(pickaxe);

        // Create the new skin appearance using ItemsAdder
        ItemStack newSkinItem;
        if (skinData.hasItemsAdderID()) {
            newSkinItem = plugin.getItemsAdderUtil().createItemStack(
                    skinData.getMaterial().name(),
                    skinData.getItemsAdderID()
            );
            logger.info("DEBUG: Created skin item with ItemsAdder ID: " + skinData.getItemsAdderID() + ", Result: " + (newSkinItem != null ? newSkinItem.getType() : "null"));
        } else {
            newSkinItem = new ItemStack(skinData.getMaterial());
            if (skinData.getCustomModelData() > 0) {
                ItemMeta newMeta = newSkinItem.getItemMeta();
                if (newMeta != null) {
                    newMeta.setCustomModelData(skinData.getCustomModelData());
                    newSkinItem.setItemMeta(newMeta);
                }
            }
            logger.info("DEBUG: Created vanilla skin item with custom model data: " + skinData.getCustomModelData());
        }

        if (newSkinItem == null) {
            logger.warning("Failed to create new skin item for: " + skinId);
            return false;
        }

        // Log the ItemMeta we're about to apply
        ItemMeta newItemMeta = newSkinItem.getItemMeta();
        if (newItemMeta != null && newItemMeta.hasCustomModelData()) {
            logger.info("DEBUG: New skin item has custom model data: " + newItemMeta.getCustomModelData());
        } else {
            logger.info("DEBUG: New skin item has NO custom model data");
        }

        // Store the current enchantcore data, then completely replace the item
        pickaxe.setType(newSkinItem.getType());
        pickaxe.setItemMeta(newSkinItem.getItemMeta()); // This should transfer ItemsAdder data

        // Restore ALL the EnchantCore data
        PDCUtil.tagAsEnchantCorePickaxe(pickaxe);
        PDCUtil.setPickaxeLevel(pickaxe, currentLevel);
        PDCUtil.setPickaxeBlocksMined(pickaxe, currentBlocks);

        // Restore all enchantments
        for (String enchantKey : currentEnchants.keySet()) {
            Integer enchantLevel = currentEnchants.get(enchantKey);
            if (enchantLevel != null && enchantLevel > 0) {
                pickaxeManager.setEnchantLevel(pickaxe, enchantKey, enchantLevel);
            }
        }

        // Set the skin ID using PDCUtil
        PDCUtil.setString(pickaxe, SKIN_KEY, skinId);

        // Update the pickaxe display (name, lore, etc.) but preserve the custom model data
        pickaxeManager.updatePickaxe(pickaxe, player);

        // Verify the custom model data is still there after update
        ItemMeta finalMeta = pickaxe.getItemMeta();
        if (finalMeta != null && finalMeta.hasCustomModelData()) {
            logger.info("DEBUG: Final pickaxe has custom model data: " + finalMeta.getCustomModelData());
        } else {
            logger.info("DEBUG: WARNING - Final pickaxe lost custom model data!");
        }

        return true;
    }

    /**
     * Removes the current skin and restores default pickaxe appearance from pickaxe.yml
     */
    private boolean removeSkin() {
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) {
            return false;
        }

        // Get current data
        int currentLevel = PDCUtil.getPickaxeLevel(pickaxe);
        long currentBlocks = PDCUtil.getPickaxeBlocksMined(pickaxe);
        java.util.Map<String, Integer> currentEnchants = pickaxeManager.getAllEnchantLevels(pickaxe);

        // Create default pickaxe from config
        String defaultMaterial = plugin.getPickaxeConfig().getPickaxeMaterial().name();
        String defaultItemsAdderID = plugin.getPickaxeConfig().getPickaxeItemsAdderID();

        ItemStack defaultPickaxe = plugin.getItemsAdderUtil().createItemStack(defaultMaterial, defaultItemsAdderID);
        if (defaultPickaxe == null) {
            return false;
        }

        // Replace with default appearance
        pickaxe.setType(defaultPickaxe.getType());
        pickaxe.setItemMeta(defaultPickaxe.getItemMeta());

        // Restore all data
        PDCUtil.tagAsEnchantCorePickaxe(pickaxe);
        PDCUtil.setPickaxeLevel(pickaxe, currentLevel);
        PDCUtil.setPickaxeBlocksMined(pickaxe, currentBlocks);

        // Restore enchantments
        for (String enchantKey : currentEnchants.keySet()) {
            Integer enchantLevel = currentEnchants.get(enchantKey);
            if (enchantLevel != null && enchantLevel > 0) {
                pickaxeManager.setEnchantLevel(pickaxe, enchantKey, enchantLevel);
            }
        }

        // REMOVE the skin key completely
        ItemMeta finalMeta = pickaxe.getItemMeta();
        if (finalMeta != null) {
            finalMeta.getPersistentDataContainer().remove(SKIN_KEY);
            pickaxe.setItemMeta(finalMeta);
        }

        // Update display
        pickaxeManager.updatePickaxe(pickaxe, player);

        return true;
    }

    /**
     * Transfers all EnchantCore data from source pickaxe to target pickaxe
     */
    private boolean transferPickaxeData(ItemStack source, ItemStack target) {
        // Use PDCUtil methods which work with your version
        int level = PDCUtil.getPickaxeLevel(source);
        long blocks = PDCUtil.getPickaxeBlocksMined(source);

        // Apply to target
        PDCUtil.setPickaxeLevel(target, level);
        PDCUtil.setPickaxeBlocksMined(target, blocks);
        PDCUtil.tagAsEnchantCorePickaxe(target);

        // Copy enchantment levels using PickaxeManager
        if (pickaxeManager != null) {
            java.util.Map<String, Integer> enchantLevels = pickaxeManager.getAllEnchantLevels(source);
            for (String enchantKey : enchantLevels.keySet()) {
                Integer enchantLevel = enchantLevels.get(enchantKey);
                if (enchantLevel != null && enchantLevel > 0) {
                    pickaxeManager.setEnchantLevel(target, enchantKey, enchantLevel);
                }
            }
        }

        // Store skin ID manually
        ItemMeta targetMeta = target.getItemMeta();
        if (targetMeta != null) {
            targetMeta.getPersistentDataContainer().set(SKIN_KEY, PersistentDataType.STRING, getCurrentSkin());
            target.setItemMeta(targetMeta);
        }

        return true;
    }

    /**
     * Handles clicks in the GUI
     */
    public void handleClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Handle back button
        if (slot == skinConfig.getBackButtonSlot()) {
            SkinConfig.SoundData sound = skinConfig.getSound("back-button");
            if (sound != null) {
                playSoundEffect(player, sound.getSound(), sound.getVolume(), sound.getPitch());
            }

            // Open the enchant GUI
            EnchantGUI enchantGUI = new EnchantGUI(plugin, player, playerData, pickaxe);
            EnchantGUIListener.setPickaxeForGui(player.getUniqueId(), pickaxe);
            enchantGUI.open();
            return;
        }

        // Handle skin selection
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        NamespacedKey skinIdKey = new NamespacedKey(plugin, "skin_id");
        if (!meta.getPersistentDataContainer().has(skinIdKey, PersistentDataType.STRING)) {
            return; // Not a skin item
        }

        String skinId = meta.getPersistentDataContainer().get(skinIdKey, PersistentDataType.STRING);
        if (skinId == null) return;

        // Handle "Remove Skin" option
        if ("remove_skin".equals(skinId)) {
        }

        // Check if already selected
        if (skinId.equals(getCurrentSkin())) {
            String message = skinConfig.getMessage("skin-already-selected");
            ChatUtil.sendMessage(player, message);

            SkinConfig.SoundData sound = skinConfig.getSound("skin-already-selected");
            if (sound != null) {
                playSoundEffect(player, sound.getSound(), sound.getVolume(), sound.getPitch());
            }
            return;
        }

        // Check permission
        NamespacedKey permissionKey = new NamespacedKey(plugin, "skin_permission");
        if (meta.getPersistentDataContainer().has(permissionKey, PersistentDataType.STRING)) {
            String permission = meta.getPersistentDataContainer().get(permissionKey, PersistentDataType.STRING);
            if (permission != null && !player.hasPermission(permission)) {
                String message = skinConfig.getMessage("no-permission");
                ChatUtil.sendMessage(player, message);

                SkinConfig.SoundData sound = skinConfig.getSound("no-permission");
                if (sound != null) {
                    playSoundEffect(player, sound.getSound(), sound.getVolume(), sound.getPitch());
                }
                return;
            }
        }

        // Get skin data from config
        SkinConfig.SkinData skinData = skinConfig.getSkin(skinId);
        if (skinData == null) {
            ChatUtil.sendMessage(player, "&cError: Skin data not found!");
            return;
        }

        // Apply the skin
        if (applySkin(skinId, skinData)) {
            String message = skinConfig.getMessage("skin-applied").replace("%skin_name%", skinData.getName());
            ChatUtil.sendMessage(player, message);

            SkinConfig.SoundData sound = skinConfig.getSound("skin-applied");
            if (sound != null) {
                playSoundEffect(player, sound.getSound(), sound.getVolume(), sound.getPitch());
            }

            // Refresh the GUI
            populateGUI();
        } else {
            String message = skinConfig.getMessage("error-applying");
            ChatUtil.sendMessage(player, message);

            SkinConfig.SoundData sound = skinConfig.getSound("no-permission"); // Reuse error sound
            if (sound != null) {
                playSoundEffect(player, sound.getSound(), sound.getVolume(), sound.getPitch());
            }
        }
    }

    /**
     * Opens the GUI for the player
     */
    public void open() {
        if (inventory == null) {
            logger.severe("Attempted to open PickaxeSkinsGUI but inventory was null!");
            ChatUtil.sendMessage(player, "&cCould not open pickaxe skins menu due to an internal error.");
            return;
        }
        player.openInventory(inventory);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory != null ? inventory : Bukkit.createInventory(this, 9, "&cError");
    }

    /**
     * Helper method to create GUI items
     */
    private ItemStack createGuiItem(Material material, String name, @Nullable List<String> lore, int modelData, boolean enchanted) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ColorUtils.translateColors(name));

            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ColorUtils.translateColors(line));
                }
                meta.setLore(coloredLore);
            }

            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }

            // Add flags to hide attributes and other stuff
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_POTION_EFFECTS);

            // Add fake enchantment for glow effect
            if (enchanted) {
                meta.addEnchant(Enchantment.LURE, 1, true);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Helper method to play sounds
     */
    private void playSoundEffect(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline() && sound != null) {
            try {
                player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
            } catch (Exception e) {
                if (configManager.isDebugMode()) {
                    logger.log(Level.WARNING, "Error playing sound " + sound.name() + " for " + player.getName(), e);
                }
            }
        }
    }
}