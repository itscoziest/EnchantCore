package com.strikesenchantcore.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag; // Import Flag base class
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.strikesenchantcore.EnchantCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull; // Import NotNull
import org.jetbrains.annotations.Nullable;

import java.util.Collections; // Import Collections for emptySet
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger

/**
 * Handles integration with the WorldGuard API for region flag checks.
 * Registers and uses the custom 'enchantcore-effects' StateFlag.
 */
public class WorldGuardHook {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    private WorldGuard worldGuardInstance = null; // WG Instance
    private boolean enabled = false; // Hook status

    // The custom flag instance - public static to be accessible during onLoad registration
    @Nullable // Can be null if registration fails
    public static StateFlag ENCHANTCORE_FLAG;
    // The name of the custom flag - public static final for access from other classes (like listeners)
    public static final String FLAG_NAME = "enchantcore-effects";

    public WorldGuardHook(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        // Initialization of instance/enabled status happens in initializeWorldGuardInstance() during onEnable
    }

    /**
     * Initializes the WorldGuard instance variable after plugins are loaded.
     * Sets the 'enabled' status based on successful hook and flag availability.
     * Should be called from EnchantCore's onEnable.
     */
    public void initializeWorldGuardInstance() {
        Plugin wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wgPlugin == null) { // No need for instanceof check, presence is enough
            logger.info("WorldGuard plugin not found. WorldGuard region features disabled.");
            enabled = false;
            return;
        }
        try {
            worldGuardInstance = WorldGuard.getInstance();
            // Check if both the instance AND our flag were successfully loaded/registered
            enabled = (worldGuardInstance != null && ENCHANTCORE_FLAG != null);

            if (enabled) {
                logger.info("Successfully hooked into WorldGuard API.");
            } else if (worldGuardInstance == null) {
                logger.severe("Failed to get WorldGuard instance during initialization!");
            } else { // worldGuardInstance != null but ENCHANTCORE_FLAG is null
                logger.severe("WorldGuard instance found, but custom flag '" + FLAG_NAME + "' registration failed earlier (check onLoad logs). Region protection disabled.");
            }
        } catch (NoClassDefFoundError e) {
            logger.warning("WorldGuard API classes not found during initialization. Compatibility issue? WG features disabled.");
            enabled = false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An unexpected error occurred while initializing WorldGuard hook.", e);
            enabled = false;
        }
    }

    /**
     * Registers the custom WorldGuard flag 'enchantcore-effects'.
     * Intended to be called during the plugin's onLoad phase.
     */
    public static void registerFlag() {
        // Use a temporary logger or get plugin instance if needed, but static context is tricky.
        // Best practice is to log via the main plugin instance *if* called from there.
        Logger staticLogger = Logger.getLogger("EnchantCore"); // Generic logger for static context

        if (ENCHANTCORE_FLAG != null) {
            staticLogger.fine("WorldGuard flag '" + FLAG_NAME + "' already registered.");
            return; // Avoid re-registering
        }

        Plugin wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wgPlugin == null) {
            staticLogger.info("WorldGuard not found during onLoad, cannot register flag '" + FLAG_NAME + "'.");
            return;
        }

        // Get WorldGuard instance - needed for registry
        WorldGuard wgInstance;
        try {
            wgInstance = WorldGuard.getInstance();
            if (wgInstance == null) { // Should generally not be null if plugin is present
                staticLogger.severe("Could not get WorldGuard instance during onLoad despite plugin presence!");
                return;
            }
        } catch (NoClassDefFoundError e) {
            staticLogger.severe("WorldGuard API classes not found during onLoad. Cannot register flag.");
            return;
        } catch (Exception e) {
            staticLogger.log(Level.SEVERE, "Unexpected error getting WorldGuard instance during onLoad.", e);
            return;
        }

        // Get the flag registry
        FlagRegistry registry = wgInstance.getFlagRegistry();
        try {
            // Create the StateFlag (boolean flag), defaulting to DENY (false)
            StateFlag flag = new StateFlag(FLAG_NAME, false);
            registry.register(flag); // Attempt registration
            ENCHANTCORE_FLAG = flag; // Store the registered flag instance
            staticLogger.info("Registered WorldGuard flag: '" + FLAG_NAME + "' (Default: DENY)");
        } catch (FlagConflictException e) {
            // Flag already exists (perhaps registered by a previous load/another plugin?)
            staticLogger.warning("WorldGuard flag '" + FLAG_NAME + "' conflicted or already registered. Attempting to retrieve existing flag...");
            Flag<?> existing = registry.get(FLAG_NAME); // Use Flag<?> wildcard type
            if (existing instanceof StateFlag) {
                ENCHANTCORE_FLAG = (StateFlag) existing; // Cast and store existing flag
                staticLogger.info("Successfully retrieved existing WorldGuard flag: '" + FLAG_NAME + "'");
            } else if (existing != null) {
                // Flag exists but is the wrong type (e.g., StringFlag instead of StateFlag)
                staticLogger.severe("Flag '" + FLAG_NAME + "' exists but is not a StateFlag! EnchantCore region protection might not work correctly.");
            } else {
                // Conflict reported but get returned null - should not happen?
                staticLogger.severe("Flag '" + FLAG_NAME + "' conflict reported, but registry.get() returned null!");
            }
        } catch (IllegalStateException e) {
            // Typically thrown if registration is attempted after onLoad
            staticLogger.log(Level.SEVERE, "IllegalStateException during flag registration (should only happen if called after onLoad): " + e.getMessage());
        } catch (Exception e) {
            // Catch any other unexpected errors during registration
            staticLogger.log(Level.SEVERE, "Error registering WorldGuard flag '" + FLAG_NAME + "'.", e);
        }
    }

    /** Checks if the WorldGuard hook is successfully enabled. */
    public boolean isEnabled() {
        // Enabled requires both the WG instance and our custom flag to be non-null
        return enabled && worldGuardInstance != null && ENCHANTCORE_FLAG != null;
    }

    /**
     * Checks if the custom EnchantCore flag is effectively ALLOW at a specific location.
     * Considers region priorities, inheritance, and the global region.
     * If WorldGuard is not enabled or the flag isn't registered, this will default to true (allow).
     *
     * @param location The Bukkit Location to check. Cannot be null.
     * @return True if the flag is effectively ALLOW or if WorldGuard hook is disabled, false if DENY or if an error occurs during check.
     */
    public boolean isEnchantAllowed(@NotNull Location location) {
        // Default to allowed if hook isn't working or input is invalid
        if (!isEnabled() || location.getWorld() == null) {
            return true;
        }

        // Get WG region manager for the world
        RegionContainer container = worldGuardInstance.getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(location.getWorld()));
        if (regions == null) {
            // Log error if manager not found for a valid world
            logger.warning("Could not get WorldGuard RegionManager for world: " + location.getWorld().getName());
            return true; // Default to allowed if manager fails
        }

        try {
            // Adapt Bukkit Location to WorldEdit Location/Vector
            com.sk89q.worldedit.util.Location wgLocation = BukkitAdapter.adapt(location);
            // Get the set of regions applicable at this precise point
            ApplicableRegionSet regionSet = regions.getApplicableRegions(wgLocation.toVector().toBlockPoint());
            // Query the effective state of our flag.
            // Passing null as the first argument checks the flag state for the location itself,
            // ignoring any player-specific bypass flags or permissions.
            StateFlag.State state = regionSet.queryState(null, ENCHANTCORE_FLAG);

            // Return true only if the final resulting state is ALLOW
            return state == StateFlag.State.ALLOW;
        } catch (Exception e) {
            // Log errors during the WG check process
            logger.log(Level.SEVERE, "Error querying WorldGuard flag '" + FLAG_NAME + "' at location: " + location, e);
            return false; // Default to disallowed on error for safety
        }
    }

    /**
     * Gets the set of applicable regions at a location, but only if the overall effective state
     * of the given flag at that location matches the desiredState.
     * Useful for finding regions to scan for area effects like Disc.
     *
     * @param loc The Bukkit Location to check. Cannot be null.
     * @param flag The StateFlag to query (e.g., ENCHANTCORE_FLAG). Cannot be null.
     * @param desiredState The desired effective state of the flag (e.g., StateFlag.State.ALLOW). Cannot be null.
     * @return A Set of ProtectedRegion objects if the effective flag state matches, or an empty set otherwise or on error. Never null.
     */
    @NotNull
    public Set<ProtectedRegion> getRegionsIfEffectiveStateMatches(@NotNull Location loc, @NotNull StateFlag flag, @NotNull StateFlag.State desiredState) {
        // Return empty set immediately if hook isn't ready or inputs are invalid
        if (!isEnabled() || loc.getWorld() == null || flag == null || worldGuardInstance == null) {
            return Collections.emptySet();
        }

        RegionContainer container = worldGuardInstance.getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(loc.getWorld()));
        if (regionManager == null) {
            logger.warning("Could not get WG RegionManager for world: " + loc.getWorld().getName() + " in getRegionsIfEffectiveStateMatches.");
            return Collections.emptySet(); // Return empty on error
        }

        try {
            com.sk89q.worldedit.util.Location wgLocation = BukkitAdapter.adapt(loc);
            ApplicableRegionSet applicableRegions = regionManager.getApplicableRegions(wgLocation.toVector().toBlockPoint());

            // Check the overall effective state first
            if (applicableRegions.queryState(null, flag) == desiredState) {
                // If the effective state matches, return a copy of the regions in the set
                // Note: getRegions() returns an immutable set, but copying ensures safety if API changes.
                return new HashSet<>(applicableRegions.getRegions());
            } else {
                // Effective state did not match
                return Collections.emptySet();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error querying WG regions with flag state check at location: " + loc, e);
            return Collections.emptySet(); // Return empty on error
        }
    }
}