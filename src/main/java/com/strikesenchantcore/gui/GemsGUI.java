package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.ConfigManager;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.enchants.EnchantRegistry;
import com.strikesenchantcore.pickaxe.PickaxeManager;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.ColorUtils;
import com.strikesenchantcore.util.PDCUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GemsGUI implements InventoryHolder {

    private final EnchantCore plugin;
    private final Player player;
    private final PlayerData playerData;
    private final ItemStack pickaxe;
    private final Inventory inventory;
    private final Logger logger;
    private final EnchantRegistry enchantRegistry;
    private final PickaxeManager pickaxeManager;
    private final MessageManager messageManager;
    private static final int[] TOKEN_MENU_SLOTS = {0, 1, 2};
    private static final int[] GEMS_MENU_SLOTS = {3, 4, 5};
    private static final int[] REBIRTH_MENU_SLOTS = {6, 7, 8};
    private static final int[] ADDITIONAL_MENU_SLOTS = {45, 47, 51, 53};

    public GemsGUI(@NotNull EnchantCore plugin, @NotNull Player player, @NotNull PlayerData playerData, @NotNull ItemStack pickaxe) {
        this.plugin = plugin;
        this.player = player;
        this.playerData = playerData;
        this.pickaxe = pickaxe;
        this.logger = plugin.getLogger();
        this.enchantRegistry = plugin.getEnchantRegistry();
        this.pickaxeManager = plugin.getPickaxeManager();
        this.messageManager = plugin.getMessageManager();
        this.inventory = Bukkit.createInventory(this, 54, ChatUtil.color("&f\uE5A1\uE090"));
        populateGUI();
    }

    private void populateGUI() {
        addMenuButtons();
        Map<String, Integer> currentEnchants = pickaxeManager.getAllEnchantLevels(this.pickaxe);
        int pickaxeLevel = PDCUtil.getPickaxeLevel(this.pickaxe);
        int currentSlot = 10;
        for (EnchantmentWrapper enchant : enchantRegistry.getAllEnchants()) {
            if (!enchant.isEnabled() || enchant.getCurrencyType() != ConfigManager.CurrencyType.GEMS) continue;
            if (currentSlot >= 45) break;
            int currentLevel = currentEnchants.getOrDefault(enchant.getRawName().toLowerCase(), 0);
            inventory.setItem(currentSlot++, enchant.createGuiItem(currentLevel, pickaxeLevel, ConfigManager.CurrencyType.GEMS, null));
        }
        addInfoItem();
    }

    private void addMenuButtons() {
        for (int slot : TOKEN_MENU_SLOTS) {
            inventory.setItem(slot, createGuiItemHelper(Material.BARRIER, "&c&lToken Enchants", List.of("&7Click to view main enchants", "&c&lUNDER DEVELOPMENT"), 0, false));
        }
        for (int slot : GEMS_MENU_SLOTS) {
            inventory.setItem(slot, createGuiItemHelper(Material.BARRIER, "&c&lGems Enchants", List.of("&7Click to view gems enchants", "&c&lUNDER DEVELOPMENT"), 0, false));
        }
        for (int slot : REBIRTH_MENU_SLOTS) {
            inventory.setItem(slot, createGuiItemHelper(Material.BARRIER, "&d&lRebirth Enchants", List.of("&7Click to view rebirth enchants", "&c&lUNDER DEVELOPMENT"), 0, false));
        }
        if (inventory.getSize() > 45) inventory.setItem(45, createGuiItemHelper(Material.BARRIER, "&6&lPickaxe Skins", List.of("&7Click to view pickaxe skins", "&c&lUNDER DEVELOPMENT"), 0, false));
        if (inventory.getSize() > 47) inventory.setItem(47, createGuiItemHelper(Material.BARRIER, "&6&lMortar", List.of("&7Click to view mortar options", "&c&lUNDER DEVELOPMENT"), 0, false));
        if (inventory.getSize() > 51) inventory.setItem(51, createGuiItemHelper(Material.BARRIER, "&6&lCrystals", List.of("&7Click to view crystals", "&c&lUNDER DEVELOPMENT"), 0, false));
        if (inventory.getSize() > 53) inventory.setItem(53, createGuiItemHelper(Material.BARRIER, "&6&lAttachments", List.of("&7Click to view attachments", "&c&lUNDER DEVELOPMENT"), 0, false));
    }

    private void addInfoItem() {
        pickaxeManager.updatePickaxe(this.pickaxe, player);
        ItemStack infoPickaxeClone = this.pickaxe.clone();
        ItemMeta meta = infoPickaxeClone.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        String balanceLine = messageManager.getMessage("currency.gems_balance_format", "&dBalance: &f%balance% Gems").replace("%balance%", NumberFormat.getNumberInstance(Locale.US).format(playerData.getGems()));
        lore.add(ChatUtil.color(balanceLine));
        meta.setLore(lore);
        infoPickaxeClone.setItemMeta(meta);
        inventory.setItem(49, infoPickaxeClone);
    }

    private EnchantmentWrapper findEnchantForSlot(int slot) {
        int currentSlot = 10;
        for (EnchantmentWrapper enchant : enchantRegistry.getAllEnchants()) {
            if (!enchant.isEnabled() || enchant.getCurrencyType() != ConfigManager.CurrencyType.GEMS) continue;
            if (currentSlot == slot) {
                return enchant;
            }
            currentSlot++;
        }
        return null;
    }

    public void handleClick(Player player, int slot, ClickType clickType) {
        if (handleMenuButtonClick(player, slot)) return;
        EnchantmentWrapper clickedEnchant = findEnchantForSlot(slot);
        if (clickedEnchant == null) return;
        int levelsToAdd = switch (clickType) {
            case LEFT -> 1;
            case RIGHT -> 10;
            case SHIFT_RIGHT -> 50;
            default -> 0;
        };
        if (levelsToAdd == 0) return;
        int currentLevel = pickaxeManager.getEnchantLevel(pickaxe, clickedEnchant.getRawName());
        int maxLevel = clickedEnchant.getMaxLevel();
        if (maxLevel > 0 && currentLevel >= maxLevel) {
            ChatUtil.sendMessage(player, "&cThis enchant is already max level!");
            playSoundEffect(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        int targetLevel = Math.min(currentLevel + levelsToAdd, maxLevel > 0 ? maxLevel : Integer.MAX_VALUE);
        int actualLevelsToAdd = targetLevel - currentLevel;
        if (actualLevelsToAdd <= 0) return;
        double totalCost = 0;
        for (int i = 1; i <= actualLevelsToAdd; i++) {
            totalCost += clickedEnchant.getCostForLevel(currentLevel + i);
        }
        if (!playerData.hasEnoughGems(totalCost)) {
            ChatUtil.sendMessage(player, "&cNot enough Gems! Cost: &d" + NumberFormat.getNumberInstance(Locale.US).format(totalCost));
            playSoundEffect(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        playerData.removeGems(totalCost);
        pickaxeManager.setEnchantLevel(pickaxe, clickedEnchant.getRawName(), targetLevel);
        pickaxeManager.updatePickaxe(pickaxe, player);
        inventory.setItem(slot, clickedEnchant.createGuiItem(targetLevel, PDCUtil.getPickaxeLevel(pickaxe), ConfigManager.CurrencyType.GEMS, null));
        addInfoItem();
        ChatUtil.sendMessage(player, "&aUpgraded " + clickedEnchant.getDisplayName() + " to level " + targetLevel + "!");
        playSoundEffect(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    private boolean handleMenuButtonClick(Player player, int slot) {
        // Navigates back to the Token/Main menu
        for (int tokenSlot : TOKEN_MENU_SLOTS) {
            if (slot == tokenSlot) {
                playSoundEffect(player, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new EnchantGUI(plugin, player, playerData, this.pickaxe).open();
                return true;
            }
        }
        // Navigates to the Rebirth menu
        for (int rebirthSlot : REBIRTH_MENU_SLOTS) {
            if (slot == rebirthSlot) {
                playSoundEffect(player, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new RebirthGUI(plugin, player, playerData, this.pickaxe).open();
                return true;
            }
        }

        // Handles all other buttons (Gems, Skins, etc.)
        if ((slot >= 3 && slot <= 5) || isAdditionalMenuSlot(slot)) {
            playSoundEffect(player, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            if (slot == 45) { // Pickaxe Skins logic
                try {
                    // Assuming you have a PickaxeSkinsGUIListener with this static method
                    PickaxeSkinsGUIListener.setPickaxeForSkinsGui(player.getUniqueId(), this.pickaxe);
                    new PickaxeSkinsGUI(plugin, player, playerData, this.pickaxe).open();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error opening PickaxeSkinsGUI from GemsGUI", e);
                    player.closeInventory();
                }
            } else {
                if (slot == 51) { // Crystals button
                    new CrystalsGUI(plugin, player).open();
                } else {
                    ChatUtil.sendMessage(player, "&c&lUNDER DEVELOPMENT");
                }
            }
            return true;
        }
        return false;
    }

    // You may also need to add this small helper method to GemsGUI.java
    private boolean isAdditionalMenuSlot(int slot) {
        for (int menuSlot : ADDITIONAL_MENU_SLOTS) {
            if (slot == menuSlot) return true;
        }
        return false;
    }

    public void open() { player.openInventory(inventory); }
    @NotNull @Override public Inventory getInventory() { return inventory; }
    public ItemStack getPickaxe() { return this.pickaxe; }
    private ItemStack createGuiItemHelper(Material m, String name, @Nullable List<String> lore, int model, boolean enchantedGlow) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.translateColors(name));
            if (lore != null) meta.setLore(lore.stream().map(ColorUtils::translateColors).collect(Collectors.toList()));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
            if (model > 0) meta.setCustomModelData(model);
            if (enchantedGlow) meta.addEnchant(Enchantment.LURE, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }
    private void playSoundEffect(Player player, Sound sound, float volume, float pitch) { player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch); }
}