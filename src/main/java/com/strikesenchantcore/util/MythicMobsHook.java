package com.strikesenchantcore.util;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Utility class for MythicMobs integration.
 * Handles spawning and managing MythicMobs entities for EnchantCore.
 */
public class MythicMobsHook {

    private final Plugin plugin;
    private final Logger logger;
    private boolean enabled;

    public MythicMobsHook(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = false;

        try {
            Plugin mythicMobs = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
            if (mythicMobs != null && mythicMobs.isEnabled()) {
                this.enabled = true;
                logger.info("MythicMobs integration enabled!");
            } else {
                logger.warning("MythicMobs not found! Lootbox piñata feature will not work.");
            }
        } catch (Exception e) {
            logger.severe("Failed to initialize MythicMobs integration: " + e.getMessage());
            this.enabled = false;
        }
    }

    /**
     * Checks if MythicMobs is available and enabled.
     * @return true if MythicMobs is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Spawns a MythicMobs entity at the specified location.
     * @param mobType The internal name of the MythicMobs mob (e.g., "pinata")
     * @param location The location to spawn the mob
     * @param level The level of the mob (optional, can be 1)
     * @return The spawned Entity, or null if failed
     */
    public Entity spawnMythicMob(String mobType, Location location, int level) {
        if (!isEnabled()) {
            logger.warning("Cannot spawn MythicMob: MythicMobs is not enabled!");
            return null;
        }

        try {
            // Get the MythicMob type
            ActiveMob activeMob = MythicBukkit.inst().getMobManager().spawnMob(mobType, location, level);
            if (activeMob != null) {
                Entity entity = activeMob.getEntity().getBukkitEntity();
                logger.info("Successfully spawned MythicMob '" + mobType + "' at " + location);
                return entity;
            } else {
                logger.warning("Failed to spawn MythicMob '" + mobType + "' - spawn returned null!");
                return null;
            }
        } catch (Exception e) {
            logger.severe("Error spawning MythicMob '" + mobType + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if an entity is a MythicMobs entity.
     * @param entity The entity to check
     * @return true if it's a MythicMobs entity
     */
    public boolean isMythicMob(Entity entity) {
        if (!isEnabled() || entity == null) {
            return false;
        }

        try {
            return MythicBukkit.inst().getMobManager().isActiveMob(entity.getUniqueId());
        } catch (Exception e) {
            logger.warning("Error checking if entity is MythicMob: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the MythicMobs internal name for an entity.
     * @param entity The entity to check
     * @return The internal name, or null if not a MythicMob
     */
    public String getMythicMobType(Entity entity) {
        if (!isEnabled() || entity == null) {
            return null;
        }

        try {
            if (isMythicMob(entity)) {
                Optional<ActiveMob> activeMobOptional = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
                if (activeMobOptional.isPresent()) {
                    return activeMobOptional.get().getMobType();
                }
            }
        } catch (Exception e) {
            logger.warning("Error getting MythicMob type: " + e.getMessage());
        }
        return null;
    }

    /**
     * Removes a MythicMobs entity.
     * @param entity The entity to remove
     * @return true if successfully removed
     */
    public boolean removeMythicMob(Entity entity) {
        if (!isEnabled() || entity == null) {
            return false;
        }

        try {
            if (isMythicMob(entity)) {
                entity.remove();
                return true;
            }
        } catch (Exception e) {
            logger.warning("Error removing MythicMob: " + e.getMessage());
        }
        return false;
    }

    /**
     * Gets the ActiveMob instance for a Bukkit entity.
     * @param entity The Bukkit entity
     * @return The ActiveMob instance, or null if not a MythicMob
     */
    public ActiveMob getActiveMob(Entity entity) {
        if (!isEnabled() || entity == null) {
            return null;
        }

        try {
            Optional<ActiveMob> activeMobOptional = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
            return activeMobOptional.orElse(null);
        } catch (Exception e) {
            logger.warning("Error getting ActiveMob instance: " + e.getMessage());
            return null;
        }
    }
}