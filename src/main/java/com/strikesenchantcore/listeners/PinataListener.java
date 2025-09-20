package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.util.ChatUtil;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
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
    private static final NamespacedKey PINATA_KEY = new NamespacedKey(EnchantCore.getInstance(), "enchantcore_pinata");

    // Map to store active piñatas: Entity UUID -> PinataData
    public static final Map<UUID, PinataData> activePinatas = new ConcurrentHashMap<>();

    public PinataListener(EnchantCore plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
        startTimeoutTask();
    }

    /**
     * Spawns a MythicMobs piñata that automatically applies the ModelEngine model.
     */
    public boolean spawnModelEnginePinata(Location location, UUID owner, ConfigurationSection rewardsSection,
                                          int health, int timeoutSeconds, String modelName) {

        if (hasActivePinata(owner)) {
            return false;
        }

        try {
            // Clear space
            clearSphereAroundLocation(location, 2);

            // Use ModelEngine's summon command to spawn the piñata
            String summonCommand = "meg summon " + modelName + " " +
                    location.getWorld().getName() + " " +
                    location.getX() + " " +
                    location.getY() + " " +
                    location.getZ();

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), summonCommand);

            // Wait a few ticks for the entity to spawn, then find it and mark it
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Find the newly spawned entity near the location
                Entity pinataEntity = null;
                for (Entity entity : location.getWorld().getNearbyEntities(location, 3, 3, 3)) {
                    // Look for the entity that was just spawned (should be the closest one)
                    if (entity.getLocation().distance(location) < 2.0) {
                        pinataEntity = entity;
                        break;
                    }
                }

                if (pinataEntity != null) {
                    // Configure the entity
                    pinataEntity.setPersistent(false);
                    pinataEntity.setCustomName("§6§lLoot Piñata");
                    pinataEntity.setCustomNameVisible(true);

                    // Mark it as our piñata
                    pinataEntity.getPersistentDataContainer().set(PINATA_KEY, PersistentDataType.BYTE, (byte) 1);

                    // Store data
                    PinataData pinataData = new PinataData(owner, health, rewardsSection, timeoutSeconds);
                    activePinatas.put(pinataEntity.getUniqueId(), pinataData);

                    plugin.getLogger().info("Successfully spawned and tracked ModelEngine piñata for " + Bukkit.getPlayer(owner));
                } else {
                    plugin.getLogger().warning("Failed to find spawned ModelEngine piñata entity for tracking");
                }
            }, 10L); // Wait 10 ticks (0.5 seconds) for entity to fully spawn

            // Play sounds and particles
            Player ownerPlayer = Bukkit.getPlayer(owner);
            if (ownerPlayer != null) {
                ownerPlayer.playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 2.0f, 2.0f);
                ownerPlayer.playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 2.0f, 1.5f);
                ownerPlayer.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.5f, 1.2f);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ownerPlayer.playSound(location, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.8f);
                }, 5L);

                for (Player nearbyPlayer : location.getWorld().getPlayers()) {
                    if (nearbyPlayer.getLocation().distance(location) <= 50 && !nearbyPlayer.equals(ownerPlayer)) {
                        nearbyPlayer.playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
                        nearbyPlayer.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f);
                    }
                }
            }

            // Spawn particles
            location.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, location.clone().add(0, 1, 0), 3);
            location.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, location.clone().add(0, 2, 0), 50, 2.0, 2.0, 2.0, 0.1);
            location.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, location.clone().add(0, 1, 0), 30, 1.5, 1.5, 1.5);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to spawn ModelEngine piñata: " + e.getMessage());
            return false;
        }
    }

    /**
     * Clears blocks in a sphere around the given location to make space for the piñata
     */
    private void clearSphereAroundLocation(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    // Check if point is within sphere
                    double distance = Math.sqrt(Math.pow(x - centerX, 4) + Math.pow(y - centerY, 6) + Math.pow(z - centerZ, 4));
                    if (distance <= radius) {
                        Location blockLoc = new Location(world, x, y, z);
                        Material blockType = blockLoc.getBlock().getType();

                        // Only clear breakable blocks, don't touch bedrock, air, etc.
                        if (blockType != Material.AIR && blockType != Material.BEDROCK &&
                                blockType != Material.BARRIER && !blockType.name().contains("COMMAND_BLOCK")) {
                            blockLoc.getBlock().setType(Material.AIR);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPinataHit(EntityDamageByEntityEvent event) {
        Entity damagedEntity = event.getEntity();

        // Check if this entity is one of our piñatas
        if (!damagedEntity.getPersistentDataContainer().has(PINATA_KEY, PersistentDataType.BYTE)) {
            return;
        }

        // Check if the damaged entity is in our active piñatas map
        if (!activePinatas.containsKey(damagedEntity.getUniqueId())) {
            return;
        }

        // Check if damage was caused by a player
        if (!(event.getDamager() instanceof Player)) {
            event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getDamager();
        PinataData pinataData = activePinatas.get(damagedEntity.getUniqueId());
        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());

        // Verify ownership and player data
        if (playerData == null || !player.getUniqueId().equals(pinataData.getOwner())) {
            event.setCancelled(true);
            return;
        }

        // Set damage to 0 to prevent the entity from dying naturally
        event.setDamage(0.0);

        // Give EnchantCore rewards
        boolean rewardGiven = tryGiveRewards(player, damagedEntity.getLocation(), pinataData.getRewardsSection());

        // Sound effects
        if (playerData.isShowEnchantSounds()) {
            if (rewardGiven) {
                player.playSound(damagedEntity.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
            } else {
                player.playSound(damagedEntity.getLocation(), Sound.BLOCK_STONE_HIT, 1.0f, 1.0f);
            }
        }

        // Spawn hit particles
        Location particleLocation = damagedEntity.getLocation().add(0, damagedEntity.getHeight() / 2, 0);
        damagedEntity.getWorld().spawnParticle(Particle.CRIT, particleLocation, 15, 0.4, 0.4, 0.4);

        // Reduce piñata health
        pinataData.reduceHealth();

        // Check if piñata should break
        if (pinataData.isBroken()) {
            breakPinata(damagedEntity, player, playerData);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPinataEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // Check if this is one of our piñatas
        if (entity.getPersistentDataContainer().has(PINATA_KEY, PersistentDataType.BYTE) &&
                activePinatas.containsKey(entity.getUniqueId())) {

            // Clear drops and XP (EntityDeathEvent cannot be cancelled)
            event.getDrops().clear();
            event.setDroppedExp(0);

            // Clean up if it somehow died naturally
            PinataData pinataData = activePinatas.remove(entity.getUniqueId());
            if (pinataData != null) {
                Player owner = Bukkit.getPlayer(pinataData.getOwner());
                if (owner != null && owner.isOnline()) {
                    PlayerData ownerData = dataManager.getPlayerData(owner.getUniqueId());
                    if (ownerData != null && ownerData.isShowEnchantMessages()) {
                        String timeoutMessage = plugin.getEnchantRegistry().getEnchant("lootpinata")
                                .getCustomSettings().getString("TimeoutMessage", "&eThe loot piñata got away...");
                        ChatUtil.sendMessage(owner, timeoutMessage);
                    }
                }
            }
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

    private void breakPinata(Entity pinataEntity, Player player, PlayerData playerData) {
        PinataData pinataData = activePinatas.remove(pinataEntity.getUniqueId());
        if (pinataData == null) return;

        Location location = pinataEntity.getLocation();

        // Remove the ModelEngine model first
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "meg remove " + pinataEntity.getUniqueId());

        // Then remove the entity
        pinataEntity.remove();

        // Spawn explosion effects
        location.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, location.clone().add(0, 1, 0), 1);

        if (playerData.isShowEnchantSounds()) {
            player.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        }

        // Optional: Send a break message
        if (playerData.isShowEnchantMessages()) {
            String breakMessage = plugin.getEnchantRegistry().getEnchant("lootpinata")
                    .getCustomSettings().getString("BreakMessage", "&a&lPiñata Broken! &eAll rewards collected!");
            ChatUtil.sendMessage(player, breakMessage);
        }
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

    private void handleTimeout(UUID entityUUID, PinataData data) {
        // Find the entity and trigger timeout
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(entityUUID)) {

                    // Remove ModelEngine model
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "meg remove " + entity.getUniqueId());

                    // Remove entity
                    entity.remove();

                    // Notify owner
                    Player owner = Bukkit.getPlayer(data.getOwner());
                    if (owner != null && owner.isOnline()) {
                        PlayerData ownerData = dataManager.getPlayerData(owner.getUniqueId());
                        if (ownerData != null && ownerData.isShowEnchantMessages()) {
                            String timeoutMessage = plugin.getEnchantRegistry().getEnchant("lootpinata")
                                    .getCustomSettings().getString("TimeoutMessage", "&eThe loot piñata got away...");
                            ChatUtil.sendMessage(owner, timeoutMessage);
                        }
                    }
                    break;
                }
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