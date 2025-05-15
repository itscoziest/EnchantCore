package com.strikesenchantcore.tasks;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.enchants.EnchantmentWrapper; // Keep if needed later
import com.strikesenchantcore.pickaxe.PickaxeManager;
import com.strikesenchantcore.util.PDCUtil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode; // Import GameMode if needed for fly check later
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull; // <<<--- ADDED THIS IMPORT

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger

/**
 * A repeating task that applies passive potion effects (like Haste, Speed)
 * to players holding an EnchantCore pickaxe with the corresponding enchantments in their main hand.
 */
public class PassiveEffectTask extends BukkitRunnable {

    private final EnchantCore plugin;
    private final PickaxeManager pickaxeManager; // Cached instance
    private final Logger logger; // Cache logger

    // Define the keys for the enchants handled here (ensure these match RawName in enchants.yml)
    private static final String HASTE_KEY = "haste";
    private static final String SPEED_KEY = "speed";
    // Add keys for Jump, Fly etc. if implementing them as passive potion effects

    // Duration for applied effects (slightly longer than task interval to prevent flickering)
    // Task runs every 20 ticks (1s), 40 ticks = 2s duration.
    private static final int EFFECT_DURATION_TICKS = 40;

    public PassiveEffectTask(@NotNull EnchantCore plugin) { // @NotNull used here
        this.plugin = plugin;
        this.pickaxeManager = plugin.getPickaxeManager(); // Get instance once
        this.logger = plugin.getLogger();

        if (this.pickaxeManager == null) {
            logger.severe("PickaxeManager is null in PassiveEffectTask! Passive effects will not work.");
            // Optionally cancel task immediately? Seems reasonable.
            try { this.cancel(); } catch (IllegalStateException ignore) {}
        }
    }

    @Override
    public void run() {
        // Exit if manager is unavailable or plugin disabled
        if (pickaxeManager == null || !plugin.isEnabled()) {
            if (!this.isCancelled()) { // Prevent error spam if somehow run after cancelled
                // Minimal logging here as constructor already warned if manager was null
                if (!this.isCancelled()) logger.warning("PassiveEffectTask running but prerequisites failed. Cancelling task.");
                try { this.cancel(); } catch (IllegalStateException ignore) {}
            }
            return;
        }

        // Iterate through all online players safely (uses a snapshot)
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Basic validity checks
            if (player == null || !player.isValid() || player.isDead()) {
                continue;
            }

            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            // Check if the player is holding the EnchantCore pickaxe *in their main hand*
            if (PDCUtil.isEnchantCorePickaxe(itemInHand)) {
                try {
                    // Get all enchant levels from the pickaxe
                    Map<String, Integer> enchantLevels = pickaxeManager.getAllEnchantLevels(itemInHand);

                    // Apply Haste effect
                    applyEffect(player, enchantLevels, HASTE_KEY, PotionEffectType.FAST_DIGGING);

                    // Apply Speed effect
                    applyEffect(player, enchantLevels, SPEED_KEY, PotionEffectType.SPEED);

                    // --- Add other passive potion effects here ---
                    // Example: applyEffect(player, enchantLevels, "jump", PotionEffectType.JUMP);

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error processing passive effects for player " + player.getName(), e);
                }
            } else {
                // Player is not holding the required pickaxe in main hand.
                // Remove effects potentially applied by this task.
                // WARNING: This might remove effects granted by other sources (beacons, commands, etc.).
                try {
                    removeEffect(player, PotionEffectType.FAST_DIGGING);
                    removeEffect(player, PotionEffectType.SPEED);
                    // --- Remove other passive effects here ---
                    // Example: removeEffect(player, PotionEffectType.JUMP);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error removing passive effects for player " + player.getName(), e);
                }
            }
        }
    }

    /**
     * Helper method to apply a specific potion effect based on enchant level.
     * Ensures amplifier is non-negative.
     *
     * @param player The player to apply the effect to.
     * @param enchantLevels Map of active enchants on the item.
     * @param enchantKey The RawName key of the enchantment.
     * @param effectType The PotionEffectType to apply.
     */
    private void applyEffect(Player player, Map<String, Integer> enchantLevels, String enchantKey, PotionEffectType effectType) {
        int level = enchantLevels.getOrDefault(enchantKey, 0);
        if (level > 0) {
            // Calculate amplifier (Potion level I = amplifier 0, II = 1, etc.)
            // Ensure amplifier doesn't go below 0.
            int amplifier = Math.max(0, level - 1);

            // Create the potion effect instance
            // Ambient: false (particles less visible), Particles: false (no particles), Icon: true (show icon)
            PotionEffect effect = new PotionEffect(effectType, EFFECT_DURATION_TICKS, amplifier, false, false, true);

            // Apply the effect, overwriting existing ones of the same type if necessary
            try {
                player.addPotionEffect(effect, true); // true = force overwrite
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to apply effect " + effectType.getName() + " to " + player.getName(), e);
            }
        } else {
            // Enchant level is 0 or not present, ensure the effect is removed
            removeEffect(player, effectType);
        }
    }

    /**
     * Helper method to remove a specific potion effect if the player has it.
     * Logs potential errors.
     *
     * @param player The player.
     * @param effectType The PotionEffectType to remove.
     */
    private void removeEffect(Player player, PotionEffectType effectType) {
        // Check if player actually has the effect before trying to remove
        if (player.hasPotionEffect(effectType)) {
            try {
                player.removePotionEffect(effectType);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to remove effect " + effectType.getName() + " from " + player.getName(), e);
            }
        }
    }

} // End of PassiveEffectTask class