package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.managers.CrystalManager;
import com.strikesenchantcore.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CrystalsGUI {
    private final EnchantCore plugin;
    private final Player player;
    private final CrystalManager crystalManager;
    private Inventory inventory;
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 7;

    public CrystalsGUI(EnchantCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.crystalManager = plugin.getCrystalManager();
        createInventory();
    }

    private void createInventory() {
        inventory = Bukkit.createInventory(null, 54, ColorUtils.translateColors("&f\uE5A1\uE0AC"));
        populateInventory();
    }

    private void populateInventory() {
        inventory.clear();
        addEquippedCrystalsSection();
        addStorageCrystalsSection();
        addNavigationItems();
        addInfoItems();
        addPickaxePreview();
    }

    private void addEquippedCrystalsSection() {
        ItemStack equippedTitle = new ItemStack(Material.BARRIER);
        ItemMeta equippedMeta = equippedTitle.getItemMeta();
        equippedMeta.setDisplayName(ColorUtils.translateColors("&a&lEQUIPPED CRYSTALS"));
        equippedMeta.setLore(Arrays.asList(
                ColorUtils.translateColors("&7Crystals equipped in this section"),
                ColorUtils.translateColors("&7provide passive bonuses."),
                "",
                ColorUtils.translateColors("&eClick a crystal below to unequip it.")
        ));
        equippedTitle.setItemMeta(equippedMeta);
        inventory.setItem(24, equippedTitle);
        inventory.setItem(25, equippedTitle.clone());

        int[] equippedSlots = {28, 29, 30, 31, 32, 33, 34};
        Map<Integer, String> equippedCrystals = crystalManager.getEquippedCrystals(player);

        for (int i = 0; i < equippedSlots.length; i++) {
            int slot = equippedSlots[i];
            String crystalKey = equippedCrystals.get(i);

            if (crystalKey != null) {
                String[] parts = crystalKey.split("_");
                if (parts.length == 2) {
                    String type = parts[0];
                    int tier = Integer.parseInt(parts[1]);
                    ItemStack crystalItem = createCrystalItem(type, tier, 1, true);
                    inventory.setItem(slot, crystalItem);
                }
            } else {
                ItemStack emptySlot = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta emptyMeta = emptySlot.getItemMeta();
                emptyMeta.setDisplayName(ColorUtils.translateColors("&7Empty Equipment Slot"));
                emptyMeta.setLore(Arrays.asList(
                        ColorUtils.translateColors("&7Click a crystal from your storage"),
                        ColorUtils.translateColors("&7to equip it here.")
                ));
                emptySlot.setItemMeta(emptyMeta);
                inventory.setItem(slot, emptySlot);
            }
        }
    }

    private void addStorageCrystalsSection() {
        ItemStack storageTitle = new ItemStack(Material.BARRIER);
        ItemMeta storageMeta = storageTitle.getItemMeta();
        storageMeta.setDisplayName(ColorUtils.translateColors("&b&lCRYSTAL STORAGE"));
        storageMeta.setLore(Arrays.asList(
                ColorUtils.translateColors("&7Your unequipped crystals."),
                "",
                ColorUtils.translateColors("&eLeft-click to equip."),
                ColorUtils.translateColors("&eRight-click to merge (needs 3).")
        ));
        storageTitle.setItemMeta(storageMeta);
        inventory.setItem(6, storageTitle);
        inventory.setItem(7, storageTitle.clone());

        int[] storageSlots = {10, 11, 12, 13, 14, 15, 17};
        Map<String, Integer> storageCrystals = crystalManager.getStorageCrystals(player);
        List<Map.Entry<String, Integer>> crystalList = new ArrayList<>(storageCrystals.entrySet());

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, crystalList.size());

        for (int i = 0; i < storageSlots.length; i++) {
            int crystalIndex = startIndex + i;
            if (crystalIndex < endIndex) {
                Map.Entry<String, Integer> entry = crystalList.get(crystalIndex);
                String crystalKey = entry.getKey();
                int amount = entry.getValue();

                String[] parts = crystalKey.split("_");
                if (parts.length == 2) {
                    String type = parts[0];
                    int tier = Integer.parseInt(parts[1]);
                    ItemStack crystalItem = createCrystalItem(type, tier, amount, false);
                    inventory.setItem(storageSlots[i], crystalItem);
                }
            }
        }
    }

    private void addNavigationItems() {
        Map<String, Integer> storageCrystals = crystalManager.getStorageCrystals(player);
        int totalPages = (int) Math.ceil((double) storageCrystals.size() / ITEMS_PER_PAGE);

        if (currentPage > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(ColorUtils.translateColors("&e&lPREVIOUS PAGE"));
            prevMeta.setLore(List.of(ColorUtils.translateColors("&7Page " + (currentPage + 1) + " of " + Math.max(1, totalPages))));
            prevPage.setItemMeta(prevMeta);
            inventory.setItem(46, prevPage);
        }

        if (currentPage < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(ColorUtils.translateColors("&e&lNEXT PAGE"));
            nextMeta.setLore(List.of(ColorUtils.translateColors("&7Page " + (currentPage + 2) + " of " + totalPages)));
            nextPage.setItemMeta(nextMeta);
            inventory.setItem(52, nextPage);
        }

        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(ColorUtils.translateColors("&c&lBACK"));
        backMeta.setLore(List.of(ColorUtils.translateColors("&7Click to return to main menu.")));
        backButton.setItemMeta(backMeta);
        inventory.setItem(45, backButton);
    }

    private void addInfoItems() {
        ItemStack stats = new ItemStack(Material.BARRIER);
        ItemMeta statsMeta = stats.getItemMeta();
        statsMeta.setDisplayName(ColorUtils.translateColors("&6&lCRYSTAL STATISTICS"));
        List<String> statsLore = new ArrayList<>();
        statsLore.add(ColorUtils.translateColors("&7Your current crystal bonuses:"));
        statsLore.add("");

        double tokenBoost = crystalManager.getTokenMultiplier(player) * 100;
        double gemBoost = crystalManager.getGemMultiplier(player) * 100;
        double procBoost = crystalManager.getProcMultiplier(player) * 100;
        double rankBoost = crystalManager.getRankMultiplier(player) * 100;
        double pickaxeXpBoost = crystalManager.getPickaxeXpMultiplier(player) * 100;
        double petBoost = crystalManager.getPetMultiplier(player) * 100;
        double salvageBoost = crystalManager.getSalvageMultiplier(player) * 100;

        statsLore.add(ColorUtils.translateColors("&eToken: &a+" + String.format("%.1f", tokenBoost) + "%"));
        statsLore.add(ColorUtils.translateColors("&bGem: &a+" + String.format("%.1f", gemBoost) + "%"));
        statsLore.add(ColorUtils.translateColors("&dProc: &a+" + String.format("%.1f", procBoost) + "%"));
        statsLore.add(ColorUtils.translateColors("&aRank: &a+" + String.format("%.1f", rankBoost) + "%"));
        statsLore.add(ColorUtils.translateColors("&6Pickaxe XP: &a+" + String.format("%.1f", pickaxeXpBoost) + "%"));
        statsLore.add(ColorUtils.translateColors("&9Pet: &a+" + String.format("%.1f", petBoost) + "%"));
        statsLore.add(ColorUtils.translateColors("&cSalvage: &a+" + String.format("%.1f", salvageBoost) + "%"));
        statsMeta.setLore(statsLore);
        stats.setItemMeta(statsMeta);
        inventory.setItem(53, stats);
    }

    private void addPickaxePreview() {
        ItemStack pickaxe = null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta() &&
                    item.getItemMeta().getPersistentDataContainer()
                            .has(new org.bukkit.NamespacedKey(plugin, "enchantcore_pickaxe"),
                                    org.bukkit.persistence.PersistentDataType.BYTE)) {
                pickaxe = item;
                break;
            }
        }

        if (pickaxe != null) {
            plugin.getPickaxeManager().updatePickaxe(pickaxe, player);
            ItemStack pickaxeClone = pickaxe.clone();
            ItemMeta meta = pickaxeClone.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(ColorUtils.translateColors("&7Crystal bonuses applied to this pickaxe"));
                meta.setLore(lore);
                pickaxeClone.setItemMeta(meta);
            }
            inventory.setItem(49, pickaxeClone);
        } else {
            ItemStack placeholder = new ItemStack(Material.DIAMOND_PICKAXE);
            ItemMeta meta = placeholder.getItemMeta();
            meta.setDisplayName(ColorUtils.translateColors("&cNo EnchantCore Pickaxe Found"));
            meta.setLore(List.of(
                    ColorUtils.translateColors("&7You need an EnchantCore pickaxe"),
                    ColorUtils.translateColors("&7in your inventory to see it here.")
            ));
            placeholder.setItemMeta(meta);
            inventory.setItem(49, placeholder);
        }
    }

    private ItemStack createCrystalItem(String type, int tier, int amount, boolean equipped) {
        Material material = getCrystalMaterial(type);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String color = crystalManager.getColor(type);
        String tierColor = crystalManager.getTierColor(tier);
        String displayName = color + crystalManager.getDisplayName(type) + " &7Tier " + tierColor + tier;
        if (amount > 1) displayName += " &8x" + amount;

        meta.setDisplayName(ColorUtils.translateColors(displayName));

        List<String> lore = new ArrayList<>();
        double baseMultiplier = getBaseMultiplier(type);
        double totalBonus = baseMultiplier * tier * 100;
        lore.add(ColorUtils.translateColors("&7Boost: &a+" + String.format("%.1f", totalBonus) + "%"));
        lore.add("");
        lore.add(ColorUtils.translateColors("&7Type: " + color + crystalManager.getDisplayName(type)));
        lore.add(ColorUtils.translateColors("&7Tier: " + tierColor + tier + "&8/&710"));
        lore.add("");

        if (tier < 10 && !equipped) {
            lore.add(ColorUtils.translateColors("&7Merge 3 of this tier to get Tier " + (tier + 1)));
            lore.add("");
        }

        if (equipped) {
            lore.add(ColorUtils.translateColors("&a✓ Currently Equipped"));
            lore.add(ColorUtils.translateColors("&eClick to unequip"));
        } else {
            lore.add(ColorUtils.translateColors("&eLeft-click to equip"));
            if (amount >= 3 && tier < 10) {
                lore.add(ColorUtils.translateColors("&eRight-click to merge"));
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        item.setAmount(Math.min(amount, 64));
        return item;
    }

    private Material getCrystalMaterial(String type) {
        switch (type.toUpperCase()) {
            case "TOKEN": return Material.SUNFLOWER;
            case "GEM": return Material.DIAMOND;
            case "PROC": return Material.AMETHYST_SHARD;
            case "RANK": return Material.EMERALD;
            case "PICKAXE_XP": return Material.EXPERIENCE_BOTTLE;
            case "PET": return Material.BONE;
            case "SALVAGE": return Material.ANVIL;
            default: return Material.QUARTZ;
        }
    }

    private double getBaseMultiplier(String type) {
        switch (type.toUpperCase()) {
            case "TOKEN": return 0.05;
            case "GEM": return 0.04;
            case "PROC": return 0.03;
            case "RANK": return 0.06;
            case "PICKAXE_XP": return 0.08;
            case "PET": return 0.05;
            case "SALVAGE": return 0.07;
            default: return 0.0;
        }
    }

    public void open() {
        player.openInventory(inventory);
        plugin.getCrystalsGUIListener().registerGUI(player, this);
    }

    public void refresh() {
        populateInventory();
    }

    public Inventory getInventory() { return inventory; }
    public Player getPlayer() { return player; }
    public CrystalManager getCrystalManager() { return crystalManager; }
}