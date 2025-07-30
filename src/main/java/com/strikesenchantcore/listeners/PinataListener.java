package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.util.ChatUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PinataListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager;
    private final Random random = ThreadLocalRandom.current();
    public static final Map<Location, PinataData> activePinatas = new ConcurrentHashMap<>();

    public PinataListener(EnchantCore plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
        startTimeoutTask();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPinataHit(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        if (!activePinatas.containsKey(location)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        PinataData pinataData = activePinatas.get(location);
        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());

        if (playerData == null || !player.getUniqueId().equals(pinataData.getOwner())) {
            return;
        }

        boolean rewardGiven = tryGiveRewards(player, location, pinataData.getRewardsSection());

        if (playerData.isShowEnchantSounds()) {
            if (rewardGiven) {
                player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
            } else {
                player.playSound(location, Sound.BLOCK_STONE_HIT, 1.0f, 1.0f);
            }
        }

        block.getWorld().spawnParticle(Particle.CRIT, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.4, 0.4, 0.4);

        pinataData.reduceHealth();

        if (pinataData.isBroken()) {
            breakPinata(location, player, playerData);
        }
    }

    private boolean tryGiveRewards(Player player, Location location, ConfigurationSection rewardsSection) {
        if (rewardsSection == null) return false;

        boolean wasRewardGiven = false;
        for (String key : rewardsSection.getKeys(false)) {
            ConfigurationSection reward = rewardsSection.getConfigurationSection(key);
            if (reward != null && random.nextDouble() < reward.getDouble("Chance", 0.0)) {
                distributeReward(player, location, reward);
                wasRewardGiven = true;
            }
        }
        return wasRewardGiven;
    }

    private void distributeReward(Player player, Location location, ConfigurationSection reward) {
        String type = reward.getString("Type", "").toUpperCase();
        String message = reward.getString("Message", "");
        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());

        // Check if player data exists before proceeding
        if (playerData == null) return;

        boolean showMessages = playerData.isShowEnchantMessages();

        if (type.equals("ITEM")) {
            Material material = Material.matchMaterial(reward.getString("Material", ""));
            if (material != null) {
                int amount = reward.getInt("Amount", 1);
                player.getWorld().dropItemNaturally(location, new ItemStack(material, amount));
                if (showMessages && !message.isEmpty()) ChatUtil.sendMessage(player, message);
            }
        } else if (type.equals("COMMAND")) {
            String command = reward.getString("Command", "").replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (showMessages && !message.isEmpty()) ChatUtil.sendMessage(player, message);
        }
    }

    private void breakPinata(Location location, Player player, PlayerData playerData) {
        PinataData pinataData = activePinatas.remove(location);
        if (pinataData == null) return;

        cleanupPinataBlock(location);
        location.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, location.clone().add(0.5, 0.5, 0.5), 1);

        if (playerData.isShowEnchantSounds()) {
            player.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPinataExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> activePinatas.containsKey(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPinataBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> activePinatas.containsKey(block.getLocation()));
    }

    private void cleanupPinataBlock(Location location) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (location.isWorldLoaded() && activePinatas.get(location) == null) {
                    location.getBlock().setType(Material.AIR);
                }
            }
        }.runTask(plugin);
    }

    private void startTimeoutTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activePinatas.isEmpty()) return;

                long currentTime = System.currentTimeMillis();
                activePinatas.entrySet().removeIf(entry -> {
                    PinataData data = entry.getValue();
                    if (currentTime > data.getTimeoutTimestamp()) {
                        handleTimeout(entry.getKey(), data);
                        return true;
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, 100L, 100L); // Check every 5 seconds
    }

    private void handleTimeout(Location location, PinataData data) {
        cleanupPinataBlock(location);

        location.getWorld().spawnParticle(Particle.SMOKE_NORMAL, location.clone().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3);
        Player owner = Bukkit.getPlayer(data.getOwner());
        if (owner != null && owner.isOnline()) {
            PlayerData ownerData = dataManager.getPlayerData(owner.getUniqueId());
            if (ownerData == null) return;

            if (ownerData.isShowEnchantSounds()) {
                owner.playSound(location, Sound.ENTITY_CHICKEN_HURT, 1.0f, 0.8f);
            }
            if (ownerData.isShowEnchantMessages()) {
                String timeoutMessage = plugin.getEnchantRegistry().getEnchant("lootpinata")
                        .getCustomSettings().getString("TimeoutMessage", "&eThe loot pinata got away...");
                ChatUtil.sendMessage(owner, timeoutMessage);
            }
        }
    }

    public static boolean hasActivePinata(UUID playerUUID) {
        return activePinatas.values().stream().anyMatch(data -> data.getOwner().equals(playerUUID));
    }

    public static class PinataData {
        private final UUID owner;
        private int health;
        private final ConfigurationSection rewardsSection;
        private final long timeoutTimestamp;

        public PinataData(UUID owner, int health, ConfigurationSection rewardsSection, int timeoutSeconds) {
            this.owner = owner;
            this.health = health;
            this.rewardsSection = rewardsSection;
            this.timeoutTimestamp = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        }

        public UUID getOwner() { return owner; }
        public void reduceHealth() { this.health--; }
        public boolean isBroken() { return this.health <= 0; }
        public ConfigurationSection getRewardsSection() { return rewardsSection; }
        public long getTimeoutTimestamp() { return timeoutTimestamp; }
    }
}