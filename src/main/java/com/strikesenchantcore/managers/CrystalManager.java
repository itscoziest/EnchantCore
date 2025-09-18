package com.strikesenchantcore.managers;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.util.ChatUtil;
import org.bukkit.entity.Player;

import java.util.*;

public class CrystalManager {
    private final EnchantCore plugin;
    private final PlayerDataManager playerDataManager;
    private final Map<String, Double> crystalMultipliers;
    // --- UPDATED: Changed max slots from 6 to 7 ---
    private final int maxEquippedSlots = 7;

    public CrystalManager(EnchantCore plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.crystalMultipliers = new HashMap<>();

        crystalMultipliers.put("TOKEN", 0.05);
        crystalMultipliers.put("GEM", 0.04);
        crystalMultipliers.put("PROC", 0.03);
        crystalMultipliers.put("RANK", 0.06);
        crystalMultipliers.put("PICKAXE_XP", 0.08);
        crystalMultipliers.put("PET", 0.05);
        crystalMultipliers.put("SALVAGE", 0.07);
    }

    public void giveCrystal(Player player, String type, int tier, int amount) {
        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return;

        String key = type.toUpperCase() + "_" + tier;
        Map<String, Integer> storage = playerData.getCrystalStorage();

        storage.put(key, storage.getOrDefault(key, 0) + amount);
        playerDataManager.savePlayerData(playerData, true);

        ChatUtil.sendMessage(player, "&aYou received &e" + amount + "&ax &b" +
                getDisplayName(type) + " &aTier &e" + tier + " &acrystal(s)!");
    }

    public boolean equipCrystal(Player player, String type, int tier, int slot) {
        if (slot < 0 || slot >= maxEquippedSlots) return false;

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        String key = type.toUpperCase() + "_" + tier;
        Map<String, Integer> storage = playerData.getCrystalStorage();
        Map<Integer, String> equipped = playerData.getEquippedCrystals();

        if (storage.getOrDefault(key, 0) <= 0) return false;

        storage.put(key, storage.get(key) - 1);
        if (storage.get(key) <= 0) {
            storage.remove(key);
        }

        if (equipped.containsKey(slot)) {
            String oldKey = equipped.get(slot);
            storage.put(oldKey, storage.getOrDefault(oldKey, 0) + 1);
        }

        equipped.put(slot, key);
        playerDataManager.savePlayerData(playerData, true);
        return true;
    }

    public boolean unequipCrystal(Player player, int slot) {
        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        Map<String, Integer> storage = playerData.getCrystalStorage();
        Map<Integer, String> equipped = playerData.getEquippedCrystals();

        if (!equipped.containsKey(slot)) return false;

        String key = equipped.remove(slot);
        storage.put(key, storage.getOrDefault(key, 0) + 1);

        playerDataManager.savePlayerData(playerData, true);
        return true;
    }

    public boolean mergeCrystals(Player player, String type, int tier) {
        if (tier >= 10) return false;

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        String currentKey = type.toUpperCase() + "_" + tier;
        String nextKey = type.toUpperCase() + "_" + (tier + 1);
        Map<String, Integer> storage = playerData.getCrystalStorage();

        if (storage.getOrDefault(currentKey, 0) < 3) return false;

        storage.put(currentKey, storage.get(currentKey) - 3);
        if (storage.get(currentKey) <= 0) {
            storage.remove(currentKey);
        }

        storage.put(nextKey, storage.getOrDefault(nextKey, 0) + 1);

        playerDataManager.savePlayerData(playerData, true);
        ChatUtil.sendMessage(player, "&aSuccessfully merged 3x &b" + getDisplayName(type) +
                " &aTier &e" + tier + " &ainto 1x &b" + getDisplayName(type) + " &aTier &e" + (tier + 1) + "&a!");
        return true;
    }

    public double getMultiplier(Player player, String type) {
        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return 0.0;

        Map<Integer, String> equipped = playerData.getEquippedCrystals();
        double totalBonus = 0.0;
        double baseMultiplier = crystalMultipliers.getOrDefault(type.toUpperCase(), 0.0);

        for (String crystalKey : equipped.values()) {
            if (crystalKey.startsWith(type.toUpperCase() + "_")) {
                try {
                    int tier = Integer.parseInt(crystalKey.split("_")[1]);
                    totalBonus += baseMultiplier * tier;
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    // Ignored
                }
            }
        }
        return totalBonus;
    }

    public Map<String, Integer> getStorageCrystals(Player player) {
        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        return playerData != null ? playerData.getCrystalStorage() : new HashMap<>();
    }

    public Map<Integer, String> getEquippedCrystals(Player player) {
        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        return playerData != null ? playerData.getEquippedCrystals() : new HashMap<>();
    }

    public String getDisplayName(String type) {
        switch (type.toUpperCase()) {
            case "TOKEN": return "Token";
            case "GEM": return "Gem";
            case "PROC": return "Proc";
            case "RANK": return "Rank";
            case "PICKAXE_XP": return "Pickaxe XP";
            case "PET": return "Pet";
            case "SALVAGE": return "Salvage";
            default: return type;
        }
    }

    public String getColor(String type) {
        switch (type.toUpperCase()) {
            case "TOKEN": return "&e";
            case "GEM": return "&b";
            case "PROC": return "&d";
            case "RANK": return "&a";
            case "PICKAXE_XP": return "&6";
            case "PET": return "&9";
            case "SALVAGE": return "&c";
            default: return "&7";
        }
    }

    public String getTierColor(int tier) {
        if (tier <= 3) return "&f";
        else if (tier <= 6) return "&e";
        else if (tier <= 9) return "&6";
        else return "&c";
    }

    public boolean isValidCrystalType(String type) {
        return crystalMultipliers.containsKey(type.toUpperCase());
    }

    public Set<String> getValidCrystalTypes() {
        return crystalMultipliers.keySet();
    }

    public double getTokenMultiplier(Player player) {
        return getMultiplier(player, "TOKEN");
    }

    public double getGemMultiplier(Player player) {
        return getMultiplier(player, "GEM");
    }

    public double getProcMultiplier(Player player) {
        return getMultiplier(player, "PROC");
    }

    public double getRankMultiplier(Player player) {
        return getMultiplier(player, "RANK");
    }

    public double getPickaxeXpMultiplier(Player player) {
        return getMultiplier(player, "PICKAXE_XP");
    }

    public double getPetMultiplier(Player player) {
        return getMultiplier(player, "PET");
    }

    public double getSalvageMultiplier(Player player) {
        return getMultiplier(player, "SALVAGE");
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public double getBaseMultiplier(String type) {
        return crystalMultipliers.getOrDefault(type.toUpperCase(), 0.0);
    }
}