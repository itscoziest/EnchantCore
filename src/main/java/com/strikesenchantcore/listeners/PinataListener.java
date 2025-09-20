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
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.World;

// ModelEngine imports
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

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
     * Spawns a ModelEngine piñata at the specified location.
     * Uses a pig entity with ModelEngine model applied.
     */
    public boolean spawnModelEnginePinata(Location location, UUID owner, ConfigurationSection rewardsSection,
                                          int health, int timeoutSeconds, String modelName) {

        // Check if player already has an active piñata
        if (hasActivePinata(owner)) {
            return false;
        }

        try {
            // Spawn a pig entity
            Pig pinataEntity = (Pig) location.getWorld().spawnEntity(location, EntityType.PIG);

            // Configure the pig
            pinataEntity.setPersistent(false);
            pinataEntity.setAI(false); // Disable AI so it doesn't move
            pinataEntity.setInvulnerable(false); // Allow damage
            pinataEntity.setSilent(true); // No pig sounds
            pinataEntity.setCustomName("§6§lLoot Piñata");
            pinataEntity.setCustomNameVisible(true);

            // Make the pig invisible so only the ModelEngine model shows
            pinataEntity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE,
                    0,
                    false,
                    false
            ));

            // Mark it as our piñata using PDC
            pinataEntity.getPersistentDataContainer().set(PINATA_KEY, PersistentDataType.BYTE, (byte) 1);

            // Apply ModelEngine model using API instead of commands
            boolean modelApplied = applyModelEngineModel(pinataEntity, modelName);

            if (!modelApplied) {
                plugin.getLogger().warning("Failed to apply ModelEngine model '" + modelName + "' to piñata entity");
                pinataEntity.remove(); // Clean up the entity
                return false;
            }

            // Create piñata data and store it
            PinataData pinataData = new PinataData(owner, health, rewardsSection, timeoutSeconds);
            activePinatas.put(pinataEntity.getUniqueId(), pinataData);

            // Clear blocks immediately in 4x6x4 area
            World world = location.getWorld();
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            // Clear 4x6x4 area immediately
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 6; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                        if (block.getType() != Material.AIR && block.getType() != Material.BEDROCK) {
                            block.setType(Material.AIR);
                        }
                    }
                }
            }

            // Play configurable sound
            Player ownerPlayer = Bukkit.getPlayer(owner);
            if (ownerPlayer != null) {
                PlayerData playerData = dataManager.getPlayerData(owner);
                if (playerData != null && playerData.isShowEnchantSounds()) {
                    // Get sound settings from lootpinata config
                    ConfigurationSection lootpinataConfig = plugin.getEnchantRegistry().getEnchant("lootpinata").getCustomSettings();

                    if (lootpinataConfig != null) {
                        // Read sound configuration
                        String soundName = lootpinataConfig.getString("SpawnSound", "ENTITY_GENERIC_EXPLODE");
                        float volume = (float) lootpinataConfig.getDouble("SpawnSoundVolume", 1.0);
                        float pitch = (float) lootpinataConfig.getDouble("SpawnSoundPitch", 0.8);

                        plugin.getLogger().info("[DEBUG] Reading sound config: " + soundName + " vol:" + volume + " pitch:" + pitch);

                        // Play the sound
                        try {
                            Sound sound = Sound.valueOf(soundName);
                            ownerPlayer.playSound(location, sound, volume, pitch);
                            plugin.getLogger().info("[DEBUG] Successfully played sound: " + soundName);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid sound name in lootpinata configuration: " + soundName);
                            // Fallback to default sound
                            ownerPlayer.playSound(location, Sound.BLOCK_NETHER_SPROUTS_BREAK, 1.0f, 0.8f);
                        }
                    } else {
                        plugin.getLogger().warning("[DEBUG] lootpinataConfig is null!");
                        // Fallback to default sound if config is missing
                        ownerPlayer.playSound(location, Sound.BLOCK_NETHER_SPROUTS_BREAK, 1.0f, 0.8f);
                    }
                }

                // Particles
                world.spawnParticle(Particle.EXPLOSION_LARGE, location.clone().add(0, 1, 0), 3);
                world.spawnParticle(Particle.FIREWORKS_SPARK, location.clone().add(0, 1, 0), 20, 2.0, 2.0, 2.0);
            }

            plugin.getLogger().info("Spawned ModelEngine piñata for player " + Bukkit.getPlayer(owner) +
                    " at " + location + " with " + health + " health");

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to spawn ModelEngine piñata: " + e.getMessage());
            return false;
        }
    }

    /**
     * Applies a ModelEngine model to an entity using the proper API
     */
    private boolean applyModelEngineModel(Entity entity, String modelName) {
        try {
            // Check if the model exists
            if (ModelEngineAPI.getBlueprint(modelName) == null) {
                plugin.getLogger().warning("ModelEngine model '" + modelName + "' does not exist!");
                return false;
            }

            // Create a modeled entity
            ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(entity);
            if (modeledEntity == null) {
                plugin.getLogger().warning("Failed to create ModeledEntity for piñata");
                return false;
            }

            // Create and add the active model
            ActiveModel activeModel = ModelEngineAPI.createActiveModel(modelName);
            if (activeModel == null) {
                plugin.getLogger().warning("Failed to create ActiveModel for model '" + modelName + "'");
                return false;
            }

            // Add the model to the entity
            modeledEntity.addModel(activeModel, true);

            plugin.getLogger().info("Successfully applied ModelEngine model '" + modelName + "' to piñata entity");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error applying ModelEngine model '" + modelName + "': " + e.getMessage());
            e.printStackTrace();
            return false;
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

        // Remove the ModelEngine model using proper API
        removeModelEngineModel(pinataEntity);

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

    private void removeModelEngineModel(Entity entity) {
        try {
            ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(entity);
            if (modeledEntity != null) {
                modeledEntity.destroy();
                plugin.getLogger().info("Successfully removed ModelEngine model from piñata entity");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error removing ModelEngine model: " + e.getMessage());
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

                    // Remove ModelEngine model using proper API
                    removeModelEngineModel(entity);

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