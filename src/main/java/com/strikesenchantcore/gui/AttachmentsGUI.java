package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.managers.AttachmentManager;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AttachmentsGUI implements InventoryHolder {

    private final EnchantCore plugin;
    private final Player player;
    private final AttachmentManager attachmentManager;
    private final Inventory inventory;

    // GUI Layout Constants
    private static final int GUI_SIZE = 54;
    private static final int[] EQUIPPED_SLOTS = {28, 29, 30, 31, 32, 33, 34};


    private static final int BACK_BUTTON_SLOT = 45;
    private static final int MERGE_BUTTON_SLOT_1 = 24;
    private static final int MERGE_BUTTON_SLOT_2 = 25;
    private static final int PICKAXE_SHOWCASE_SLOT = 49;
    private static final int TOTAL_PROC_INFO_SLOT = 53;

    private static final int STORAGE_START = 10;
    private static final int STORAGE_END = 16;

    public AttachmentsGUI(EnchantCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.attachmentManager = plugin.getAttachmentManager();
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, ColorUtils.translateColors("&f\uE5A1\uE0AD"));

        populateGUI();
    }

    private void populateGUI() {
        inventory.clear(); // Clear inventory to prevent item duplication on refresh
        populateEquippedSlots();
        populateStorageArea();
        addMergeAllButton();
        addBackButton();
        addPickaxePreview();
        addTotalProcInfo();
    }



    private void populateEquippedSlots() {
        AttachmentManager.AttachmentStorage storage = attachmentManager.getPlayerStorage(player.getUniqueId());

        for (int i = 0; i < EQUIPPED_SLOTS.length; i++) {
            int slot = EQUIPPED_SLOTS[i];
            Integer equippedTier = storage.getEquippedAttachment(i);

            if (equippedTier != null) {
                // Show equipped attachment
                ItemStack equipped = attachmentManager.createAttachmentItem(equippedTier, 1);
                ItemMeta meta = equipped.getItemMeta();
                if (meta != null) {
                    List<String> newLore = new ArrayList<>();
                    newLore.add(ColorUtils.translateColors("&7Proc Bonus: &e+" + String.format("%.1f", attachmentManager.getProcBonusForTier(equippedTier) * 100) + "%"));
                    newLore.add(ColorUtils.translateColors("&7Enhances enchant activation rates"));
                    newLore.add("");
                    newLore.add(ColorUtils.translateColors("&c&lEQUIPPED"));
                    newLore.add(ColorUtils.translateColors("&eClick to unequip"));
                    meta.setLore(newLore);
                    equipped.setItemMeta(meta);
                }
                inventory.setItem(slot, equipped);
            } else {
                // Show empty equipped slot
                ItemStack empty = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "&7&lEquipped Slot " + (i + 1),
                        Arrays.asList("&7Drag an attachment here", "&7to equip it!"), false);
                inventory.setItem(slot, empty);
            }
        }
    }

    private void populateStorageArea() {
        AttachmentManager.AttachmentStorage storage = attachmentManager.getPlayerStorage(player.getUniqueId());
        Map<Integer, Integer> attachments = storage.getAllAttachments();

        int currentSlot = STORAGE_START;

        for (int tier = 1; tier <= AttachmentManager.MAX_TIER && currentSlot <= STORAGE_END; tier++) {
            int count = attachments.getOrDefault(tier, 0);
            if (count > 0) {
                ItemStack attachmentStack = attachmentManager.createAttachmentItem(tier, count);
                ItemMeta meta = attachmentStack.getItemMeta();
                if (meta != null) {
                    List<String> newLore = new ArrayList<>();
                    newLore.add(ColorUtils.translateColors("&7Proc Bonus: &e+" + String.format("%.1f", attachmentManager.getProcBonusForTier(tier) * 100) + "%"));
                    newLore.add(ColorUtils.translateColors("&7Enhances enchant activation rates"));
                    newLore.add("");
                    newLore.add(ColorUtils.translateColors("&7Amount: &e" + count));

                    if (count >= AttachmentManager.MERGE_COST && tier < AttachmentManager.MAX_TIER) {
                        int canMerge = count / AttachmentManager.MERGE_COST;
                        newLore.add(ColorUtils.translateColors("&aCan merge: &6" + canMerge + "x &7(3→1)"));
                        newLore.add(ColorUtils.translateColors("&eShift+Right-click to merge"));
                    }

                    newLore.add(ColorUtils.translateColors("&eLeft-click to equip one"));
                    newLore.add(ColorUtils.translateColors("&eRight-click for options"));
                    meta.setLore(newLore);
                    attachmentStack.setItemMeta(meta);
                }
                inventory.setItem(currentSlot, attachmentStack);
                currentSlot++;
            }
        }

        while (currentSlot <= STORAGE_END) {
            inventory.setItem(currentSlot, null);
            currentSlot++;
        }
    }

    private void addMergeAllButton() {
        List<String> lore = Arrays.asList(
                ColorUtils.translateColors("&7Automatically merge all possible"),
                ColorUtils.translateColors("&7attachments to higher tiers"),
                "",
                ColorUtils.translateColors("&e&lClick to merge all!")
        );

        ItemStack mergeButton = createItem(Material.BARRIER, "&6&lMerge All", lore, true);
        inventory.setItem(MERGE_BUTTON_SLOT_1, mergeButton.clone());
        inventory.setItem(MERGE_BUTTON_SLOT_2, mergeButton.clone());
    }

    private void addBackButton() {
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ColorUtils.translateColors("&c&lBACK"));
            backMeta.setLore(List.of(ColorUtils.translateColors("&7Click to return to main menu.")));
            backMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_POTION_EFFECTS);
            backButton.setItemMeta(backMeta);
        }
        inventory.setItem(BACK_BUTTON_SLOT, backButton);
    }

    private void addPickaxePreview() {
        ItemStack pickaxe = plugin.getPickaxeManager().findPickaxe(player);
        if (pickaxe != null) {
            plugin.getPickaxeManager().updatePickaxe(pickaxe, player);
            ItemStack pickaxeClone = pickaxe.clone();
            ItemMeta meta = pickaxeClone.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(ColorUtils.translateColors("&7Attachment bonuses applied to this pickaxe"));
                meta.setLore(lore);
                pickaxeClone.setItemMeta(meta);
            }
            inventory.setItem(PICKAXE_SHOWCASE_SLOT, pickaxeClone);
        } else {
            ItemStack placeholder = new ItemStack(Material.DIAMOND_PICKAXE);
            ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtils.translateColors("&cNo EnchantCore Pickaxe Found"));
                meta.setLore(List.of(
                        ColorUtils.translateColors("&7You need an EnchantCore pickaxe"),
                        ColorUtils.translateColors("&7in your inventory to see it here.")
                ));
                placeholder.setItemMeta(meta);
            }
            inventory.setItem(PICKAXE_SHOWCASE_SLOT, placeholder);
        }
    }

    private void addTotalProcInfo() {
        AttachmentManager.AttachmentStorage storage = attachmentManager.getPlayerStorage(player.getUniqueId());
        double totalBonus = attachmentManager.getTotalProcBonus(player.getUniqueId());
        int equippedCount = storage.getEquippedCount();

        List<String> lore = new ArrayList<>();
        lore.add(ColorUtils.translateColors("&7Shows your total proc rate bonus"));
        lore.add(ColorUtils.translateColors("&7from all equipped attachments"));
        lore.add("");
        lore.add(ColorUtils.translateColors("&eEquipped: &6" + equippedCount + "&7/&6" + AttachmentManager.MAX_EQUIPPED_ATTACHMENTS));
        lore.add(ColorUtils.translateColors("&eTotal Bonus: &6+" + String.format("%.2f", totalBonus * 100) + "%"));

        if (equippedCount > 0) {
            lore.add("");
            lore.add(ColorUtils.translateColors("&6&lEquipped Breakdown:"));
            for (int slot = 0; slot < AttachmentManager.MAX_EQUIPPED_ATTACHMENTS; slot++) {
                Integer tier = storage.getEquippedAttachment(slot);
                if (tier != null) {
                    double bonus = attachmentManager.getProcBonusForTier(tier);
                    lore.add(ColorUtils.translateColors("&7• Tier " + tier + ": &e+" + String.format("%.1f", bonus * 100) + "%"));
                }
            }
        } else {
            lore.add("");
            lore.add(ColorUtils.translateColors("&7Equip attachments to boost"));
            lore.add(ColorUtils.translateColors("&7your enchant proc rates!"));
        }

        ItemStack infoItem = createItem(Material.BARRIER, "&6&lTotal Proc Bonus", lore, totalBonus > 0);
        inventory.setItem(TOTAL_PROC_INFO_SLOT, infoItem);
    }

    public void handleClick(int slot, boolean isShiftClick, boolean isRightClick) {
        // Handle back button
        if (slot == BACK_BUTTON_SLOT) {
            handleBackButton(); // Use the new method for a smooth transition
            return;
        }

        // Handle equipped slot clicks
        for (int i = 0; i < EQUIPPED_SLOTS.length; i++) {
            if (slot == EQUIPPED_SLOTS[i]) {
                handleEquippedSlotClick(i);
                return;
            }
        }

        // Handle merge buttons
        if (slot == MERGE_BUTTON_SLOT_1 || slot == MERGE_BUTTON_SLOT_2) {
            attachmentManager.mergeAllPossible(player);
            populateGUI();
            return;
        }

        // Handle informational slots
        if (slot == PICKAXE_SHOWCASE_SLOT || slot == TOTAL_PROC_INFO_SLOT) {
            return;
        }

        // Handle storage area clicks
        if (slot >= STORAGE_START && slot <= STORAGE_END) {
            handleStorageClick(slot, isShiftClick, isRightClick);
        }
    }

    // New method to handle the back button logic smoothly
    private void handleBackButton() {
        try {
            final com.strikesenchantcore.data.PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (playerData != null) {
                ItemStack pickaxe = plugin.getPickaxeManager().findPickaxe(player);

                if (pickaxe != null) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        new EnchantGUI(plugin, player, playerData, pickaxe).open();
                    }, 1L);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    ChatUtil.sendMessage(player, "&cCould not find your EnchantCore pickaxe!");
                    player.closeInventory();
                }
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                ChatUtil.sendMessage(player, "&cError loading player data!");
                player.closeInventory();
            }
        } catch (Exception e) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            ChatUtil.sendMessage(player, "&cError opening main menu!");
            e.printStackTrace();
            player.closeInventory();
        }
    }


    private void handleEquippedSlotClick(int equippedSlot) {
        if (attachmentManager.unequipAttachment(player, equippedSlot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            populateGUI();
        }
    }

    private void handleStorageClick(int slot, boolean isShiftClick, boolean isRightClick) {
        ItemStack clickedItem = inventory.getItem(slot);
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int tier = parseTierFromName(clickedItem.getItemMeta().getDisplayName());
        if (tier == -1) return;

        if (isShiftClick && isRightClick) {
            if (attachmentManager.canMerge(player.getUniqueId(), tier)) {
                attachmentManager.mergeAttachments(player, tier);
                populateGUI();
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                ChatUtil.sendMessage(player, "&cNeed at least " + AttachmentManager.MERGE_COST + " Tier " + tier + " attachments to merge!");
            }
        } else if (!isRightClick) {
            if (attachmentManager.equipAttachment(player, tier)) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                populateGUI();
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        } else {
            showAttachmentOptions(tier, attachmentManager.getPlayerStorage(player.getUniqueId()).getAttachmentCount(tier));
        }
    }

    private void showAttachmentOptions(int tier, int count) {
        ChatUtil.sendMessage(player, "&6&l=== Tier " + tier + " Attachment Options ===");
        ChatUtil.sendMessage(player, "&eAmount: &6" + count);
        ChatUtil.sendMessage(player, "&eProc Bonus: &6+" + String.format("%.1f", attachmentManager.getProcBonusForTier(tier) * 100) + "%");

        if (count >= AttachmentManager.MERGE_COST && tier < AttachmentManager.MAX_TIER) {
            int canMerge = count / AttachmentManager.MERGE_COST;
            ChatUtil.sendMessage(player, "&eCan merge: &6" + canMerge + "x &7into Tier " + (tier + 1));
            ChatUtil.sendMessage(player, "&7Shift+Right-click to merge");
        }

        ChatUtil.sendMessage(player, "&7Left-click to equip • Right-click for options");
    }

    private int parseTierFromName(String displayName) {
        try {
            String cleaned = ChatColor.stripColor(displayName);
            String[] parts = cleaned.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equalsIgnoreCase("Tier") && i + 1 < parts.length) {
                    return Integer.parseInt(parts[i + 1]);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return -1;
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

    public void open() {
        player.openInventory(inventory);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void refresh() {
        populateGUI();
    }
}