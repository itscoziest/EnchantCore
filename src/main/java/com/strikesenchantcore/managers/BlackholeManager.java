// --- ENTIRE CLASS - COPY AND PASTE ---
package com.strikesenchantcore.managers;

import com.strikesenchantcore.EnchantCore;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BlackholeManager {

    private final EnchantCore plugin;
    private final Map<UUID, BlackholeData> activeBlackholes;
    private final File blackholeDataFile;
    private FileConfiguration blackholeConfig;

    public BlackholeManager(EnchantCore plugin) {
        this.plugin = plugin;
        this.activeBlackholes = new ConcurrentHashMap<>();
        this.blackholeDataFile = new File(plugin.getDataFolder(), "blackholes.yml");

        loadBlackholeData();
    }

    public static class BlackholeData {
        private final UUID id;
        private final Location center;
        private final Set<Location> affectedBlocks;
        private final long creationTime;
        private final int radius;
        private final UUID playerUUID;

        public BlackholeData(UUID id, Location center, int radius, UUID playerUUID) {
            this.id = id;
            this.center = center;
            this.radius = radius;
            this.affectedBlocks = new HashSet<>();
            this.creationTime = System.currentTimeMillis();
            this.playerUUID = playerUUID;
        }

        public UUID getId() { return id; }
        public Location getCenter() { return center; }
        public Set<Location> getAffectedBlocks() { return affectedBlocks; }
        public long getCreationTime() { return creationTime; }
        public int getRadius() { return radius; }
        public UUID getPlayerUUID() { return playerUUID; }
    }

    public void registerBlackhole(UUID playerUUID, Location center, int radius) {
        UUID blackholeId = UUID.randomUUID();
        BlackholeData data = new BlackholeData(blackholeId, center, radius, playerUUID);

        generateSphere(center, radius, data.getAffectedBlocks());
        activeBlackholes.put(blackholeId, data);
        saveBlackholeData();

        plugin.getLogger().info("Registered blackhole for player " + playerUUID + " at " + locationToString(center));
    }

    public boolean removeBlackholeByLocation(Location location) {
        UUID toRemove = null;
        for (Map.Entry<UUID, BlackholeData> entry : activeBlackholes.entrySet()) {
            if (entry.getValue().getCenter().getWorld().equals(location.getWorld()) &&
                    entry.getValue().getCenter().distance(location) < 5.0) {
                toRemove = entry.getKey();
                break;
            }
        }

        if (toRemove != null) {
            activeBlackholes.remove(toRemove);
            saveBlackholeData();
            return true;
        }
        return false;
    }

    public void removeBlackholeByPlayer(UUID playerUUID) {
        activeBlackholes.values().removeIf(data -> data.getPlayerUUID().equals(playerUUID));
        saveBlackholeData();
    }


    public void cleanupAllBlackholes() {
        plugin.getLogger().info("Cleaning up " + activeBlackholes.size() + " active blackholes (coal spheres)...");

        for (BlackholeData data : activeBlackholes.values()) {
            restoreBlocks(data);
        }

        activeBlackholes.clear();
        saveBlackholeData();
        plugin.getLogger().info("All blackhole spheres cleaned up successfully.");
    }

    /**
     * NEW METHOD: This is the failsafe cleanup for orphaned armor stands.
     * It scans all worlds for armor stands with the specific plugin tag and removes them.
     */
    public void cleanupOrphanedArmorStands() {
        int removedCount = 0;
        // This key MUST match the one used in BlockBreakListener.java
        NamespacedKey blackholeKey = new NamespacedKey(plugin, "blackhole_armor_stand");

        plugin.getLogger().info("Starting sweep for orphaned Blackhole armor stands...");

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                // Check if it's an armor stand and if it has our specific tag
                if (entity instanceof org.bukkit.entity.ArmorStand && entity.getPersistentDataContainer().has(blackholeKey, PersistentDataType.BYTE)) {
                    entity.remove();
                    removedCount++;
                }
            }
        }

        if (removedCount > 0) {
            plugin.getLogger().info("Removed " + removedCount + " orphaned Blackhole armor stands.");
        } else {
            plugin.getLogger().info("No orphaned Blackhole armor stands found.");
        }
    }

    private void generateSphere(Location center, int radius, Set<Location> blocks) {
        World world = center.getWorld();
        if (world == null) return;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    double distance = Math.sqrt(
                            Math.pow(x - centerX, 2) +
                                    Math.pow(y - centerY, 2) +
                                    Math.pow(z - centerZ, 2)
                    );

                    if (distance <= radius) {
                        Location loc = new Location(world, x, y, z);
                        blocks.add(loc);
                    }
                }
            }
        }
    }

    private void restoreBlocks(BlackholeData data) {
        for (Location loc : data.getAffectedBlocks()) {
            if (loc.getWorld() == null) continue;

            Block block = loc.getBlock();
            if (block.getType() == Material.COAL_BLOCK) {
                block.setType(Material.AIR);
            }
        }
    }

    private void loadBlackholeData() {
        if (!blackholeDataFile.exists()) {
            try {
                blackholeDataFile.getParentFile().mkdirs();
                blackholeDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create blackhole data file", e);
                return;
            }
        }

        blackholeConfig = YamlConfiguration.loadConfiguration(blackholeDataFile);

        if (blackholeConfig.contains("blackholes")) {
            Set<String> keys = blackholeConfig.getConfigurationSection("blackholes").getKeys(false);

            for (String key : keys) {
                try {
                    UUID id = UUID.fromString(key);
                    String worldName = blackholeConfig.getString("blackholes." + key + ".world");
                    double x = blackholeConfig.getDouble("blackholes." + key + ".x");
                    double y = blackholeConfig.getDouble("blackholes." + key + ".y");
                    double z = blackholeConfig.getDouble("blackholes." + key + ".z");
                    int radius = blackholeConfig.getInt("blackholes." + key + ".radius");
                    UUID playerUUID = UUID.fromString(blackholeConfig.getString("blackholes." + key + ".player_uuid"));

                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        Location center = new Location(world, x, y, z);
                        BlackholeData data = new BlackholeData(id, center, radius, playerUUID);

                        generateSphere(center, radius, data.getAffectedBlocks());
                        restoreBlocks(data);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load blackhole data for key: " + key, e);
                }
            }

            activeBlackholes.clear();
            saveBlackholeData();

            plugin.getLogger().info("Cleaned up " + keys.size() + " blackholes from previous session");
        }
    }

    private void saveBlackholeData() {
        try {
            blackholeConfig.set("blackholes", null);

            for (Map.Entry<UUID, BlackholeData> entry : activeBlackholes.entrySet()) {
                UUID id = entry.getKey();
                BlackholeData data = entry.getValue();
                Location center = data.getCenter();

                String path = "blackholes." + id.toString();
                blackholeConfig.set(path + ".world", center.getWorld().getName());
                blackholeConfig.set(path + ".x", center.getX());
                blackholeConfig.set(path + ".y", center.getY());
                blackholeConfig.set(path + ".radius", data.getRadius());
                blackholeConfig.set(path + ".created", data.getCreationTime());
                blackholeConfig.set(path + ".player_uuid", data.getPlayerUUID().toString());
            }

            blackholeConfig.save(blackholeDataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save blackhole data", e);
        }
    }

    private String locationToString(Location loc) {
        return String.format("%s:%.1f,%.1f,%.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }
}