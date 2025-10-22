package com.strikesenchantcore.managers;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.PDCUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MortarManager {

    private final EnchantCore plugin;
    private final PlayerDataManager playerDataManager;
    private final Map<UUID, Long> mortarCooldowns;

    public MortarManager(EnchantCore plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.mortarCooldowns = new HashMap<>();

        // Start the mortar activation task
        startMortarTask();
    }

    private void startMortarTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkMortarActivation(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second
    }

    private void checkMortarActivation(Player player) {
        UUID playerId = player.getUniqueId();
        MortarData mortarData = getMortarData(playerId);

        if (mortarData.getLevel() == 0) return; // No mortar

        // Check if cooldown is over
        if (isOnCooldown(playerId)) return;

        // Random chance to activate based on level (higher level = more frequent)
        double activationChance = 0.001 * mortarData.getLevel(); // 0.1% per level per second
        if (ThreadLocalRandom.current().nextDouble() > activationChance) return;

        // Check Lightning Strike upgrade for double activation
        if (mortarData.hasUpgrade(MortarUpgrade.LIGHTNING_STRIKE)) {
            double doubleChance = 0.01 + (0.005 * mortarData.getUpgradeLevel(MortarUpgrade.LIGHTNING_STRIKE));
            if (ThreadLocalRandom.current().nextDouble() < doubleChance) {
                activateMortar(player, true); // Double activation
                return;
            }
        }

        activateMortar(player, false);
    }

    public void activateMortar(Player player, boolean isDouble) {
        UUID playerId = player.getUniqueId();
        MortarData mortarData = getMortarData(playerId);
        PlayerData playerData = playerDataManager.getPlayerData(playerId);

        if (mortarData.getLevel() == 0 || playerData == null) return;

        // Get player's pickaxe
        ItemStack pickaxe = plugin.getPickaxeManager().findPickaxe(player);
        if (pickaxe == null) return;

        // Get 3 random enchants from the pickaxe
        List<EnchantmentWrapper> availableEnchants = getPickaxeEnchants(pickaxe);
        if (availableEnchants.isEmpty()) return;

        // Apply Selective Fire upgrade to favor high-tier enchants
        if (mortarData.hasUpgrade(MortarUpgrade.SELECTIVE_FIRE)) {
            availableEnchants = filterHighTierEnchants(availableEnchants, mortarData.getUpgradeLevel(MortarUpgrade.SELECTIVE_FIRE));
        }

        Collections.shuffle(availableEnchants);
        List<EnchantmentWrapper> selectedEnchants = availableEnchants.subList(0, Math.min(3, availableEnchants.size()));

        // Calculate boost multiplier
        double baseMultiplier = 1.5 + (0.1 * mortarData.getLevel());
        if (mortarData.hasUpgrade(MortarUpgrade.MULTIPLIER)) {
            baseMultiplier += 0.2 * mortarData.getUpgradeLevel(MortarUpgrade.MULTIPLIER);
        }

        // Apply 10-second boost
        long boostDuration = 10000; // 10 seconds in milliseconds
        mortarData.setActiveBoost(System.currentTimeMillis() + boostDuration, baseMultiplier);

        // Set cooldown
        long cooldown = calculateCooldown(mortarData);
        mortarCooldowns.put(playerId, System.currentTimeMillis() + cooldown);

        // Trigger the selected enchants (simulation)
        for (EnchantmentWrapper enchant : selectedEnchants) {
            // Here you would trigger the actual enchant effects
            // This is a placeholder for the actual enchant triggering logic
        }

        // Send feedback to player
        ChatUtil.sendMessage(player, "&6&lMORTAR ACTIVATED! &e" + selectedEnchants.size() + " enchants triggered!");
        ChatUtil.sendMessage(player, "&eBoost: &6" + String.format("%.1f", baseMultiplier) + "x &efor 10 seconds!");

        if (isDouble) {
            ChatUtil.sendMessage(player, "&d&lLIGHTNING STRIKE! &eDouble activation!");
        }

        // Update last activation time
        mortarData.setLastActivation(System.currentTimeMillis());
    }

    private List<EnchantmentWrapper> getPickaxeEnchants(ItemStack pickaxe) {
        List<EnchantmentWrapper> enchants = new ArrayList<>();
        Map<String, Integer> pickaxeEnchants = plugin.getPickaxeManager().getAllEnchantLevels(pickaxe);

        for (Map.Entry<String, Integer> entry : pickaxeEnchants.entrySet()) {
            if (entry.getValue() > 0) {
                EnchantmentWrapper enchant = plugin.getEnchantRegistry().getEnchant(entry.getKey());
                if (enchant != null && enchant.isEnabled()) {
                    enchants.add(enchant);
                }
            }
        }

        return enchants;
    }

    private List<EnchantmentWrapper> filterHighTierEnchants(List<EnchantmentWrapper> enchants, int selectiveFireLevel) {
        if (selectiveFireLevel == 0) return enchants;

        // Sort enchants by "tier" (using max level as a proxy for tier)
        enchants.sort((a, b) -> Integer.compare(b.getMaxLevel(), a.getMaxLevel()));

        // Increase chance of selecting higher tier enchants
        double highTierChance = 0.3 + (0.1 * selectiveFireLevel);
        List<EnchantmentWrapper> filtered = new ArrayList<>();

        for (EnchantmentWrapper enchant : enchants) {
            if (enchant.getMaxLevel() > 100 && ThreadLocalRandom.current().nextDouble() < highTierChance) {
                filtered.add(enchant);
            } else if (enchant.getMaxLevel() <= 100) {
                filtered.add(enchant);
            }
        }

        return filtered.isEmpty() ? enchants : filtered;
    }

    private long calculateCooldown(MortarData mortarData) {
        long baseCooldown = 120000 - (5000 * mortarData.getLevel()); // Base 120s, -5s per level
        baseCooldown = Math.max(30000, baseCooldown); // Minimum 30 seconds

        if (mortarData.hasUpgrade(MortarUpgrade.COOLDOWN_CONDENSER)) {
            long reduction = 1000 + (1000 * mortarData.getUpgradeLevel(MortarUpgrade.COOLDOWN_CONDENSER));
            baseCooldown -= reduction;
        }

        return Math.max(10000, baseCooldown); // Absolute minimum 10 seconds
    }

    public boolean canUpgradeMortar(Player player) {
        UUID playerId = player.getUniqueId();
        MortarData mortarData = getMortarData(playerId);
        PlayerData playerData = playerDataManager.getPlayerData(playerId);

        if (mortarData.getLevel() >= 10 || playerData == null) return false;

        // Check requirements
        MortarUpgradeRequirement req = getMortarUpgradeRequirement(mortarData.getLevel() + 1);

        ItemStack pickaxe = plugin.getPickaxeManager().findPickaxe(player);
        if (pickaxe == null) return false;

        int pickaxeLevel = PDCUtil.getPickaxeLevel(pickaxe);
        long blocksMined = PDCUtil.getPickaxeBlocksMined(pickaxe);

        return pickaxeLevel >= req.getRequiredPickaxeLevel() &&
                blocksMined >= req.getRequiredBlocksMined() &&
                playerData.getTokens() >= req.getRequiredTokens();
    }

    public boolean upgradeMortar(Player player) {
        if (!canUpgradeMortar(player)) return false;

        UUID playerId = player.getUniqueId();
        MortarData mortarData = getMortarData(playerId);
        PlayerData playerData = playerDataManager.getPlayerData(playerId);

        MortarUpgradeRequirement req = getMortarUpgradeRequirement(mortarData.getLevel() + 1);

        // Deduct tokens
        if (!playerData.removeTokens(req.getRequiredTokens())) return false;

        // Upgrade mortar
        mortarData.setLevel(mortarData.getLevel() + 1);

        ChatUtil.sendMessage(player, "&6&lMORTAR UPGRADED! &eLevel: &6" + mortarData.getLevel());

        return true;
    }

    public boolean canUpgradeUpgrade(Player player, MortarUpgrade upgrade) {
        UUID playerId = player.getUniqueId();
        MortarData mortarData = getMortarData(playerId);
        PlayerData playerData = playerDataManager.getPlayerData(playerId);

        if (mortarData.getLevel() == 0 || playerData == null) return false;

        int currentLevel = mortarData.getUpgradeLevel(upgrade);
        if (currentLevel >= upgrade.getMaxLevel()) return false;

        long cost = upgrade.getUpgradeCost(currentLevel + 1);
        return playerData.getTokens() >= cost;
    }

    public boolean upgradeUpgrade(Player player, MortarUpgrade upgrade) {
        if (!canUpgradeUpgrade(player, upgrade)) return false;

        UUID playerId = player.getUniqueId();
        MortarData mortarData = getMortarData(playerId);
        PlayerData playerData = playerDataManager.getPlayerData(playerId);

        int currentLevel = mortarData.getUpgradeLevel(upgrade);
        long cost = upgrade.getUpgradeCost(currentLevel + 1);

        if (!playerData.removeTokens(cost)) return false;

        mortarData.setUpgradeLevel(upgrade, currentLevel + 1);

        ChatUtil.sendMessage(player, "&6&l" + upgrade.getDisplayName() + " UPGRADED! &eLevel: &6" + (currentLevel + 1));

        return true;
    }

    public MortarData getMortarData(UUID playerId) {
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData != null) {
            return playerData.getMortarData();
        }
        // Fallback to prevent NullPointerException. This should ideally never be reached
        // if PlayerData is loaded correctly for every online player.
        return new MortarData();
    }

    public boolean isOnCooldown(UUID playerId) {
        Long cooldownEnd = mortarCooldowns.get(playerId);
        if (cooldownEnd == null) return false;

        if (System.currentTimeMillis() >= cooldownEnd) {
            mortarCooldowns.remove(playerId);
            return false;
        }

        return true;
    }

    public long getCooldownRemaining(UUID playerId) {
        Long cooldownEnd = mortarCooldowns.get(playerId);
        if (cooldownEnd == null) return 0;

        return Math.max(0, cooldownEnd - System.currentTimeMillis());
    }

    public boolean hasActiveBoost(UUID playerId) {
        MortarData mortarData = getMortarData(playerId);
        return mortarData.getBoostEndTime() > System.currentTimeMillis();
    }

    public double getActiveBoostMultiplier(UUID playerId) {
        if (!hasActiveBoost(playerId)) return 1.0;
        return getMortarData(playerId).getBoostMultiplier();
    }

    private MortarUpgradeRequirement getMortarUpgradeRequirement(int level) {
        // Define requirements for each level
        return switch (level) {
            case 1 -> new MortarUpgradeRequirement(5, 1000, 5000);
            case 2 -> new MortarUpgradeRequirement(10, 5000, 15000);
            case 3 -> new MortarUpgradeRequirement(15, 15000, 35000);
            case 4 -> new MortarUpgradeRequirement(20, 35000, 75000);
            case 5 -> new MortarUpgradeRequirement(25, 75000, 150000);
            case 6 -> new MortarUpgradeRequirement(30, 150000, 300000);
            case 7 -> new MortarUpgradeRequirement(35, 300000, 600000);
            case 8 -> new MortarUpgradeRequirement(40, 600000, 1200000);
            case 9 -> new MortarUpgradeRequirement(45, 1200000, 2500000);
            case 10 -> new MortarUpgradeRequirement(50, 2500000, 5000000);
            default -> new MortarUpgradeRequirement(Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        };
    }

    // Inner classes
    public static class MortarData {
        private int level = 0;
        private long lastActivation = 0;
        private long boostEndTime = 0;
        private double boostMultiplier = 1.0;
        private final Map<MortarUpgrade, Integer> upgradeLevels = new HashMap<>();

        public boolean hasMortarBoost() {
            return boostEndTime > System.currentTimeMillis();
        }

        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }

        public long getLastActivation() { return lastActivation; }
        public void setLastActivation(long lastActivation) { this.lastActivation = lastActivation; }

        public long getBoostEndTime() { return boostEndTime; }
        public double getBoostMultiplier() { return boostMultiplier; }

        public void setActiveBoost(long endTime, double multiplier) {
            this.boostEndTime = endTime;
            this.boostMultiplier = multiplier;
        }

        public boolean hasUpgrade(MortarUpgrade upgrade) {
            return upgradeLevels.getOrDefault(upgrade, 0) > 0;
        }

        public int getUpgradeLevel(MortarUpgrade upgrade) {
            return upgradeLevels.getOrDefault(upgrade, 0);
        }

        public void setUpgradeLevel(MortarUpgrade upgrade, int level) {
            upgradeLevels.put(upgrade, level);
        }
    }

    public static class MortarUpgradeRequirement {
        private final int requiredPickaxeLevel;
        private final long requiredBlocksMined;
        private final long requiredTokens;

        public MortarUpgradeRequirement(int requiredPickaxeLevel, long requiredBlocksMined, long requiredTokens) {
            this.requiredPickaxeLevel = requiredPickaxeLevel;
            this.requiredBlocksMined = requiredBlocksMined;
            this.requiredTokens = requiredTokens;
        }

        public int getRequiredPickaxeLevel() { return requiredPickaxeLevel; }
        public long getRequiredBlocksMined() { return requiredBlocksMined; }
        public long getRequiredTokens() { return requiredTokens; }
    }

    public enum MortarUpgrade {
        MULTIPLIER("Multiplier", 10, level -> 10000L * level * level),
        COOLDOWN_CONDENSER("Cooldown Condenser", 5, level -> 25000L * level),
        LIGHTNING_STRIKE("Lightning Strike", 5, level -> 50000L * level),
        SELECTIVE_FIRE("Selective Fire", 5, level -> 75000L * level);

        private final String displayName;
        private final int maxLevel;
        private final java.util.function.Function<Integer, Long> costFunction;

        MortarUpgrade(String displayName, int maxLevel, java.util.function.Function<Integer, Long> costFunction) {
            this.displayName = displayName;
            this.maxLevel = maxLevel;
            this.costFunction = costFunction;
        }

        public String getDisplayName() { return displayName; }
        public int getMaxLevel() { return maxLevel; }
        public long getUpgradeCost(int level) { return costFunction.apply(level); }
    }
}