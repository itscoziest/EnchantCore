package com.strikesenchantcore.managers;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.ColorUtils;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class AttachmentManager {

    private final EnchantCore plugin;
    private final Map<UUID, AttachmentStorage> playerStorages;

    // Constants
    public static final int MAX_EQUIPPED_ATTACHMENTS = 7;
    public static final int MAX_TIER = 10;
    public static final int MERGE_COST = 3; // 3 attachments merge into 1

    public AttachmentManager(EnchantCore plugin) {
        this.plugin = plugin;
        this.playerStorages = new HashMap<>();
    }

    public AttachmentStorage getPlayerStorage(UUID playerId) {
        return playerStorages.computeIfAbsent(playerId, k -> new AttachmentStorage());
    }

    public void giveAttachmentBox(Player player, int amount) {
        for (int i = 0; i < amount; i++) {
            ItemStack box = createAttachmentBox();
            if (player.getInventory().firstEmpty() == -1) {
                // Inventory full, drop on ground
                player.getWorld().dropItemNaturally(player.getLocation(), box);
                ChatUtil.sendMessage(player, "&6Attachment Box dropped on ground! (Inventory full)");
            } else {
                player.getInventory().addItem(box);
            }
        }

        if (amount > 1) {
            ChatUtil.sendMessage(player, "&6You received &e" + amount + " &6Attachment Boxes!");
        } else {
            ChatUtil.sendMessage(player, "&6You received an &eAttachment Box&6!");
        }
    }

    public ItemStack createAttachmentBox() {
        ItemStack box = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = box.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.translateColors("&6&lAttachment Box"));
            List<String> lore = Arrays.asList(
                    ColorUtils.translateColors("&7Right-click to open!"),
                    ColorUtils.translateColors("&7Contains a random attachment"),
                    ColorUtils.translateColors("&7from Tier 1 to Tier 10"),
                    "",
                    ColorUtils.translateColors("&e&lClick to open!")
            );
            meta.setLore(lore);
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            box.setItemMeta(meta);
        }
        return box;
    }

    public void openAttachmentBox(Player player, ItemStack box) {
        // Generate random tier (weighted towards lower tiers)
        int tier = generateRandomTier();

        AttachmentStorage storage = getPlayerStorage(player.getUniqueId());
        storage.addAttachment(tier, 1);

        // Remove one box from inventory
        if (box.getAmount() > 1) {
            box.setAmount(box.getAmount() - 1);
        } else {
            player.getInventory().remove(box);
        }

        ChatUtil.sendMessage(player, "&6&lAttachment Box opened! &eReceived: &6Tier " + tier + " Attachment");

        // Play sound effect
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private int generateRandomTier() {
        // Weighted distribution - higher chance for lower tiers
        double random = ThreadLocalRandom.current().nextDouble();

        if (random < 0.35) return 1;      // 35%
        if (random < 0.60) return 2;      // 25%
        if (random < 0.78) return 3;      // 18%
        if (random < 0.89) return 4;      // 11%
        if (random < 0.95) return 5;      // 6%
        if (random < 0.98) return 6;      // 3%
        if (random < 0.995) return 7;     // 1.5%
        if (random < 0.999) return 8;     // 0.4%
        if (random < 0.9998) return 9;    // 0.08%
        return 10;                        // 0.02%
    }

    public boolean canMerge(UUID playerId, int tier) {
        if (tier >= MAX_TIER) return false;

        AttachmentStorage storage = getPlayerStorage(playerId);
        return storage.getAttachmentCount(tier) >= MERGE_COST;
    }

    public boolean mergeAttachments(Player player, int tier) {
        if (!canMerge(player.getUniqueId(), tier)) {
            ChatUtil.sendMessage(player, "&cNot enough Tier " + tier + " attachments to merge!");
            return false;
        }

        AttachmentStorage storage = getPlayerStorage(player.getUniqueId());
        storage.removeAttachment(tier, MERGE_COST);
        storage.addAttachment(tier + 1, 1);

        ChatUtil.sendMessage(player, "&6&lMerged! &e" + MERGE_COST + "x Tier " + tier + " &6→ &e1x Tier " + (tier + 1));
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);

        return true;
    }

    public void mergeAllPossible(Player player) {
        AttachmentStorage storage = getPlayerStorage(player.getUniqueId());
        int totalMerged = 0;

        // Start from tier 1 and work upwards
        for (int tier = 1; tier < MAX_TIER; tier++) {
            int currentCount = storage.getAttachmentCount(tier);
            while (currentCount >= MERGE_COST) {
                storage.removeAttachment(tier, MERGE_COST);
                storage.addAttachment(tier + 1, 1);
                totalMerged++;
                currentCount = storage.getAttachmentCount(tier); // Update count after removal
            }
        }

        if (totalMerged > 0) {
            ChatUtil.sendMessage(player, "&6&lMerge All Complete! &ePerformed " + totalMerged + " merges!");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
        } else {
            ChatUtil.sendMessage(player, "&cNo attachments available to merge!");
        }
    }

    public boolean equipAttachment(Player player, int tier) {
        UUID playerId = player.getUniqueId();
        AttachmentStorage storage = getPlayerStorage(playerId);

        if (storage.getEquippedCount() >= MAX_EQUIPPED_ATTACHMENTS) {
            ChatUtil.sendMessage(player, "&cMax attachments equipped! (" + MAX_EQUIPPED_ATTACHMENTS + "/7)");
            return false;
        }

        if (storage.getAttachmentCount(tier) <= 0) {
            ChatUtil.sendMessage(player, "&cNo Tier " + tier + " attachments available!");
            return false;
        }

        storage.removeAttachment(tier, 1);
        storage.equipAttachment(tier);

        ChatUtil.sendMessage(player, "&6Equipped &eTier " + tier + " &6attachment!");
        return true;
    }

    public boolean unequipAttachment(Player player, int slot) {
        AttachmentStorage storage = getPlayerStorage(player.getUniqueId());
        Integer tier = storage.getEquippedAttachment(slot);

        if (tier == null) {
            ChatUtil.sendMessage(player, "&cNo attachment in that slot!");
            return false;
        }

        storage.unequipAttachment(slot);
        storage.addAttachment(tier, 1);

        ChatUtil.sendMessage(player, "&6Unequipped &eTier " + tier + " &6attachment!");
        return true;
    }

    public double getTotalProcBonus(UUID playerId) {
        AttachmentStorage storage = getPlayerStorage(playerId);
        double totalBonus = 0.0;

        for (Integer tier : storage.getEquippedAttachments()) {
            totalBonus += getProcBonusForTier(tier);
        }

        return totalBonus;
    }

    public double getProcBonusForTier(int tier) {
        // Configurable proc bonus per tier
        return switch (tier) {
            case 1 -> 0.001;  // +0.1%
            case 2 -> 0.002;  // +0.2%
            case 3 -> 0.005;  // +0.5%
            case 4 -> 0.010;  // +1.0%
            case 5 -> 0.020;  // +2.0%
            case 6 -> 0.035;  // +3.5%
            case 7 -> 0.050;  // +5.0%
            case 8 -> 0.070;  // +7.0%
            case 9 -> 0.085;  // +8.5%
            case 10 -> 0.100; // +10.0%
            default -> 0.0;
        };
    }

    public ItemStack createAttachmentItem(int tier, int amount) {
        Material material = getAttachmentMaterial(tier);
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ColorUtils.translateColors(getTierColor(tier) + "&lTier " + tier + " Attachment"));
            List<String> lore = Arrays.asList(
                    ColorUtils.translateColors("&7Proc Bonus: &e+" + String.format("%.1f", getProcBonusForTier(tier) * 100) + "%"),
                    ColorUtils.translateColors("&7Enhances enchant activation rates"),
                    "",
                    ColorUtils.translateColors("&eLeft-click to equip"),
                    ColorUtils.translateColors("&eRight-click for merge options")
            );
            meta.setLore(lore);

            // Add enchant glow for higher tiers
            if (tier >= 5) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }

        return item;
    }

    private Material getAttachmentMaterial(int tier) {
        return switch (tier) {
            case 1 -> Material.IRON_NUGGET;
            case 2 -> Material.GOLD_NUGGET;
            case 3 -> Material.COPPER_INGOT;
            case 4 -> Material.IRON_INGOT;
            case 5 -> Material.GOLD_INGOT;
            case 6 -> Material.DIAMOND;
            case 7 -> Material.EMERALD;
            case 8 -> Material.NETHERITE_SCRAP;
            case 9 -> Material.NETHERITE_INGOT;
            case 10 -> Material.NETHER_STAR;
            default -> Material.STONE;
        };
    }

    private String getTierColor(int tier) {
        return switch (tier) {
            case 1 -> "&7";  // Gray
            case 2 -> "&f";  // White
            case 3 -> "&a";  // Green
            case 4 -> "&9";  // Blue
            case 5 -> "&d";  // Light Purple
            case 6 -> "&5";  // Dark Purple
            case 7 -> "&6";  // Gold
            case 8 -> "&c";  // Red
            case 9 -> "&4";  // Dark Red
            case 10 -> "&e"; // Yellow
            default -> "&7";
        };
    }

    // Inner class for attachment storage
    public static class AttachmentStorage {
        private final Map<Integer, Integer> attachments; // tier -> count
        private final Map<Integer, Integer> equipped;    // slot -> tier

        public AttachmentStorage() {
            this.attachments = new HashMap<>();
            this.equipped = new HashMap<>();
        }

        public void addAttachment(int tier, int amount) {
            attachments.merge(tier, amount, Integer::sum);
        }

        public boolean removeAttachment(int tier, int amount) {
            int current = attachments.getOrDefault(tier, 0);
            if (current < amount) return false;

            if (current == amount) {
                attachments.remove(tier);
            } else {
                attachments.put(tier, current - amount);
            }
            return true;
        }

        public int getAttachmentCount(int tier) {
            return attachments.getOrDefault(tier, 0);
        }

        public Map<Integer, Integer> getAllAttachments() {
            return new HashMap<>(attachments);
        }

        public void equipAttachment(int tier) {
            for (int slot = 0; slot < MAX_EQUIPPED_ATTACHMENTS; slot++) {
                if (!equipped.containsKey(slot)) {
                    equipped.put(slot, tier);
                    break;
                }
            }
        }

        public void unequipAttachment(int slot) {
            equipped.remove(slot);
        }

        public Integer getEquippedAttachment(int slot) {
            return equipped.get(slot);
        }

        public Collection<Integer> getEquippedAttachments() {
            return equipped.values();
        }

        public Map<Integer, Integer> getEquippedMap() {
            return new HashMap<>(equipped);
        }

        public int getEquippedCount() {
            return equipped.size();
        }

        public void setEquippedAttachment(int slot, Integer tier) {
            if (tier == null) {
                equipped.remove(slot);
            } else {
                equipped.put(slot, tier);
            }
        }
    }
}