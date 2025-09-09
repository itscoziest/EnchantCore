package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.ConfigManager;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.config.SkinConfig;
import com.strikesenchantcore.data.PlayerData;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PickaxeSkinsGUI implements InventoryHolder {

    private final EnchantCore plugin;
    private final Logger logger;
    private final Player player;
    private final PlayerData playerData;
    private final ItemStack pickaxe;
    private final Inventory inventory;

    private final PickaxeManager pickaxeManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final SkinConfig skinConfig;

    private final NamespacedKey SKIN_KEY;

    public PickaxeSkinsGUI(@NotNull EnchantCore plugin, @NotNull Player player, @NotNull PlayerData playerData, @NotNull ItemStack pickaxe) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.player = player;
        this.playerData = playerData;
        this.pickaxe = pickaxe;

        this.pickaxeManager = plugin.getPickaxeManager();
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        this.skinConfig = plugin.getSkinConfig();
        this.SKIN_KEY = new NamespacedKey(plugin, "pickaxe_skin");

        String title = skinConfig.getGuiTitle();
        this.inventory = Bukkit.createInventory(this, skinConfig.getGuiSize(), title);

        populateGUI();
    }

    private void populateGUI() {
        fillBackground();
        addSkinItems();
        addBackButton();
        addPickaxeShowcase();
    }

    private void fillBackground() {
        if (!skinConfig.isFillerEnabled()) return;
        ItemStack filler = createGuiItem(skinConfig.getFillerMaterial(), skinConfig.getFillerName(), null, skinConfig.getFillerModelData(), false);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private void addSkinItems() {
        String currentSkin = getCurrentSkin();
        for (SkinConfig.SkinData skinData : skinConfig.getSkins().values()) {
            addSkinItem(skinData, currentSkin);
        }
    }

    private void addPickaxeShowcase() {
        pickaxeManager.updatePickaxe(this.pickaxe, player);
        ItemStack showcasePickaxe = this.pickaxe.clone();
        ItemMeta meta = showcasePickaxe.getItemMeta();
        if (meta == null) return;
        List<String> currentLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!currentLore.isEmpty() && currentLore.get(currentLore.size() - 1) != null && !currentLore.get(currentLore.size() - 1).trim().isEmpty()) {
            currentLore.add("");
        }
        String currentSkin = getCurrentSkin();
        if (!"none".equals(currentSkin)) {
            SkinConfig.SkinData skinData = skinConfig.getSkin(currentSkin);
            currentLore.add(ColorUtils.translateColors(skinData != null ? "&6Current Skin: " + skinData.getName() : "&6Current Skin: &7Unknown"));
        } else {
            currentLore.add(ColorUtils.translateColors("&6Current Skin: &7Default"));
        }
        meta.setLore(currentLore);
        showcasePickaxe.setItemMeta(meta);
        inventory.setItem(49, showcasePickaxe); // Using fixed slot 49 for showcase
    }

    private void addSkinItem(SkinConfig.SkinData skinData, String currentSkin) {
        List<String> lore = new ArrayList<>(skinData.getLore());
        lore.add("");
        boolean hasPermission = !skinData.hasPermission() || player.hasPermission(skinData.getPermission());
        boolean isCurrentSkin = skinData.getId().equals(currentSkin);

        if (isCurrentSkin) {
            lore.add("&a&l✓ Currently Selected");
        } else if (hasPermission) {
            lore.add("&e&l► Click to Select");
        } else {
            lore.add("&c&l✗ No Permission");
        }

        ItemStack item = skinData.hasItemsAdderID() ? plugin.getItemsAdderUtil().createItemStack(skinData.getMaterial().name(), skinData.getItemsAdderID()) : new ItemStack(skinData.getMaterial());
        if(item == null) item = new ItemStack(skinData.getMaterial());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(skinData.getName());
            meta.setLore(lore.stream().map(ColorUtils::translateColors).collect(Collectors.toList()));
            if (!skinData.hasItemsAdderID() && skinData.getCustomModelData() > 0) {
                meta.setCustomModelData(skinData.getCustomModelData());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_POTION_EFFECTS);
            if (isCurrentSkin) {
                meta.addEnchant(Enchantment.LURE, 1, true);
            }
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

    private void addBackButton() {
        ItemStack backButton = createGuiItem(skinConfig.getBackButtonMaterial(), skinConfig.getBackButtonName(), skinConfig.getBackButtonLore(), skinConfig.getBackButtonModelData(), false);
        inventory.setItem(skinConfig.getBackButtonSlot(), backButton);
    }

    private String getCurrentSkin() {
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) return "none";
        String skinValue = PDCUtil.getString(pickaxe, SKIN_KEY);
        return skinValue != null ? skinValue : "none";
    }

    private boolean applySkin(String skinId, SkinConfig.SkinData skinData) {
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) return false;
        int currentLevel = PDCUtil.getPickaxeLevel(pickaxe);
        long currentBlocks = PDCUtil.getPickaxeBlocksMined(pickaxe);
        java.util.Map<String, Integer> currentEnchants = pickaxeManager.getAllEnchantLevels(pickaxe);

        ItemStack newSkinItem = skinData.hasItemsAdderID() ? plugin.getItemsAdderUtil().createItemStack(skinData.getMaterial().name(), skinData.getItemsAdderID()) : new ItemStack(skinData.getMaterial());
        if (newSkinItem == null) return false;

        if (!skinData.hasItemsAdderID() && skinData.getCustomModelData() > 0) {
            ItemMeta newMeta = newSkinItem.getItemMeta();
            if (newMeta != null) {
                newMeta.setCustomModelData(skinData.getCustomModelData());
                newSkinItem.setItemMeta(newMeta);
            }
        }

        pickaxe.setType(newSkinItem.getType());
        pickaxe.setItemMeta(newSkinItem.getItemMeta());
        PDCUtil.tagAsEnchantCorePickaxe(pickaxe);
        PDCUtil.setPickaxeLevel(pickaxe, currentLevel);
        PDCUtil.setPickaxeBlocksMined(pickaxe, currentBlocks);
        currentEnchants.forEach((enchantKey, enchantLevel) -> pickaxeManager.setEnchantLevel(pickaxe, enchantKey, enchantLevel));
        PDCUtil.setString(pickaxe, SKIN_KEY, skinId);
        pickaxeManager.updatePickaxe(pickaxe, player);
        return true;
    }

    private boolean removeSkin() {
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) return false;
        int currentLevel = PDCUtil.getPickaxeLevel(pickaxe);
        long currentBlocks = PDCUtil.getPickaxeBlocksMined(pickaxe);
        java.util.Map<String, Integer> currentEnchants = pickaxeManager.getAllEnchantLevels(pickaxe);
        String defaultMaterial = plugin.getPickaxeConfig().getPickaxeMaterial().name();
        String defaultItemsAdderID = plugin.getPickaxeConfig().getPickaxeItemsAdderID();
        ItemStack defaultPickaxe = plugin.getItemsAdderUtil().createItemStack(defaultMaterial, defaultItemsAdderID);
        if (defaultPickaxe == null) return false;
        pickaxe.setType(defaultPickaxe.getType());
        pickaxe.setItemMeta(defaultPickaxe.getItemMeta());
        PDCUtil.tagAsEnchantCorePickaxe(pickaxe);
        PDCUtil.setPickaxeLevel(pickaxe, currentLevel);
        PDCUtil.setPickaxeBlocksMined(pickaxe, currentBlocks);
        currentEnchants.forEach((enchantKey, enchantLevel) -> pickaxeManager.setEnchantLevel(pickaxe, enchantKey, enchantLevel));
        ItemMeta finalMeta = pickaxe.getItemMeta();
        if (finalMeta != null) {
            finalMeta.getPersistentDataContainer().remove(SKIN_KEY);
            pickaxe.setItemMeta(finalMeta);
        }
        pickaxeManager.updatePickaxe(pickaxe, player);
        return true;
    }

    public void handleClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (slot == skinConfig.getBackButtonSlot()) {
            playSoundEffect(player, skinConfig.getSound("back-button"));
            new EnchantGUI(plugin, player, playerData, pickaxe).open();
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;
        NamespacedKey skinIdKey = new NamespacedKey(plugin, "skin_id");
        if (!meta.getPersistentDataContainer().has(skinIdKey, PersistentDataType.STRING)) return;

        String skinId = meta.getPersistentDataContainer().get(skinIdKey, PersistentDataType.STRING);
        if (skinId == null) return;

        if ("remove_skin".equals(skinId)) {
            // Logic for removing skin can be added here if needed, or handled by a default skin item
        }

        if (skinId.equals(getCurrentSkin())) {
            ChatUtil.sendMessage(player, skinConfig.getMessage("skin-already-selected"));
            playSoundEffect(player, skinConfig.getSound("skin-already-selected"));
            return;
        }

        NamespacedKey permissionKey = new NamespacedKey(plugin, "skin_permission");
        if (meta.getPersistentDataContainer().has(permissionKey, PersistentDataType.STRING)) {
            String permission = meta.getPersistentDataContainer().get(permissionKey, PersistentDataType.STRING);
            if (permission != null && !player.hasPermission(permission)) {
                ChatUtil.sendMessage(player, skinConfig.getMessage("no-permission"));
                playSoundEffect(player, skinConfig.getSound("no-permission"));
                return;
            }
        }

        SkinConfig.SkinData skinData = skinConfig.getSkin(skinId);
        if (skinData == null) {
            ChatUtil.sendMessage(player, "&cError: Skin data not found!");
            return;
        }

        if (applySkin(skinId, skinData)) {
            ChatUtil.sendMessage(player, skinConfig.getMessage("skin-applied").replace("%skin_name%", skinData.getName()));
            playSoundEffect(player, skinConfig.getSound("skin-applied"));
            populateGUI();
        } else {
            ChatUtil.sendMessage(player, skinConfig.getMessage("error-applying"));
            playSoundEffect(player, skinConfig.getSound("no-permission"));
        }
    }

    public void open() { player.openInventory(inventory); }
    @NotNull @Override public Inventory getInventory() { return inventory; }

    // THE ONLY ADDITION: This method is required for the new listener system.
    public ItemStack getPickaxe() {
        return this.pickaxe;
    }

    private ItemStack createGuiItem(Material material, String name, @Nullable List<String> lore, int modelData, boolean enchanted) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.translateColors(name));
            if (lore != null) meta.setLore(lore.stream().map(ColorUtils::translateColors).collect(Collectors.toList()));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_POTION_EFFECTS);
            if (modelData > 0) meta.setCustomModelData(modelData);
            if (enchanted) meta.addEnchant(Enchantment.LURE, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void playSoundEffect(Player player, @Nullable SkinConfig.SoundData sound) {
        if (sound != null) {
            playSoundEffect(player, sound.getSound(), sound.getVolume(), sound.getPitch());
        }
    }

    private void playSoundEffect(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline() && sound != null) {
            player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
        }
    }
}