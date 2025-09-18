package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.managers.MortarManager;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.ColorUtils;
import com.strikesenchantcore.util.PDCUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MortarGUI implements InventoryHolder {

    private final EnchantCore plugin;
    private final Player player;
    private final Inventory inventory;
    private final MortarManager mortarManager;

    // GUI Layout
    private static final int[] FILLER_SLOTS = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52, 53};
    private static final int MORTAR_INFO_SLOT = 4;
    private static final int UPGRADE_MORTAR_SLOT = 22;
    private static final int STATS_SLOT = 49;

    // Upgrade slots
    private static final int MULTIPLIER_SLOT = 10;
    private static final int COOLDOWN_CONDENSER_SLOT = 12;
    private static final int LIGHTNING_STRIKE_SLOT = 14;
    private static final int SELECTIVE_FIRE_SLOT = 16;

    public MortarGUI(EnchantCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.mortarManager = plugin.getMortarManager();
        this.inventory = Bukkit.createInventory(this, 54, ColorUtils.translateColors("&6&lMortar Workshop"));

        populateGUI();
    }

    private void populateGUI() {
        // Fill background
        fillBackground();

        // Add main items
        addMortarInfoItem();
        addUpgradeMortarItem();
        addStatsItem();

        // Add upgrade items
        addUpgradeItems();
    }

    private void fillBackground() {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, "&7", null, false);
        for (int slot : FILLER_SLOTS) {
            inventory.setItem(slot, filler);
        }
    }

    private void addMortarInfoItem() {
        UUID playerId = player.getUniqueId();
        MortarManager.MortarData mortarData = mortarManager.getMortarData(playerId);

        List<String> lore = new ArrayList<>();

        if (mortarData.getLevel() == 0) {
            lore.add("&7The Mortar is a powerful upgrade that");
            lore.add("&7randomly triggers 3 enchants and gives");
            lore.add("&7a boost to token/gem gain!");
            lore.add("");
            lore.add("&c&lNOT UNLOCKED");
            lore.add("&7Use the upgrade button to unlock!");
        } else {
            lore.add("&7The Mortar randomly activates and");
            lore.add("&7triggers 3 of your enchants while");
            lore.add("&7providing a temporary boost!");
            lore.add("");
            lore.add("&eLevel: &6" + mortarData.getLevel() + "&7/&610");

            // Cooldown status
            if (mortarManager.isOnCooldown(playerId)) {
                long remaining = mortarManager.getCooldownRemaining(playerId);
                lore.add("&eCooldown: &c" + formatTime(remaining));
            } else {
                lore.add("&eCooldown: &aReady!");
            }

            // Active boost status
            if (mortarManager.hasActiveBoost(playerId)) {
                double multiplier = mortarManager.getActiveBoostMultiplier(playerId);
                long boostRemaining = mortarData.getBoostEndTime() - System.currentTimeMillis();
                lore.add("&eActive Boost: &6" + String.format("%.1f", multiplier) + "x");
                lore.add("&eTime Remaining: &6" + formatTime(boostRemaining));
            } else {
                lore.add("&eActive Boost: &7None");
            }

            // Last activation
            if (mortarData.getLastActivation() > 0) {
                long timeSince = System.currentTimeMillis() - mortarData.getLastActivation();
                lore.add("&eLast Activation: &6" + formatTime(timeSince) + " ago");
            } else {
                lore.add("&eLast Activation: &7Never");
            }
        }

        Material material = mortarData.getLevel() == 0 ? Material.BARRIER : Material.TNT;
        String name = mortarData.getLevel() == 0 ? "&c&lMortar (Locked)" : "&6&lMortar Level " + mortarData.getLevel();

        ItemStack item = createItem(material, name, lore, mortarData.getLevel() > 0);
        inventory.setItem(MORTAR_INFO_SLOT, item);
    }

    private void addUpgradeMortarItem() {
        UUID playerId = player.getUniqueId();
        MortarManager.MortarData mortarData = mortarManager.getMortarData(playerId);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);

        List<String> lore = new ArrayList<>();

        if (mortarData.getLevel() >= 10) {
            lore.add("&a&lMAX LEVEL REACHED!");
            lore.add("&7Your mortar is fully upgraded.");

            ItemStack item = createItem(Material.EMERALD_BLOCK, "&a&lMortar Maxed", lore, true);
            inventory.setItem(UPGRADE_MORTAR_SLOT, item);
            return;
        }

        int nextLevel = mortarData.getLevel() + 1;
        MortarManager.MortarUpgradeRequirement req = getUpgradeRequirement(nextLevel);

        lore.add("&7Upgrade your mortar to level &6" + nextLevel);
        lore.add("");
        lore.add("&eRequirements:");

        // Check pickaxe level
        ItemStack pickaxe = plugin.getPickaxeManager().findPickaxe(player);
        int currentPickaxeLevel = pickaxe != null ? PDCUtil.getPickaxeLevel(pickaxe) : 0;
        boolean pickaxeLevelMet = currentPickaxeLevel >= req.getRequiredPickaxeLevel();
        lore.add((pickaxeLevelMet ? "&a✓ " : "&c✗ ") + "Pickaxe Level: &f" +
                currentPickaxeLevel + "&7/&f" + req.getRequiredPickaxeLevel());

        // Check blocks mined
        long currentBlocks = pickaxe != null ? PDCUtil.getPickaxeBlocksMined(pickaxe) : 0;
        boolean blocksMet = currentBlocks >= req.getRequiredBlocksMined();
        lore.add((blocksMet ? "&a✓ " : "&c✗ ") + "Blocks Mined: &f" +
                NumberFormat.getNumberInstance(Locale.US).format(currentBlocks) + "&7/&f" +
                NumberFormat.getNumberInstance(Locale.US).format(req.getRequiredBlocksMined()));

        // Check tokens
        long currentTokens = playerData != null ? playerData.getTokens() : 0;
        boolean tokensMet = currentTokens >= req.getRequiredTokens();
        lore.add((tokensMet ? "&a✓ " : "&c✗ ") + "Tokens: &f" +
                NumberFormat.getNumberInstance(Locale.US).format(currentTokens) + "&7/&f" +
                NumberFormat.getNumberInstance(Locale.US).format(req.getRequiredTokens()));

        lore.add("");
        boolean canUpgrade = pickaxeLevelMet && blocksMet && tokensMet;
        if (canUpgrade) {
            lore.add("&a&lClick to upgrade!");
        } else {
            lore.add("&c&lRequirements not met!");
        }

        Material material = canUpgrade ? Material.EMERALD : Material.REDSTONE_BLOCK;
        String name = canUpgrade ? "&a&lUpgrade Mortar" : "&c&lUpgrade Mortar";

        ItemStack item = createItem(material, name, lore, canUpgrade);
        inventory.setItem(UPGRADE_MORTAR_SLOT, item);
    }

    private void addStatsItem() {
        UUID playerId = player.getUniqueId();
        MortarManager.MortarData mortarData = mortarManager.getMortarData(playerId);

        List<String> lore = new ArrayList<>();
        lore.add("&7View detailed mortar statistics");
        lore.add("&7and activation history.");
        lore.add("");
        lore.add("&eLevel: &6" + mortarData.getLevel());

        if (mortarData.getLevel() > 0) {
            // Calculate base multiplier
            double baseMultiplier = 1.5 + (0.1 * mortarData.getLevel());
            if (mortarData.hasUpgrade(MortarManager.MortarUpgrade.MULTIPLIER)) {
                baseMultiplier += 0.2 * mortarData.getUpgradeLevel(MortarManager.MortarUpgrade.MULTIPLIER);
            }
            lore.add("&eBoost Multiplier: &6" + String.format("%.1f", baseMultiplier) + "x");

            // Calculate cooldown
            long baseCooldown = 120000 - (5000 * mortarData.getLevel());
            baseCooldown = Math.max(30000, baseCooldown);
            if (mortarData.hasUpgrade(MortarManager.MortarUpgrade.COOLDOWN_CONDENSER)) {
                long reduction = 1000 + (1000 * mortarData.getUpgradeLevel(MortarManager.MortarUpgrade.COOLDOWN_CONDENSER));
                baseCooldown -= reduction;
            }
            baseCooldown = Math.max(10000, baseCooldown);
            lore.add("&eBase Cooldown: &6" + formatTime(baseCooldown));
        }

        lore.add("");
        lore.add("&eClick for detailed stats!");

        ItemStack item = createItem(Material.BOOK, "&e&lMortar Statistics", lore, false);
        inventory.setItem(STATS_SLOT, item);
    }

    private void addUpgradeItems() {
        addUpgradeItem(MortarManager.MortarUpgrade.MULTIPLIER, MULTIPLIER_SLOT, Material.GOLD_INGOT);
        addUpgradeItem(MortarManager.MortarUpgrade.COOLDOWN_CONDENSER, COOLDOWN_CONDENSER_SLOT, Material.CLOCK);
        addUpgradeItem(MortarManager.MortarUpgrade.LIGHTNING_STRIKE, LIGHTNING_STRIKE_SLOT, Material.LIGHTNING_ROD);
        addUpgradeItem(MortarManager.MortarUpgrade.SELECTIVE_FIRE, SELECTIVE_FIRE_SLOT, Material.TARGET);
    }

    private void addUpgradeItem(MortarManager.MortarUpgrade upgrade, int slot, Material material) {
        UUID playerId = player.getUniqueId();
        MortarManager.MortarData mortarData = mortarManager.getMortarData(playerId);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);

        if (mortarData.getLevel() == 0) {
            // Mortar not unlocked
            ItemStack item = createItem(Material.BARRIER, "&c&lLocked",
                    List.of("&7Unlock the mortar first!"), false);
            inventory.setItem(slot, item);
            return;
        }

        int currentLevel = mortarData.getUpgradeLevel(upgrade);
        boolean isMaxed = currentLevel >= upgrade.getMaxLevel();

        List<String> lore = new ArrayList<>();

        // Add upgrade description
        switch (upgrade) {
            case MULTIPLIER:
                lore.add("&7Increases the token/gem multiplier");
                lore.add("&7when mortar activates.");
                lore.add("&7+0.2x per level");
                break;
            case COOLDOWN_CONDENSER:
                lore.add("&7Reduces mortar cooldown by");
                lore.add("&71-5 seconds per level.");
                break;
            case LIGHTNING_STRIKE:
                lore.add("&7Small chance to activate");
                lore.add("&7mortar twice in a row!");
                lore.add("&7Chance increases per level.");
                break;
            case SELECTIVE_FIRE:
                lore.add("&7Increases chance to trigger");
                lore.add("&7high-tier enchantments.");
                lore.add("&7Better targeting per level.");
                break;
        }

        lore.add("");
        lore.add("&eLevel: &6" + currentLevel + "&7/&6" + upgrade.getMaxLevel());

        if (isMaxed) {
            lore.add("");
            lore.add("&a&lMAX LEVEL!");

            ItemStack item = createItem(material, "&a&l" + upgrade.getDisplayName() + " (Maxed)", lore, true);
            inventory.setItem(slot, item);
        } else {
            long cost = upgrade.getUpgradeCost(currentLevel + 1);
            long currentTokens = playerData != null ? playerData.getTokens() : 0;
            boolean canAfford = currentTokens >= cost;

            lore.add("&eCost: &6" + NumberFormat.getNumberInstance(Locale.US).format(cost) + " Tokens");
            lore.add("&eYour Tokens: &6" + NumberFormat.getNumberInstance(Locale.US).format(currentTokens));
            lore.add("");

            if (canAfford) {
                lore.add("&a&lClick to upgrade!");
            } else {
                lore.add("&c&lNot enough tokens!");
            }

            String name = (canAfford ? "&a&l" : "&c&l") + upgrade.getDisplayName();
            ItemStack item = createItem(material, name, lore, canAfford);
            inventory.setItem(slot, item);
        }
    }

    public void handleClick(int slot) {
        UUID playerId = player.getUniqueId();

        if (slot == UPGRADE_MORTAR_SLOT) {
            if (mortarManager.upgradeMortar(player)) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                populateGUI(); // Refresh GUI
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        } else if (slot == STATS_SLOT) {
            player.closeInventory();
            // Send detailed stats in chat
            sendDetailedStats();
        } else if (slot == MULTIPLIER_SLOT) {
            handleUpgradeClick(MortarManager.MortarUpgrade.MULTIPLIER);
        } else if (slot == COOLDOWN_CONDENSER_SLOT) {
            handleUpgradeClick(MortarManager.MortarUpgrade.COOLDOWN_CONDENSER);
        } else if (slot == LIGHTNING_STRIKE_SLOT) {
            handleUpgradeClick(MortarManager.MortarUpgrade.LIGHTNING_STRIKE);
        } else if (slot == SELECTIVE_FIRE_SLOT) {
            handleUpgradeClick(MortarManager.MortarUpgrade.SELECTIVE_FIRE);
        }
    }

    private void handleUpgradeClick(MortarManager.MortarUpgrade upgrade) {
        if (mortarManager.upgradeUpgrade(player, upgrade)) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            populateGUI(); // Refresh GUI
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void sendDetailedStats() {
        UUID playerId = player.getUniqueId();
        MortarManager.MortarData mortarData = mortarManager.getMortarData(playerId);

        ChatUtil.sendMessage(player, "&6&l=== DETAILED MORTAR STATS ===");
        ChatUtil.sendMessage(player, "&eLevel: &6" + mortarData.getLevel());

        if (mortarData.getLevel() == 0) {
            ChatUtil.sendMessage(player, "&cNo mortar unlocked yet!");
            return;
        }

        // Calculate and display all stats
        double baseMultiplier = 1.5 + (0.1 * mortarData.getLevel());
        if (mortarData.hasUpgrade(MortarManager.MortarUpgrade.MULTIPLIER)) {
            baseMultiplier += 0.2 * mortarData.getUpgradeLevel(MortarManager.MortarUpgrade.MULTIPLIER);
        }
        ChatUtil.sendMessage(player, "&eBoost Multiplier: &6" + String.format("%.1f", baseMultiplier) + "x");

        long baseCooldown = 120000 - (5000 * mortarData.getLevel());
        baseCooldown = Math.max(30000, baseCooldown);
        if (mortarData.hasUpgrade(MortarManager.MortarUpgrade.COOLDOWN_CONDENSER)) {
            long reduction = 1000 + (1000 * mortarData.getUpgradeLevel(MortarManager.MortarUpgrade.COOLDOWN_CONDENSER));
            baseCooldown -= reduction;
        }
        baseCooldown = Math.max(10000, baseCooldown);
        ChatUtil.sendMessage(player, "&eBase Cooldown: &6" + formatTime(baseCooldown));

        // Current status
        if (mortarManager.isOnCooldown(playerId)) {
            long remaining = mortarManager.getCooldownRemaining(playerId);
            ChatUtil.sendMessage(player, "&eCurrent Cooldown: &c" + formatTime(remaining));
        } else {
            ChatUtil.sendMessage(player, "&eCurrent Status: &aReady to activate!");
        }

        if (mortarManager.hasActiveBoost(playerId)) {
            double multiplier = mortarManager.getActiveBoostMultiplier(playerId);
            long boostRemaining = mortarData.getBoostEndTime() - System.currentTimeMillis();
            ChatUtil.sendMessage(player, "&eActive Boost: &6" + String.format("%.1f", multiplier) + "x &efor &6" + formatTime(boostRemaining));
        }

        if (mortarData.getLastActivation() > 0) {
            long timeSince = System.currentTimeMillis() - mortarData.getLastActivation();
            ChatUtil.sendMessage(player, "&eLast Activation: &6" + formatTime(timeSince) + " ago");
        } else {
            ChatUtil.sendMessage(player, "&eLast Activation: &7Never");
        }

        // Upgrades
        ChatUtil.sendMessage(player, "&6&lUpgrades:");
        for (MortarManager.MortarUpgrade upgrade : MortarManager.MortarUpgrade.values()) {
            int level = mortarData.getUpgradeLevel(upgrade);
            ChatUtil.sendMessage(player, "&e" + upgrade.getDisplayName() + ": &6" + level + "&7/&6" + upgrade.getMaxLevel());
        }
    }

    private MortarManager.MortarUpgradeRequirement getUpgradeRequirement(int level) {
        // This should match the requirements in MortarManager
        return switch (level) {
            case 1 -> new MortarManager.MortarUpgradeRequirement(5, 1000, 5000);
            case 2 -> new MortarManager.MortarUpgradeRequirement(10, 5000, 15000);
            case 3 -> new MortarManager.MortarUpgradeRequirement(15, 15000, 35000);
            case 4 -> new MortarManager.MortarUpgradeRequirement(20, 35000, 75000);
            case 5 -> new MortarManager.MortarUpgradeRequirement(25, 75000, 150000);
            case 6 -> new MortarManager.MortarUpgradeRequirement(30, 150000, 300000);
            case 7 -> new MortarManager.MortarUpgradeRequirement(35, 300000, 600000);
            case 8 -> new MortarManager.MortarUpgradeRequirement(40, 600000, 1200000);
            case 9 -> new MortarManager.MortarUpgradeRequirement(45, 1200000, 2500000);
            case 10 -> new MortarManager.MortarUpgradeRequirement(50, 2500000, 5000000);
            default -> new MortarManager.MortarUpgradeRequirement(Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        };
    }

    private ItemStack createItem(Material material, String name, List<String> lore, boolean enchanted) {
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

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_POTION_EFFECTS);

            if (enchanted) {
                meta.addEnchant(Enchantment.LURE, 1, true);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + "s";
        } else {
            long minutes = seconds / 60;
            seconds = seconds % 60;
            if (minutes < 60) {
                return minutes + "m " + seconds + "s";
            } else {
                long hours = minutes / 60;
                minutes = minutes % 60;
                return hours + "h " + minutes + "m " + seconds + "s";
            }
        }
    }

    public void open() {
        player.openInventory(inventory);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}