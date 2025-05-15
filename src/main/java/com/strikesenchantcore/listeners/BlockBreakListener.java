// src/main/java/com/strikesenchantcore/listeners/BlockBreakListener.java
package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.AutoSellConfig;
import com.strikesenchantcore.config.ConfigManager;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.enchants.EnchantRegistry;
import com.strikesenchantcore.pickaxe.PickaxeManager;
import com.strikesenchantcore.util.ColorUtils;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.PDCUtil;
import com.strikesenchantcore.util.VaultHook;
import com.strikesenchantcore.util.WorldGuardHook;
import java.util.stream.Collectors; // For Collectors class used in streams

import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger; // Added Logger import

public class BlockBreakListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager;
    private final PickaxeManager pickaxeManager;
    private final EnchantRegistry enchantRegistry;
    private final WorldGuardHook worldGuardHook;
    private final VaultHook vaultHook;
    private final AutoSellConfig autoSellConfig;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Logger logger; // Use the plugin's logger
    private final Random random = ThreadLocalRandom.current(); // Use ThreadLocalRandom for better performance potentially
    private static final String METADATA_ENCHANT_BREAK = "EnchantCore_EnchantBreak";
    private static final String METADATA_NUKE_TNT = "EnchantCore_NukeTNT";
    private static final int MAX_BLOCKS_PER_TICK = Integer.MAX_VALUE; // Slightly reduced default, adjust as needed
    private static final long MAX_NANOS_PER_TICK = Long.MAX_VALUE ; // Max nanoseconds (~8ms) AreaTask can use per tick

    // Use ConcurrentHashMap for thread safety if accessed from async tasks (though most access seems sync)
    private final Map<UUID, AutoSellSummary> playerSummaries = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingSummaryTasks = new ConcurrentHashMap<>();
    // These need to be thread-safe as they can be accessed by the main thread (event) and async tasks (Nuke countdown/completion)
    public static final Set<UUID> nukeActivePlayers = ConcurrentHashMap.newKeySet();
    public final Map<UUID, BossBar> activeNukeBossBars = new ConcurrentHashMap<>();


    private enum ProcessResult { SOLD, PICKED_UP, COUNTED, IGNORED, FAILED }
    private static class AutoSellSummary { double totalValue=0.0; long totalItems=0L; long rawBlocksSold=0L; }

    public BlockBreakListener(EnchantCore plugin, WorldGuardHook worldGuardHook, AutoSellConfig autoSellConfig) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.autoSellConfig = autoSellConfig;
        this.dataManager = plugin.getPlayerDataManager();
        this.pickaxeManager = plugin.getPickaxeManager();
        this.enchantRegistry = plugin.getEnchantRegistry();
        this.vaultHook = plugin.getVaultHook();
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger(); // Initialize logger
    }

    /**
     * Cleans up resources like BossBars when the plugin is disabled.
     */
    public void cleanupOnDisable() {
        if (isDebugMode()) {
            logger.info("[BlockBreakListener] Cleaning up Nuke BossBars on disable...");
        }
        // Iterate over a copy of the values to avoid ConcurrentModificationException
        List<BossBar> barsToClear = new ArrayList<>(activeNukeBossBars.values());
        for (BossBar bossBar : barsToClear) {
            if (bossBar != null) {
                try {
                    bossBar.removeAll(); // Remove all players safely
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error removing players from Nuke BossBar during cleanup", e);
                }
            }
        }
        activeNukeBossBars.clear(); // Clear the map
        nukeActivePlayers.clear(); // Clear the set of active players
        if (isDebugMode()) {
            logger.info("[BlockBreakListener] Nuke cleanup complete.");
        }
    }

    /**
     * Checks the plugin's configuration manager for debug mode status.
     * @return true if debug mode is enabled, false otherwise.
     */
    private boolean isDebugMode() {
        return configManager != null && configManager.isDebugMode();
    }

    /**
     * Handles cleanup when a player quits the server.
     * Removes pending tasks and Nuke status/BossBar.
     * @param event The PlayerQuitEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR) // Run late to ensure other plugins see the player leave first
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        boolean debug = isDebugMode();

        // Nuke Cleanup
        if (nukeActivePlayers.remove(playerUUID)) {
            if(debug) logger.info("[Debug] Player " + player.getName() + " quit while Nuke was active. Removing from set.");
            // Associated task should cancel itself when player is offline check fails
        }
        BossBar nukeBossBar = activeNukeBossBars.remove(playerUUID);
        if (nukeBossBar != null) {
            try {
                nukeBossBar.removeAll();
                if(debug) logger.info("[Debug] Removed Nuke BossBar for quitting player " + player.getName());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error removing players from Nuke BossBar on player quit", e);
            }
        }

        // AutoSell Summary Cleanup
        BukkitTask pendingTask = pendingSummaryTasks.remove(playerUUID);
        if (pendingTask != null) {
            try { pendingTask.cancel(); } catch (Exception ignore) {} // Ignore if already cancelled
        }
        AutoSellSummary summary = playerSummaries.remove(playerUUID);
        // Send final summary if applicable (Optional - maybe only save data?)
        // Removed sending final summary on quit for simplicity, data is saved anyway.

        // Note: PlayerData unloading/saving is handled by PlayerQuitListener/PlayerDataManager
    }

    /**
     * Main handler for block break events. Processes pickaxe logic, enchants, auto-sell/pickup, and level ups.
     * @param event The BlockBreakEvent.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        final Player player = event.getPlayer();

        // --- Nuke Active Check ---
        if (nukeActivePlayers.contains(player.getUniqueId())) {
            // Title is handled by Nuke activation, just cancel the break event.
            event.setCancelled(true);
            return;
        }
        // --- End Nuke Active Check ---

        Block block = event.getBlock();
        final Material originalMaterial = block.getType();
        final boolean debug = isDebugMode();

        // --- Basic Pre-checks ---
        if (player.getGameMode() != GameMode.SURVIVAL ||
                block.hasMetadata(METADATA_ENCHANT_BREAK) || // Already being processed by an enchant
                originalMaterial == Material.AIR || // Should not happen, but safety check
                !isBreakable(block, false) ) { // Check if breakable *before* getting item (optimization)
            return;
        }

        ItemStack pickaxeInHand = player.getInventory().getItemInMainHand();
        if (!PDCUtil.isEnchantCorePickaxe(pickaxeInHand)) {
            return; // Not holding our pickaxe
        }
        // --- End Pre-checks ---

        final ItemStack finalPickaxeRef = pickaxeInHand; // Final reference for lambdas/tasks

        // --- Player Data Handling ---
        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) { // Try loading if not cached
            playerData = dataManager.loadPlayerData(player.getUniqueId());
            if (playerData == null) { // Still null after loading attempt
                logger.severe("[onBlockBreak] FAILED PlayerData load for " + player.getName() + " (UUID: " + player.getUniqueId() + "). Cannot process break event.");
                return; // Cannot proceed without player data
            }
        }
        final PlayerData finalPlayerData = playerData; // Final reference
        // --- End Player Data Handling ---

        if(debug) logger.info("\n[DEBUG] == Starting Break Event: " + player.getName() + " | " + originalMaterial + " ==");
        boolean needsLoreUpdate = false; // Flag to track if pickaxe visuals need updating

        // --- Process the single block break (AutoSell/AutoPickup/Counting) ---
        ProcessResult initialResult = ProcessResult.IGNORED;
        try {
            initialResult = processSingleBlockBreak(player, block, originalMaterial, finalPickaxeRef, event, finalPlayerData);
            if(debug) logger.info("[DEBUG] Initial block process result: " + initialResult);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[DEBUG] Exception during processSingleBlockBreak for " + player.getName(), e);
            initialResult = ProcessResult.FAILED;
        }
        if (initialResult == ProcessResult.COUNTED || initialResult == ProcessResult.PICKED_UP || initialResult == ProcessResult.SOLD) {
            needsLoreUpdate = true; // Mark if the block contributed to stats
            if(debug) logger.fine("[DEBUG] Block counted/picked/sold, marking for lore update.");
        }
        // --- End Single Block Processing ---

        // --- Process Enchant Activations ---
        Map<String, Integer> enchantLevels = pickaxeManager.getAllEnchantLevels(finalPickaxeRef);
        if (!enchantLevels.isEmpty()) {
            if(debug) logger.info("[DEBUG] Found enchants on pickaxe: " + enchantLevels.keySet());
            try {
                processEnchantmentActivations(player, block, finalPickaxeRef, finalPlayerData, enchantLevels, event);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[DEBUG] Exception during processEnchantmentActivations for " + player.getName(), e);
            }
        } else if(debug) {
            logger.info("[DEBUG] No EC enchants found on pickaxe.");
        }
        // --- End Enchant Activations ---

        // --- Check for Level Up ---
        // This should happen *after* activations which might affect block counts (though currently they don't directly)
        try {
            if (pickaxeManager.checkForLevelUp(player, finalPlayerData, finalPickaxeRef)) {
                needsLoreUpdate = true; // Level up requires lore update
                if(debug) logger.info("[DEBUG] Level up detected, forcing lore update.");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[DEBUG] Exception during checkForLevelUp for " + player.getName(), e);
        }
        // --- End Level Up Check ---


        // --- Final Pickaxe Update ---
        // Update lore/name if anything changed (blocks mined, level up)
        if (needsLoreUpdate) {
            if(debug) logger.info("[DEBUG] Applying final pickaxe lore update.");
            try {
                pickaxeManager.updatePickaxe(finalPickaxeRef, player);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[DEBUG] Exception during final updatePickaxe for " + player.getName(), e);
            }
        }
        // --- End Final Update ---

        if(debug) logger.info("[DEBUG] == Finished Break Event: " + player.getName() + " ==");
    }

    /**
     * Prevents the default explosion behavior of TNT marked for the Nuke enchant.
     * @param event The ExplosionPrimeEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNukeTntPrime(ExplosionPrimeEvent event) {
        if (event.getEntityType() == EntityType.PRIMED_TNT && event.getEntity().hasMetadata(METADATA_NUKE_TNT)) {
            event.setCancelled(true); // Prevent vanilla explosion
            event.setRadius(0F);      // Ensure no block damage even if cancellation fails somehow
            event.setFire(false);     // Prevent fire spread
            if (isDebugMode()) logger.info("[Dbg][NukeTNTListener] Cancelled vanilla explosion for custom Nuke TNT: " + event.getEntity().getUniqueId());
        }
    }

    /**
     * Processes a single block break for AutoSell, AutoPickup, and block counting.
     *
     * @param player Player breaking the block.
     * @param block The block being broken.
     * @param originalMaterial The original material of the block (before breaking).
     * @param pickaxe The pickaxe used.
     * @param event The BlockBreakEvent (can be null if called from a task).
     * @param playerData The player's data.
     * @return The result of the processing (SOLD, PICKED_UP, COUNTED, etc.).
     */
    private ProcessResult processSingleBlockBreak(Player player, Block block, Material originalMaterial, ItemStack pickaxe, @Nullable BlockBreakEvent event, PlayerData playerData) {
        final boolean debug = isDebugMode();
        if (playerData == null) { // Should be checked before calling, but double-check
            if(debug) logger.warning("[Debug][ProcessSingle] PlayerData is null for " + player.getName());
            return ProcessResult.FAILED;
        }
        boolean dropsCancelled = false; // Track if drops should be cancelled by this method
        boolean blockSold = false;
        boolean blockCounted = false;

        if(debug) logger.fine("[DEBUG][ProcessSingle] Processing: " + originalMaterial + " for " + player.getName() + (event == null ? " (Tasked)" : " (Event)"));

        // --- AutoSell Logic ---
        boolean autoSellEnabled = configManager.getConfig().getBoolean("AutoSell.Enabled", false);
        if (autoSellEnabled && player.hasPermission("enchantcore.autosell") && vaultHook.isEnabled()) {
            double price = autoSellConfig.getSellPrice(originalMaterial);
            if (price > 0) {
                Collection<ItemStack> drops = getDropsForBlock(block, originalMaterial, pickaxe, player, event);
                int amount = drops.stream().mapToInt(ItemStack::getAmount).sum();
                if (amount == 0 && originalMaterial.isItem()) amount = 1; // Handle items that don't "drop" themselves

                if (amount > 0) {
                    double boosterMultiplier = playerData.getBlockBoosterMultiplier(); // Get current booster
                    double totalPrice = price * amount * boosterMultiplier;
                    if (vaultHook.deposit(player, totalPrice)) {
                        if (debug) logger.fine(String.format("[DEBUG][AutoSell] P: %s Sold: %d x %s Price: %.2f Multi: %.1fx Total: %.2f", player.getName(), amount, originalMaterial, price, boosterMultiplier, totalPrice));
                        AutoSellSummary summary = playerSummaries.computeIfAbsent(player.getUniqueId(), k -> new AutoSellSummary());
                        summary.totalValue += totalPrice;
                        summary.totalItems += amount;
                        if (event != null) summary.rawBlocksSold++; // Only count if it's the *direct* break event

                        scheduleSummaryMessage(player);
                        if (event != null) event.setDropItems(false); // Cancel drops if sold
                        dropsCancelled = true;
                        blockSold = true;
                    } else {
                        if(debug) logger.warning("[DEBUG][AutoSell] Vault deposit FAILED for " + player.getName() + " (Amount: " + totalPrice + ")");
                        // Maybe notify player? Could be spammy.
                    }
                }
            }
        }
        // --- End AutoSell ---

        // --- Count Block ---
        // Always count the block break towards the player's total
        playerData.addBlocksMined(1L);
        PDCUtil.setPickaxeBlocksMined(pickaxe, playerData.getBlocksMined());
        blockCounted = true;
        // Level up check moved to main event handler to run *after* enchants
        // --- End Count Block ---

        // --- AutoPickup Logic ---
        if (!blockSold) { // Only pickup if not sold
            boolean autoPickupEnabled = configManager.getConfig().getBoolean("AutoPickup.Enabled", false);
            if (autoPickupEnabled && player.hasPermission("enchantcore.autopickup")) {
                Collection<ItemStack> drops = getDropsForBlock(block, originalMaterial, pickaxe, player, event);
                if (!drops.isEmpty()) {
                    PlayerInventory inv = player.getInventory();
                    Map<Integer, ItemStack> leftovers = inv.addItem(drops.toArray(new ItemStack[0])); // Add drops to inventory
                    if (!leftovers.isEmpty()) { // If inventory is full
                        Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);
                        String fullMsgFormat = messageManager.getMessage("listeners.autopickup.inventory_full", "&cInv Full! Dropped %item%!");
                        // Notify only once, mention the first item dropped
                        ItemStack firstLeftover = leftovers.values().iterator().next();
                        String itemName = PDCUtil.getItemName(firstLeftover); // Use helper for name
                        ChatUtil.sendMessage(player, fullMsgFormat.replace("%item%", itemName));
                        // Drop all items that didn't fit
                        for (ItemStack leftoverItem : leftovers.values()) {
                            player.getWorld().dropItemNaturally(dropLocation, leftoverItem);
                        }
                        if (debug) logger.fine("[DEBUG][AutoPickup] Player " + player.getName() + " inventory full, dropped leftovers starting with " + itemName);
                    } else {
                        if (debug) logger.finest("[DEBUG][AutoPickup] Player " + player.getName() + " picked up items: " + drops.stream().map(ItemStack::getType).collect(Collectors.toList()));
                    }
                    if (event != null) event.setDropItems(false); // Cancel original drops if picked up
                    dropsCancelled = true;
                }
            }
        }
        // --- End AutoPickup ---

        // Ensure the event's drop status reflects our actions
        if (event != null && dropsCancelled) {
            event.setDropItems(false);
        }

        // Determine final result
        if (blockSold) return ProcessResult.SOLD;
        if (dropsCancelled) return ProcessResult.PICKED_UP; // Not sold, but handled (picked up)
        if (blockCounted) return ProcessResult.COUNTED; // Only counted, drops happen naturally if not cancelled by event
        return ProcessResult.IGNORED; // Block wasn't processed
    }

    /**
     * Calculates and returns the drops for a given block, considering the tool and player context.
     * Handles potential exceptions during drop calculation.
     *
     * @param block The block being broken.
     * @param originalMaterial The original material before breaking (used as fallback).
     * @param tool The tool used (pickaxe).
     * @param player The player involved.
     * @param event The BlockBreakEvent (nullable).
     * @return A collection of ItemStacks representing the drops, or an empty list on error/no drops.
     */
    private Collection<ItemStack> getDropsForBlock(Block block, Material originalMaterial, ItemStack tool, Player player, @Nullable BlockBreakEvent event) {
        try {
            if (event != null && event.isDropItems()) {
                // Prefer using event drops if available and not cancelled
                return block.getDrops(tool, player);
            } else {
                // If no event or drops cancelled by event, calculate manually
                // Need to check current block state carefully
                Block currentBlock = block.getWorld().getBlockAt(block.getLocation());
                if (currentBlock.getType() != Material.AIR) {
                    // If block still exists, get drops from its current state
                    return currentBlock.getDrops(tool, player);
                } else if (originalMaterial.isItem() && originalMaterial != Material.AIR) {
                    // Fallback: If block is gone but original was an item, drop one of it
                    return Collections.singletonList(new ItemStack(originalMaterial));
                } else {
                    // Otherwise (original wasn't item or block is gone), no drops
                    return Collections.emptyList();
                }
            }
        } catch (Exception e) {
            // Log error getting drops
            logger.log(Level.WARNING, "Error calculating drops for " + originalMaterial + " at " + block.getLocation() + " for " + player.getName() + ": " + e.getMessage());
            return Collections.emptyList(); // Return empty list on error
        }
    }


    /**
     * Schedules a task to send the AutoSell summary message after a configured delay.
     * Avoids scheduling duplicate tasks for the same player.
     * @param player The player to send the summary to.
     */
    private void scheduleSummaryMessage(Player player) {
        final boolean debug = isDebugMode();
        UUID playerUUID = player.getUniqueId();
        final int delaySeconds = configManager.getAutoSellSummaryIntervalSeconds();

        // If summaries are disabled, ensure no task is pending
        if (delaySeconds <= 0) {
            BukkitTask existingTask = pendingSummaryTasks.remove(playerUUID);
            if(existingTask != null) {
                try { existingTask.cancel(); } catch (Exception ignore) {}
                if(debug) logger.finest("[Debug][AutoSell] Summaries disabled, cancelled pending task for " + player.getName());
            }
            return;
        }

        // If a task is already scheduled, do nothing
        if (pendingSummaryTasks.containsKey(playerUUID)) {
            if (debug) logger.finest("[Debug][AutoSell] Summary task already pending for " + player.getName() + ", skipping new schedule.");
            return;
        }

        // Schedule the new summary task
        BukkitTask newTask = new BukkitRunnable() {
            @Override
            public void run() {
                pendingSummaryTasks.remove(playerUUID); // Task is running, remove from pending
                AutoSellSummary summary = playerSummaries.remove(playerUUID); // Get and remove summary data
                Player onlinePlayer = Bukkit.getPlayer(playerUUID); // Re-fetch player instance

                // Check conditions for sending message
                if (onlinePlayer != null && onlinePlayer.isOnline() && summary != null && (summary.totalValue > 0 || summary.totalItems > 0)) {
                    PlayerData currentPlayerData = dataManager.getPlayerData(playerUUID); // Get fresh data for multiplier
                    double multiplier = (currentPlayerData != null) ? currentPlayerData.getBlockBoosterMultiplier() : 1.0;
                    sendSummaryMessage(onlinePlayer, summary, multiplier); // Send the formatted message
                    if (debug) logger.fine("[DEBUG][AutoSell Summary] Sent summary to " + onlinePlayer.getName());
                } else if (debug && summary != null) {
                    String reason = (onlinePlayer == null || !onlinePlayer.isOnline()) ? "Player offline" : "Summary empty/processed";
                    logger.fine("[Debug][AutoSell Summary] Did not send summary for " + playerUUID + ". Reason: " + reason);
                } else if (debug && summary == null){
                    logger.fine("[Debug][AutoSell Summary] Task ran for " + playerUUID + " but summary data was already removed.");
                }
            }
        }.runTaskLater(plugin, delaySeconds * 20L); // Schedule with delay

        pendingSummaryTasks.put(playerUUID, newTask); // Store reference to the pending task
        if (debug) logger.finest("[Debug][AutoSell] Scheduled new summary task for " + player.getName() + " in " + delaySeconds + "s");
    }

    /**
     * Formats and sends the AutoSell summary message to the player.
     * @param player The player receiving the message.
     * @param summary The summary data.
     * @param currentMultiplier The player's current block booster multiplier.
     */
    private void sendSummaryMessage(Player player, AutoSellSummary summary, double currentMultiplier) {
        final int interval = configManager.getAutoSellSummaryIntervalSeconds(); // For placeholder
        String header = messageManager.getMessage("autosell.summary.header", "&m----------------------------");
        List<String> bodyFormat = messageManager.getMessageList("autosell.summary.body", List.of("&cAutoSell Summary format missing!"));
        String footer = messageManager.getMessage("autosell.summary.footer", "&m----------------------------");

        // Send Header
        if (header != null && !header.isEmpty()) { ChatUtil.sendMessage(player, header); }

        // Send Body Lines (with placeholder replacement)
        for (String line : bodyFormat) {
            String formattedLine = line
                    .replace("%autosell_interval%", String.valueOf(interval))
                    .replace("%autosell_total_items%", String.format("%,d", summary.totalItems)) // Comma separated total items
                    .replace("%autosell_raw_items%", String.format("%,d", summary.rawBlocksSold)) // Comma separated raw blocks
                    .replace("%autosell_earnings%", vaultHook.format(summary.totalValue)) // Use Vault's formatting for currency
                    .replace("%autosell_multiplier%", String.format("%.1fx", currentMultiplier)); // Multiplier with 1 decimal place
            player.sendMessage(formattedLine); // MessageManager already handles color
        }

        // Send Footer
        if (footer != null && !footer.isEmpty()) { ChatUtil.sendMessage(player, footer); }
    }

    /**
     * Iterates through the pickaxe's enchantments and attempts to activate them based on chance.
     * Calls specific handler methods for each enchantment.
     */
    private void processEnchantmentActivations(Player player, Block originalBlock, ItemStack pickaxe, PlayerData playerData, Map<String, Integer> enchantLevels, BlockBreakEvent event) {
        final boolean debug = isDebugMode();
        if (debug) logger.info("[DEBUG][EnchantActivation] Processing " + enchantLevels.size() + " potential activations for " + player.getName() + "...");

        // Iterate over a copy of the keys to avoid issues if an enchant modifies the map (unlikely here)
        Set<String> keysToCheck = new HashSet<>(enchantLevels.keySet());

        for (String enchantKey : keysToCheck) {
            int level = enchantLevels.getOrDefault(enchantKey, 0); // Get level again inside loop for safety
            if (level <= 0) continue;

            EnchantmentWrapper enchant = enchantRegistry.getEnchant(enchantKey); // Lookup should be fast (HashMap)
            if (enchant == null || !enchant.isEnabled() || enchant.isPassive()) {
                if (debug && enchant != null && enchant.isPassive()) logger.finest(" -> Skipping passive enchant: " + enchantKey);
                continue;
            }

            // WorldGuard check for area effects (checking origin block is sufficient here, task checks target blocks)
            boolean wgAllowedForOrigin = worldGuardHook.isEnchantAllowed(originalBlock.getLocation());
            if(debug) logger.finest(" -> Checking: " + enchantKey + " (Lvl " + level + ") | WG @ Origin: " + wgAllowedForOrigin);
            if (!wgAllowedForOrigin && isAreaEffectEnchant(enchantKey)) {
                if(debug) logger.info("[DEBUG] Skipping area effect enchant " + enchantKey + " because origin is not WG allowed.");
                continue;
            }

            ConfigurationSection settings = enchant.getCustomSettings();
            // Check for required settings section for non-vanilla active enchants
            if (!enchant.isVanilla() && settings == null && requiresSettings(enchantKey)) {
                if(debug) logger.warning("[DEBUG] Skipping active enchant "+enchantKey+" due to missing 'Settings:' section in enchants.yml!");
                continue;
            }

            // --- Chance Calculation ---
            double chance = 1.0; // Default 100%
            if (settings != null && settings.contains("ChanceBase")) {
                chance = settings.getDouble("ChanceBase", 0.0) + (settings.getDouble("ChanceIncreasePerLevel", 0.0) * Math.max(0, level - 1));
                chance = Math.max(0.0, Math.min(1.0, chance)); // Clamp chance [0.0, 1.0]
            }
            if (debug) logger.fine(String.format("[DEBUG][ChanceCheck] %s (L%d): Calc Chance=%.6f", enchantKey, level, chance));

            // --- Roll and Compare ---
            if (random.nextDouble() >= chance) { // Fail if roll >= chance
                if (debug && chance < 1.0) logger.fine(String.format("[DEBUG][ChanceCheck] FAILED for %s (Needed < %.6f)", enchantKey, chance));
                continue; // Failed chance check, next enchant
            }

            // --- Activation Success ---
            if(debug) logger.info(String.format("[DEBUG][ChanceCheck] PASSED for %s! Entering handler...", enchantKey));
            try {
                // Call appropriate handler
                switch (enchantKey) {
                    case "explosive":       handleExplosive(player, originalBlock.getLocation(), pickaxe, level, settings, event, playerData); break;
                    case "disc":            handleDisc(player, originalBlock, pickaxe, level, settings, event, playerData); break;
                    case "nuke":            handleNukeTNT(player, originalBlock.getLocation(), pickaxe, level, settings, playerData); break;
                    case "charity":         handleCharity(player, level, settings, playerData); break;
                    case "blessing":        handleBlessing(player, level, settings, playerData); break;
                    case "tokenator":       handleTokenator(player, level, settings, playerData); break;
                    case "keyfinder":       handleKeyFinder(player, level, settings, playerData); break;
                    case "blockbooster":    handleBlockBoosterActivation(player, playerData, level, settings); break;
                    case "salary":          handleSalary(player, level, settings, playerData); break;
                    case "voucherfinder":   handleVoucherFinder(player, level, settings, playerData); break;
                    // No default case needed - if it's not listed, it's either passive, vanilla, or has no handler
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[DEBUG] Exception during handler execution for enchant " + enchantKey + " for player " + player.getName(), e);
            }
        } // End enchant loop
        if(debug) logger.info("[DEBUG][EnchantActivation] Finished activation loop for " + player.getName());
    }

    // Helper to check if an enchant key corresponds to an area effect enchant
    private boolean isAreaEffectEnchant(String key) {
        return key.equals("explosive") || key.equals("nuke") || key.equals("disc");
    }

    // Helper to check if an enchant requires the 'Settings' section
    private boolean requiresSettings(String key) {
        // List all active, non-vanilla enchants that NEED settings
        return isAreaEffectEnchant(key) || // Area effects need Radius/etc.
                key.equals("charity") || key.equals("blessing") || key.equals("tokenator") ||
                key.equals("keyfinder") || key.equals("blockbooster") || key.equals("salary") ||
                key.equals("voucherfinder");
        // Add any other custom active enchants that rely on the Settings section
    }

    /**
     * Handles the Nuke enchantment activation, countdown, and block breaking task.
     */
    private void handleNukeTNT(Player player, Location impactLocation, ItemStack pickaxe, int level, ConfigurationSection settings, PlayerData playerData) {
        final boolean debug = isDebugMode();
        UUID playerUUID = player.getUniqueId();

        // Prevent concurrent Nukes for the same player
        if (nukeActivePlayers.contains(playerUUID)) {
            if (debug) logger.info("[Dbg][NukeTNT] Nuke already active for " + player.getName() + ", skipping.");
            return;
        }

        // Ensure settings are available
        if (settings == null) {
            logger.severe("[NukeTNT] Cannot activate Nuke for " + player.getName() + ": ConfigurationSection is null!");
            return;
        }

        // --- Configuration Loading with Defaults ---
        final int explosionRadius = settings.getInt("Radius", 15);
        final boolean breakBedrock = settings.getBoolean("BreakBedrock", false);
        final int countdownSeconds = settings.getInt("CountdownSeconds", 3);
        final int totalTicksForCountdown = Math.max(20, countdownSeconds * 20); // Min 1 second
        final boolean bossBarEnabled = settings.getBoolean("CountdownBossBarEnabled", true); // Default true now
        final String bossBarTitleFormat = settings.getString("BossBarTitle", "&c&lNuke: &e%countdown%s");
        final String bossBarColorStr = settings.getString("BossBarColor", "RED");
        final boolean particleEffectEnabled = settings.getBoolean("ParticleEffectEnabled", true); // Default true now

        BarColor bossBarColor = BarColor.RED; // Default
        try {
            bossBarColor = BarColor.valueOf(bossBarColorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            if(debug) logger.warning("[NukeTNT] Invalid BossBarColor '" + bossBarColorStr + "'. Defaulting to RED.");
        }
        final BarStyle bossBarStyle = BarStyle.SOLID; // Consistent style
        // --- End Configuration Loading ---

        // Mark player as active and provide initial feedback
        nukeActivePlayers.add(playerUUID);

        // 1. Sound
        if (playerData.isShowEnchantSounds()) {
            playSoundAt(player, impactLocation, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        }
        // 2. Persistent Title
        String titleText = messageManager.getMessage("listeners.nuke.restricted_title", "&4&lNUKE ACTIVE");
        String subtitleText = messageManager.getMessage("listeners.nuke.restricted_subtitle", "&cMining is restricted!");
        int fadeInTicks = messageManager.getConfig().getInt("listeners.nuke.title_fadein", 10);
        int stayTicks = totalTicksForCountdown + 40; // Show title for full countdown + 2 sec buffer
        int fadeOutTicks = messageManager.getConfig().getInt("listeners.nuke.title_fadeout", 20);
        sendTitleToPlayer(player, titleText, subtitleText, fadeInTicks, stayTicks, fadeOutTicks);
        if(debug) logger.info("[Dbg][NukeTNT] Sent persistent title to " + player.getName() + " (Stay: "+stayTicks+"t)");


        // --- Spawn Visual TNT ---
        Location tntSpawnLocation = impactLocation.clone().add(0.5, 0.5, 0.5); // Center in block
        final TNTPrimed nukeTnt;
        try {
            nukeTnt = (TNTPrimed) impactLocation.getWorld().spawn(tntSpawnLocation, TNTPrimed.class, tnt -> {
                tnt.setFuseTicks(totalTicksForCountdown + 60); // Extra long fuse, > stayTicks
                tnt.setSource(player);
                tnt.setYield(0f);
                tnt.setIsIncendiary(false);
                tnt.setMetadata(METADATA_NUKE_TNT, new FixedMetadataValue(plugin, true));
            });
            if (debug) logger.info("[Dbg][NukeTNT] Spawned visual TNTPrimed ("+nukeTnt.getUniqueId()+") for " + player.getName());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Dbg][NukeTNT] Failed to spawn visual TNTPrimed for " + player.getName(), e);
            notifyNukeComplete(playerUUID, true); // Abort if TNT fails to spawn
            return;
        }
        // --- End Spawn Visual TNT ---


        // --- Setup BossBar ---
        final BossBar nukeBossBar = bossBarEnabled ? Bukkit.createBossBar("", bossBarColor, bossBarStyle) : null;
        if (nukeBossBar != null) {
            nukeBossBar.addPlayer(player);
            activeNukeBossBars.put(playerUUID, nukeBossBar);
            if(debug) logger.info("[Dbg][NukeTNT] Created and added player to BossBar.");
        }
        // --- End Setup BossBar ---


        // --- Countdown Task ---
        new BukkitRunnable() {
            private int ticksElapsed = 0;
            private final UUID taskPlayerUUID = playerUUID; // Store UUID for checks

            @Override
            public void run() {
                // --- Cancellation Checks ---
                // Re-fetch player instance inside the task for safety
                Player currentPlayer = Bukkit.getPlayer(taskPlayerUUID);
                if (currentPlayer == null || !currentPlayer.isOnline() || !nukeTnt.isValid() || nukeTnt.isDead()) {
                    cleanupAndCancel(true); // True indicates aborted/incomplete
                    if(debug) logger.info("[Dbg][NukeTNT Task] Cancelled for " + taskPlayerUUID + " (Player Offline/TNT Invalid).");
                    return;
                }
                // Check if player is still marked as active (might have been completed/cancelled externally)
                if (!nukeActivePlayers.contains(taskPlayerUUID)) {
                    cleanupAndCancel(false); // False indicates likely completed elsewhere
                    if(debug) logger.info("[Dbg][NukeTNT Task] Cancelled for " + taskPlayerUUID + " (No longer in active set).");
                    return;
                }
                // --- End Cancellation Checks ---

                Location currentTntLocation = nukeTnt.getLocation();
                // Particles (throttled)
                if (particleEffectEnabled && ticksElapsed % 4 == 0) {
                    spawnParticleEffect(currentTntLocation.getWorld(), Particle.REDSTONE, currentTntLocation.clone().add(0, 0.5, 0), 10, 0.5, new Particle.DustOptions(Color.RED, 1.0F));
                }

                // --- Countdown Logic ---
                if (ticksElapsed < totalTicksForCountdown) {
                    if (ticksElapsed % 20 == 0) { // Update every second
                        int remainingSeconds = countdownSeconds - (ticksElapsed / 20);
                        // Update BossBar title and progress
                        if (nukeBossBar != null) {
                            String bossBarText = bossBarTitleFormat.replace("%countdown%", String.valueOf(remainingSeconds));
                            nukeBossBar.setTitle(ChatUtil.color(bossBarText));
                            nukeBossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) remainingSeconds / countdownSeconds)));
                        }
                        // Play tick sound
                        if (remainingSeconds > 0 && playerData.isShowEnchantSounds()) {
                            playSoundAt(currentPlayer, currentTntLocation, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f + ((countdownSeconds - remainingSeconds) * 0.2f));
                        }
                    }
                    ticksElapsed++;
                } else {
                    // --- Detonation ---
                    cleanupAndCancel(false); // Stop countdown task, clean up TNT/Bar (false = normal finish)

                    Location explosionCenter = currentTntLocation; // Use TNT's last known location
                    if (debug) logger.info("[Dbg][NukeTNT] Countdown finished. Triggering custom explosion at " + explosionCenter + " for " + player.getName());

                    // Explosion Effects (Sound & Particle)
                    if(playerData.isShowEnchantSounds()) {
                        playSoundAt(currentPlayer, explosionCenter, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
                    }
                    spawnParticleEffect(explosionCenter.getWorld(), Particle.EXPLOSION_HUGE, explosionCenter.clone().add(0.5,0.5,0.5), 20, explosionRadius * 0.3, null);

                    // Find blocks (this can take time)
                    List<Block> blocksToBreak = findBlocksInRadius(explosionCenter, explosionRadius, breakBedrock, "NukeTNT");

                    // Start AreaBlockBreakTask
                    if (!blocksToBreak.isEmpty()) {
                        if (debug) logger.info("[Dbg][NukeTNT] Tasking " + blocksToBreak.size() + " blocks for Nuke for " + player.getName());
                        // Pass the *original* player reference and data snapshot
                        new AreaBlockBreakTask(player, pickaxe, blocksToBreak, breakBedrock, explosionCenter, "NukeTNT", playerData, true).runTaskTimer(plugin, 1L, 1L);
                        // The Area task will call notifyNukeComplete when it finishes
                    } else {
                        if (debug) logger.info("[Dbg][NukeTNT] 0 blocks found for Nuke for " + player.getName());
                        notifyNukeComplete(taskPlayerUUID, true); // Notify immediately if no blocks found
                    }
                } // --- End Detonation ---
            }

            // Helper method within the Runnable to handle cleanup and cancellation
            private void cleanupAndCancel(boolean aborted) {
                this.cancel(); // Stop this runnable
                // Clean up BossBar from main map
                BossBar bar = activeNukeBossBars.remove(taskPlayerUUID);
                if (bar != null) {
                    try { bar.removeAll(); } catch (Exception ignore) {}
                }
                // Remove visual TNT
                if (nukeTnt.isValid() && !nukeTnt.isDead()) {
                    try { nukeTnt.remove(); } catch (Exception ignore) {}
                }
                // If aborted prematurely, notify completion immediately
                if (aborted) {
                    notifyNukeComplete(taskPlayerUUID, true); // Mark as potentially incomplete
                }
            }

        }.runTaskTimer(plugin, 0L, 1L); // Run task every tick
    }


    /**
     * Handles the Disc enchantment activation. Finds blocks in the layer within allowed WG regions.
     */
    private void handleDisc(Player player, Block brokenBlock, ItemStack pickaxe, int level, ConfigurationSection settings, BlockBreakEvent event, PlayerData playerData) {
        final boolean debug = isDebugMode();
        // Null check settings for safety
        if (settings == null) {
            logger.warning("[Disc] Cannot activate Disc for " + player.getName() + ": ConfigurationSection is null!");
            return;
        }
        boolean breakBedrock = settings.getBoolean("BreakBedrock", false);
        int yLevel = brokenBlock.getY();
        World world = brokenBlock.getWorld();
        Set<Block> blocksToBreak = new HashSet<>(); // Use Set to avoid duplicates

        // --- Feedback ---
        if (playerData.isShowEnchantMessages()) {
            String msgFormat = settings.getString("Message", "&b&lWoosh! &3Disc cleared layer %layer%!");
            ChatUtil.sendMessage(player, msgFormat.replace("%layer%", String.valueOf(yLevel)));
        }
        if (playerData.isShowEnchantSounds()) {
            playSoundAt(player, player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.5f);
        }
        // --- End Feedback ---

        if (debug) logger.info("[Dbg][Disc] Player " + player.getName() + " triggered Disc at Y=" + yLevel);

        // --- WorldGuard Integration ---
        if (!worldGuardHook.isEnabled()) {
            if (debug) logger.info("[Dbg][Disc] WorldGuard is not enabled. Disc enchantment cannot function.");
            return; // Essential for Disc to prevent grief
        }

        // Get regions at the *broken block's* location where the enchant is allowed
        Set<ProtectedRegion> allowedRegions = worldGuardHook.getRegionsIfEffectiveStateMatches(
                brokenBlock.getLocation(),
                WorldGuardHook.ENCHANTCORE_FLAG,
                StateFlag.State.ALLOW
        );

        if (allowedRegions.isEmpty()) {
            if (debug) logger.info("[Dbg][Disc] No WorldGuard regions allow '" + WorldGuardHook.FLAG_NAME + "' at " + brokenBlock.getLocation() + ". Disc affects nothing.");
            return;
        }
        if (debug) logger.info("[Dbg][Disc] Found " + allowedRegions.size() + " allowed WorldGuard region(s) at origin.");
        // --- End WorldGuard Integration ---

        // --- Block Finding ---
        // Iterate through the allowed regions found at the origin
        for (ProtectedRegion region : allowedRegions) {
            if (region == null) continue;
            // Get region bounds safely
            com.sk89q.worldedit.math.BlockVector3 minPoint, maxPoint;
            try {
                minPoint = region.getMinimumPoint(); maxPoint = region.getMaximumPoint();
            } catch (Exception e) { continue; } // Skip region if bounds fail

            int minX = minPoint.getBlockX(); int maxX = maxPoint.getBlockX();
            int minZ = minPoint.getBlockZ(); int maxZ = maxPoint.getBlockZ();

            if (debug) logger.fine("[Dbg][Disc] Scanning region '" + region.getId() + "' X(" + minX + "-" + maxX + "), Z(" + minZ + "-" + maxZ + ") at Y=" + yLevel);

            // Iterate X and Z within the current region's bounds
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location checkLoc = new Location(world, x, yLevel, z);
                    // IMPORTANT: Final check if the *specific* block location is allowed by WG
                    // This handles overlaps correctly. isEnchantAllowed checks the effective state at checkLoc.
                    if (worldGuardHook.isEnchantAllowed(checkLoc)) {
                        Block currentBlock = world.getBlockAt(checkLoc);
                        if (isBreakable(currentBlock, breakBedrock)) {
                            blocksToBreak.add(currentBlock); // Add to set
                        }
                    }
                }
            }
        } // End region loop
        // --- End Block Finding ---

        // --- Start Task ---
        if (!blocksToBreak.isEmpty()) {
            if(debug) logger.info("[Dbg][Disc] Found "+blocksToBreak.size()+" blocks within allowed region(s) for "+player.getName()+". Tasking...");
            new AreaBlockBreakTask(player, pickaxe, new ArrayList<>(blocksToBreak), breakBedrock, brokenBlock.getLocation(), "Disc", playerData, true).runTaskTimer(plugin, 1L, 1L);
        } else if(debug) {
            logger.info("[Dbg][Disc] 0 valid blocks found for "+player.getName()+" after region checks.");
        }
        // --- End Start Task ---
    }

    /**
     * Handles the Explosive enchantment activation. Finds blocks in a radius and starts a breaking task.
     */
    private void handleExplosive(Player p, Location c, ItemStack pick, int level, ConfigurationSection settings, BlockBreakEvent e, PlayerData pd) {
        final boolean debug = isDebugMode();
        // Null check settings
        if (settings == null) {
            logger.warning("[Explosive] Cannot activate for " + p.getName() + ": ConfigurationSection is null!");
            return;
        }
        boolean breakBedrock = settings.getBoolean("BreakBedrock", false);
        int actualRadius;
        int radiusTier = settings.getInt("RadiusTier", 2); // Default tier 2 (radius 3)

        // Determine radius based on tier
        switch (radiusTier) {
            case 1: actualRadius = 2; break;
            case 2: actualRadius = 3; break;
            case 3: actualRadius = 4; break;
            case 4: actualRadius = 5; break;
            case 5: actualRadius = 6; break;
            default:
                if (debug) logger.warning("[Debug][Explosive] Invalid RadiusTier " + radiusTier + " for " + p.getName() + ". Defaulting to 3.");
                actualRadius = 3; break;
        }

        // Don't proceed if radius is invalid
        if (actualRadius <= 0) {
            if(debug) logger.info("[Debug][Explosive] Radius " + actualRadius + " is invalid for " + p.getName() + ". No explosion.");
            return;
        }

        // --- Feedback ---
        if (pd.isShowEnchantMessages()) {
            ChatUtil.sendMessage(p, settings.getString("Message", "&6&lBoom! &eExplosive triggered!"));
        }
        if (pd.isShowEnchantSounds()) {
            playSoundAt(p, p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.0f);
        }
        // --- End Feedback ---

        if (debug) logger.info("[Debug][Explosive] Player " + p.getName() + ", Lvl " + level + " -> Radius: " + actualRadius);

        // Find blocks (checks WorldGuard internally via isEnchantAllowed)
        List<Block> blocksToBreak = findBlocksInRadius(c, actualRadius, breakBedrock, "Explosive");

        // Start Task
        if (!blocksToBreak.isEmpty()) {
            if (debug) logger.info("[Debug][Explosive] Tasking " + blocksToBreak.size() + " blocks for " + p.getName());
            new AreaBlockBreakTask(p, pick, blocksToBreak, breakBedrock, c, "Explosive", pd, true).runTaskTimer(plugin, 1L, 1L);
        } else if (debug) {
            logger.info("[Debug][Explosive] 0 blocks found for " + p.getName());
        }
    }

    /**
     * Handles the BlockBooster activation. Applies the booster effect to PlayerData.
     */
    private void handleBlockBoosterActivation(Player player, PlayerData playerData, int level, ConfigurationSection settings) {
        final boolean debug = isDebugMode();
        if (settings == null) {
            logger.warning("[BlockBooster] Cannot activate for " + player.getName() + ": ConfigurationSection is null!");
            return;
        }
        // Don't activate if already active
        if (playerData.isBlockBoosterActive()) {
            if (debug) logger.info("[Debug][BlockBooster] Already active for " + player.getName() + ". Skipping.");
            return;
        }

        // Load config values
        int durBase = settings.getInt("DurationBase", 60);
        int durInc = settings.getInt("DurationIncreasePerLevel", 10);
        double multBase = settings.getDouble("MultiplierBase", 1.1); // Default to 1.1x
        double multInc = settings.getDouble("MultiplierIncreasePerLevel", 0.1);

        // Calculate final stats
        int duration = Math.max(1, durBase + (durInc * Math.max(0, level - 1))); // Min 1 sec
        double multiplier = Math.max(1.01, multBase + (multInc * Math.max(0, level - 1))); // Min 1.01x

        if (debug) logger.info("[Debug][BlockBooster] Activating for " + player.getName() + "! Duration=" + duration + "s, Multiplier=x" + String.format("%.2f", multiplier));

        // Apply to PlayerData
        playerData.activateBlockBooster(duration, multiplier);
        // No need to save here, PlayerDataManager handles auto-saving

        // --- Feedback ---
        if (playerData.isShowEnchantMessages()) {
            String msgFmt = settings.getString("Message", "&d&lBooster! &fx%multiplier% Blocks Mined for %duration%s");
            String msg = ColorUtils.translateColors(msgFmt.replace("%multiplier%", String.format("%.1f", multiplier)).replace("%duration%", String.valueOf(duration)));
            ChatUtil.sendMessage(player, msg);
        }
        if (playerData.isShowEnchantSounds()) {
            playSoundAt(player, player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }
        // --- End Feedback ---
    }

    /**
     * Handles the Charity enchantment. Gives money to all online players.
     */
    private void handleCharity(Player activator, int level, ConfigurationSection settings, PlayerData activatorData) {
        final boolean debug = isDebugMode();
        if (settings == null || !vaultHook.isEnabled()) {
            if(debug) logger.info("[Debug][Charity] Skipped: Settings="+(settings==null)+" VaultEnabled="+vaultHook.isEnabled());
            return;
        }

        // Calculate reward (long)
        long minRewardBase = settings.getLong("RewardMinBase", 25L); // Use getLong
        long minRewardIncrease = settings.getLong("RewardMinIncreasePerLevel", 5L);
        long maxRewardBase = settings.getLong("RewardMaxBase", 75L);
        long maxRewardIncrease = settings.getLong("RewardMaxIncreasePerLevel", 15L);
        long minAmount = minRewardBase + (Math.max(0, level - 1) * minRewardIncrease);
        long maxAmount = maxRewardBase + (Math.max(0, level - 1) * maxRewardIncrease);

        final long amountToGive;
        if (maxAmount > minAmount) {
            long range = maxAmount - minAmount + 1;
            if (range <= 0) { amountToGive = minAmount; }
            else if (range > Integer.MAX_VALUE) { amountToGive = minAmount + (long)(random.nextDouble() * range); }
            else { amountToGive = minAmount + random.nextInt((int)range); }
        } else { amountToGive = minAmount; }


        if (amountToGive <= 0) {
            if (debug) logger.info("[Debug][Charity] Calculated amount " + amountToGive + " <= 0. Skipping.");
            return;
        }
        if (debug) logger.info("[Debug][Charity] Calculated amount per player: " + amountToGive);

        // Final vars for sync task
        final Player finalActivator = activator;
        final PlayerData finalActivatorData = activatorData;
        final String gaveFormat = settings.getString("MessageGave", "&dCharity! &fShared %amount% with %count% players!");
        final String receivedFormat = settings.getString("MessageReceived", "&dCharity! &fReceived %amount% from %player%!");
        final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US); // For display consistency

        // Run distribution synchronously
        runTaskSync(() -> {
            int givenCount = 0;
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            if(debug) logger.fine("[Debug][Charity Task] Distributing " + amountToGive + " to " + onlinePlayers.size() + " players...");

            for (Player recipient : onlinePlayers) {
                if (recipient != null && recipient.isOnline()) {
                    if (vaultHook.deposit(recipient, amountToGive)) {
                        givenCount++;
                        // Send message to recipient (if not activator and messages enabled)
                        if (!recipient.getUniqueId().equals(finalActivator.getUniqueId())) {
                            PlayerData recipientData = dataManager.getPlayerData(recipient.getUniqueId());
                            if (recipientData == null) { recipientData = dataManager.loadPlayerData(recipient.getUniqueId()); }
                            if (recipientData != null && recipientData.isShowEnchantMessages()) {
                                // Format using Vault's method for consistency
                                ChatUtil.sendMessage(recipient, receivedFormat.replace("%amount%", vaultHook.format(amountToGive)).replace("%player%", finalActivator.getName()));
                            }
                        }
                    } else if(debug) {
                        logger.warning("[Debug][Charity Task] Vault deposit FAILED for recipient: " + recipient.getName() + " (Amount: "+amountToGive+")");
                    }
                }
            } // End loop

            if(debug) logger.info("[Debug][Charity Task] Distribution complete. Given to " + givenCount + " players.");

            // Feedback to activator
            if (givenCount > 0) {
                if (finalActivatorData.isShowEnchantMessages()) {
                    // Format using Vault's method
                    ChatUtil.sendMessage(finalActivator, gaveFormat.replace("%amount%", vaultHook.format(amountToGive)).replace("%count%", String.valueOf(givenCount)));
                }
                if (finalActivatorData.isShowEnchantSounds()) {
                    playSoundAt(finalActivator, finalActivator.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                }
            }
        }); // End runTaskSync
    }

    /**
     * Handles the Blessing enchantment. Gives tokens to all online players.
     */
    private void handleBlessing(Player activator, int level, ConfigurationSection settings, PlayerData activatorData) {
        final boolean debug = isDebugMode();
        if (settings == null) {
            logger.warning("[Blessing] Cannot activate for " + activator.getName() + ": ConfigurationSection is null!");
            return;
        }

        // Calculate reward (long)
        long minRewardBase = settings.getLong("RewardMinBase", 5L);
        long minRewardIncrease = settings.getLong("RewardMinIncreasePerLevel", 1L);
        long maxRewardBase = settings.getLong("RewardMaxBase", 20L);
        long maxRewardIncrease = settings.getLong("RewardMaxIncreasePerLevel", 5L);
        long minAmount = minRewardBase + (Math.max(0, level - 1) * minRewardIncrease);
        long maxAmount = maxRewardBase + (Math.max(0, level - 1) * maxRewardIncrease);

        final long amountToGive;
        if (maxAmount > minAmount) {
            long range = maxAmount - minAmount + 1;
            if (range <= 0) { amountToGive = minAmount; }
            else if (range > Integer.MAX_VALUE) { amountToGive = minAmount + (long)(random.nextDouble() * range); }
            else { amountToGive = minAmount + random.nextInt((int)range); }
        } else { amountToGive = minAmount; }

        if (amountToGive <= 0) {
            if (debug) logger.info("[Debug][Blessing] Calculated amount " + amountToGive + " <= 0. Skipping.");
            return;
        }
        if (debug) logger.info("[Debug][Blessing] Calculated amount to give: " + amountToGive);

        // Final vars for task
        final Player finalActivator = activator;
        final PlayerData finalActivatorData = activatorData;
        final String msgGaveFormat = settings.getString("MessageGave", "&b&lBLESSED! &fYou shared %amount% Tokens with %count% players!");
        final String msgReceivedFormat = settings.getString("MessageReceived", "&b&lBLESSED! &fYou received %amount% Tokens from %player%!");
        final List<String> commands = settings.getStringList("Commands");
        final NumberFormat tokenFormat = NumberFormat.getNumberInstance(Locale.US); // For display

        // Run distribution synchronously
        runTaskSync(() -> {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            int givenCount = 0;
            if(debug) logger.fine("[Debug][Blessing Task] Distributing " + amountToGive + " tokens to " + onlinePlayers.size() + " players...");

            for (Player recipient : onlinePlayers) {
                if (recipient != null && recipient.isOnline()) {
                    PlayerData recipientData = dataManager.getPlayerData(recipient.getUniqueId());
                    if (recipientData == null) { recipientData = dataManager.loadPlayerData(recipient.getUniqueId()); }

                    if (recipientData != null) {
                        recipientData.addTokens(amountToGive);
                        dataManager.savePlayerData(recipientData, true); // Save async after adding
                        givenCount++;

                        // Send message to recipient
                        if (!recipient.getUniqueId().equals(finalActivator.getUniqueId()) && recipientData.isShowEnchantMessages()) {
                            ChatUtil.sendMessage(recipient, msgReceivedFormat.replace("%amount%", tokenFormat.format(amountToGive)).replace("%player%", finalActivator.getName()));
                        }
                    } else if(debug) {
                        logger.warning("[Debug][Blessing Task] Could not load PlayerData for recipient: " + recipient.getName());
                    }
                }
            } // End loop

            if(debug) logger.info("[Debug][Blessing Task] Distribution complete. Given tokens to " + givenCount + " players.");

            // Feedback to activator
            if (givenCount > 0) {
                if (finalActivatorData.isShowEnchantMessages()) {
                    ChatUtil.sendMessage(finalActivator, msgGaveFormat.replace("%amount%", tokenFormat.format(amountToGive)).replace("%count%", String.valueOf(givenCount)));
                }
                if (finalActivatorData.isShowEnchantSounds()) {
                    playSoundAt(finalActivator, finalActivator.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }
            }

            // Extra commands
            if (commands != null && !commands.isEmpty()) {
                if(debug) logger.fine("[Debug][Blessing Task] Executing " + commands.size() + " extra commands...");
                for (String cmd : commands) {
                    if (cmd == null || cmd.trim().isEmpty() || cmd.trim().toLowerCase().startsWith("say ")) continue;
                    String pCmd = cmd.replace("%player%", finalActivator.getName());
                    // PAPI processing could be added here
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pCmd);
                    if (debug) logger.fine("[Debug][Blessing Task] Extra cmd executed: " + pCmd);
                }
            }
        }); // End runTaskSync
    }

    // --- Redirects to Generic Handlers ---
    private void handleTokenator(Player p, int l, ConfigurationSection s, PlayerData pd) {
        handleGenericTokenEnchant(p, l, s, "Tokenator", pd);
    }
    private void handleKeyFinder(Player p, int l, ConfigurationSection s, PlayerData pd) {
        handleGenericCommandEnchant(p, l, s, "KeyFinder", pd);
    }
    private void handleVoucherFinder(Player p, int l, ConfigurationSection s, PlayerData pd) {
        handleGenericCommandEnchant(p, l, s, "VoucherFinder", pd);
    }
    private void handleSalary(Player player, int level, ConfigurationSection settings, PlayerData playerData) {
        handleGenericVaultEnchant(player, level, settings, "Salary", playerData); // Use Vault handler
    }
    // --- End Redirects ---


    /**
     * Generic handler for enchantments that give the activating player Vault money.
     */
    private void handleGenericVaultEnchant(Player player, int level, ConfigurationSection settings, String enchantName, PlayerData playerData) {
        final boolean debug = isDebugMode();
        // Prerequisites check
        if (settings == null || !vaultHook.isEnabled()) {
            if(debug) logger.info("[Debug][GenericVault: "+enchantName+"] Skipped: Settings="+(settings==null)+" VaultEnabled="+vaultHook.isEnabled());
            return;
        }

        // Calculate reward (long)
        long minRewardBase = settings.getLong("RewardMinBase", 10L); // Adjusted default
        long minRewardIncrease = settings.getLong("RewardMinIncreasePerLevel", 2L);
        long maxRewardBase = settings.getLong("RewardMaxBase", 50L);
        long maxRewardIncrease = settings.getLong("RewardMaxIncreasePerLevel", 5L);
        long minAmount = minRewardBase + (Math.max(0, level - 1) * minRewardIncrease);
        long maxAmount = maxRewardBase + (Math.max(0, level - 1) * maxRewardIncrease);

        final long amountToGive;
        if (maxAmount > minAmount) {
            long range = maxAmount - minAmount + 1;
            if (range <= 0) { amountToGive = minAmount; }
            else if (range > Integer.MAX_VALUE) { amountToGive = minAmount + (long)(random.nextDouble() * range); }
            else { amountToGive = minAmount + random.nextInt((int)range); }
        } else { amountToGive = minAmount; }

        if (amountToGive <= 0) {
            if (debug) logger.info("[Debug][GenericVault: "+enchantName+"] Calculated amount " + amountToGive + " <= 0. Skipping.");
            return;
        }
        if (debug) logger.info("[Debug][GenericVault: "+enchantName+"] Calculated amount to give: " + amountToGive);

        // Final vars for sync task
        final Player finalPlayer = player;
        final PlayerData finalPlayerData = playerData;
        final String messageFormat = settings.getString("Message", "&aYour " + enchantName + " gave you +%amount%!");
        final String finalEnchantName = enchantName; // For debug

        // Run deposit and feedback on main thread
        runTaskSync(() -> {
            if (vaultHook.deposit(finalPlayer, amountToGive)) {
                // Feedback
                if (finalPlayerData.isShowEnchantMessages()) {
                    // Format using Vault's method
                    ChatUtil.sendMessage(finalPlayer, messageFormat.replace("%amount%", vaultHook.format(amountToGive)));
                }
                if (finalPlayerData.isShowEnchantSounds()) {
                    playSoundAt(finalPlayer, finalPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
                }
                // Extra Commands (if any)
                List<String> commands = settings.getStringList("Commands");
                if (commands != null && !commands.isEmpty()) {
                    if(debug) logger.fine("[Debug][GenericVault Task: "+finalEnchantName+"] Executing " + commands.size() + " extra commands...");
                    for (String cmd : commands) {
                        if (cmd == null || cmd.trim().isEmpty() || cmd.trim().toLowerCase().startsWith("say ")) continue;
                        String pCmd = cmd.replace("%player%", finalPlayer.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pCmd);
                        if (debug) logger.fine("[Debug][GenericVault Task: "+finalEnchantName+"] Extra cmd executed: " + pCmd);
                    }
                }
            } else if(debug) {
                logger.warning("[Debug][GenericVault Task: "+finalEnchantName+"] Vault deposit FAILED for " + finalPlayer.getName() + " (Amount: " + amountToGive + ")");
            }
        }); // End runTaskSync
    }

    /**
     * Generic handler for enchantments that give the activating player tokens.
     */
    private void handleGenericTokenEnchant(Player player, int level, ConfigurationSection settings, String enchantName, PlayerData playerData) {
        final boolean debug = isDebugMode();
        if (settings == null) {
            if(debug) logger.info("[Debug][GenericToken: "+enchantName+"] Settings null for " + player.getName());
            return;
        }

        // Calculate reward (long)
        long minRewardBase = settings.getLong("RewardMinBase", 5L);
        long minRewardIncrease = settings.getLong("RewardMinIncreasePerLevel", 2L);
        long maxRewardBase = settings.getLong("RewardMaxBase", 15L);
        long maxRewardIncrease = settings.getLong("RewardMaxIncreasePerLevel", 6L);
        long minAmount = minRewardBase + (Math.max(0, level - 1) * minRewardIncrease);
        long maxAmount = maxRewardBase + (Math.max(0, level - 1) * maxRewardIncrease);

        final long amountToGive;
        if (maxAmount > minAmount) {
            long range = maxAmount - minAmount + 1;
            if (range <= 0) { amountToGive = minAmount; }
            else if (range > Integer.MAX_VALUE) { amountToGive = minAmount + (long)(random.nextDouble() * range); }
            else { amountToGive = minAmount + random.nextInt((int)range); }
        } else { amountToGive = minAmount; }

        if (amountToGive <= 0) {
            if (debug) logger.info("[Debug][GenericToken: "+enchantName+"] Calculated amount " + amountToGive + " <= 0. Skipping.");
            return;
        }
        if (debug) logger.info("[Debug][GenericToken: "+enchantName+"] Calculated amount to give: " + amountToGive);

        // Final vars for task
        final Player finalPlayer = player;
        final PlayerData finalPlayerData = playerData; // Pass the existing data reference
        final String messageFormat = settings.getString("Message", "&aYour " + enchantName + " gave you %amount% Tokens!");
        final String finalEnchantName = enchantName;
        final NumberFormat tokenFormat = NumberFormat.getNumberInstance(Locale.US);

        // Run token addition, save, feedback, and commands synchronously
        runTaskSync(() -> {
            finalPlayerData.addTokens(amountToGive);
            // Save asynchronously AFTER adding tokens
            dataManager.savePlayerData(finalPlayerData, true);

            // Feedback
            if (finalPlayerData.isShowEnchantMessages()) {
                ChatUtil.sendMessage(finalPlayer, messageFormat.replace("%amount%", tokenFormat.format(amountToGive)));
            }
            if (finalPlayerData.isShowEnchantSounds()) {
                playSoundAt(finalPlayer, finalPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            }

            // Extra Commands
            List<String> commands = settings.getStringList("Commands");
            if (commands != null && !commands.isEmpty()) {
                if(debug) logger.fine("[Debug][GenericToken Task: "+finalEnchantName+"] Executing " + commands.size() + " extra commands...");
                for (String cmd : commands) {
                    if (cmd == null || cmd.trim().isEmpty() || cmd.trim().toLowerCase().startsWith("say ")) continue;
                    String pCmd = cmd.replace("%player%", finalPlayer.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pCmd);
                    if (debug) logger.fine("[Debug][GenericToken Task: "+finalEnchantName+"] Extra cmd executed: " + pCmd);
                }
            }
        });
    }

    /**
     * Generic handler for enchantments that run console commands.
     */
    private void handleGenericCommandEnchant(Player player, int level, ConfigurationSection settings, String enchantName, PlayerData playerData) {
        final boolean debug = isDebugMode();
        if (settings == null) {
            if(debug) logger.info("[Debug][GenericCommand: "+enchantName+"] Settings null for " + player.getName());
            return;
        }
        List<String> commandsToExecute = settings.getStringList("Commands");
        if (commandsToExecute == null || commandsToExecute.isEmpty()) {
            if(debug) logger.info("[Debug][GenericCommand: "+enchantName+"] No commands found for " + player.getName());
            return;
        }

        // Final vars for task
        final Player finalPlayer = player;
        final PlayerData finalPlayerData = playerData;
        final String finalMessage = settings.getString("Message", "&aYour " + enchantName + " activated!");
        final String finalEnchantName = enchantName;

        // Process commands immediately (replace %player%)
        final List<String> processedCommands = new ArrayList<>();
        for (String cmd : commandsToExecute) {
            if (cmd != null && !cmd.trim().isEmpty()) { // Check not null/empty
                processedCommands.add(cmd.replace("%player%", finalPlayer.getName()));
            }
        }

        if (processedCommands.isEmpty()) {
            if(debug) logger.info("[Debug][GenericCommand: "+enchantName+"] Processed command list is empty.");
            return;
        }
        if(debug) logger.fine("[Debug][GenericCommand: "+enchantName+"] Processed commands: " + processedCommands);

        // Run commands and feedback synchronously
        runTaskSync(() -> {
            if(debug) logger.fine("[Debug][GenericCommand Task: "+finalEnchantName+"] Task running for " + finalPlayer.getName());
            // Execute Commands
            for (String pCmd : processedCommands) {
                if (pCmd != null && !pCmd.trim().isEmpty()) { // Final null/empty check
                    if(debug) logger.fine("[Dbg][GenericCommand Task: "+finalEnchantName+"] Executing: " + pCmd);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pCmd);
                }
            }

            // Feedback
            if (finalPlayerData.isShowEnchantMessages()) {
                ChatUtil.sendMessage(finalPlayer, ColorUtils.translateColors(finalMessage)); // MessageManager handles color
            }
            if (finalPlayerData.isShowEnchantSounds()) {
                // Determine sound based on enchant name
                Sound s = Sound.ENTITY_ITEM_PICKUP; // Default
                if (enchantName.equalsIgnoreCase("KeyFinder")) s = Sound.BLOCK_ENDER_CHEST_OPEN; // Changed sound
                else if (enchantName.equalsIgnoreCase("VoucherFinder")) s = Sound.ENTITY_VILLAGER_TRADE;
                playSoundAt(finalPlayer, finalPlayer.getLocation(), s, 1.0f, 1.2f);
            }
        });
    }


    /**
     * Checks if a block is considered breakable by the plugin's logic.
     * @param block The block to check.
     * @param allowBedrock Whether bedrock should be considered breakable.
     * @return true if breakable, false otherwise.
     */
    private boolean isBreakable(Block block, boolean allowBedrock){
        if(block == null) return false;
        Material t = block.getType();
        // More robust check for air types
        if (t == Material.AIR || t == Material.CAVE_AIR || t == Material.VOID_AIR) return false;
        // Bedrock check
        if (t == Material.BEDROCK && !allowBedrock) return false;
        // Add other unbreakable/technical blocks
        String name = t.toString();
        // Consider adding container blocks if they shouldn't be broken by area effects?
        return !(name.contains("COMMAND_BLOCK") ||
                t == Material.BARRIER || t == Material.LIGHT ||
                t == Material.STRUCTURE_BLOCK || t == Material.STRUCTURE_VOID ||
                t == Material.JIGSAW || name.contains("PORTAL") ||
                t == Material.END_GATEWAY || t == Material.END_PORTAL_FRAME ||
                t == Material.SPAWNER
        );
    }

    /**
     * Finds blocks within a spherical radius, checking WorldGuard permissions.
     * @param center Center location of the sphere.
     * @param radius Radius of the sphere.
     * @param breakBedrock Whether to include bedrock.
     * @param enchantName Name of the enchant calling this (for debugging).
     * @return List of breakable and allowed blocks.
     */
    private List<Block> findBlocksInRadius(Location center, int radius, boolean breakBedrock, String enchantName) {
        List<Block> blocks = new ArrayList<>();
        World w = center.getWorld();
        if (w == null || radius < 0) return blocks; // Invalid input

        int cX = center.getBlockX(); int cY = center.getBlockY(); int cZ = center.getBlockZ();
        final double radiusSquared = (double) radius * radius + 0.01; // Epsilon for edge blocks

        // Iterate through a cube surrounding the center point
        for (int x = cX - radius; x <= cX + radius; x++) {
            for (int y = cY - radius; y <= cY + radius; y++) {
                // World height boundary check
                if (y < w.getMinHeight() || y >= w.getMaxHeight()) continue;
                for (int z = cZ - radius; z <= cZ + radius; z++) {
                    // Check distance squared for spherical shape
                    double distSq = square(x - cX) + square(y - cY) + square(z - cZ);
                    if (distSq <= radiusSquared) {
                        // Skip the absolute center block unless it's Nuke
                        if (x == cX && y == cY && z == cZ && !enchantName.equalsIgnoreCase("NukeTNT")) {
                            continue;
                        }
                        Location checkLoc = new Location(w, x, y, z);
                        // Check WorldGuard permission *first* - potentially saves block lookups
                        if (worldGuardHook.isEnchantAllowed(checkLoc)) {
                            Block bl = w.getBlockAt(checkLoc); // Get block only if WG allows
                            if (isBreakable(bl, breakBedrock)) { // Check if materially breakable
                                blocks.add(bl);
                            }
                        }
                    }
                }
            }
        }
        return blocks;
    }

    /** Helper for squaring doubles. */
    private static double square(double val) { return val * val; }

    /** Helper to run tasks synchronously and safely. */
    private void runTaskSync(Runnable task){
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            if(isDebugMode()) logger.warning("[RunTaskSync] Plugin disabled, task not scheduled.");
        }
    }

    /**
     * Notifies that a Nuke process has completed or been aborted. Cleans up player state.
     * @param playerUUID The UUID of the player whose Nuke finished.
     * @param aborted True if the Nuke was cancelled/failed, false if it completed normally (even if 0 blocks broken).
     */
    private void notifyNukeComplete(UUID playerUUID, boolean aborted) {
        final boolean debug = isDebugMode();
        // Always remove BossBar if present
        BossBar nukeBossBar = activeNukeBossBars.remove(playerUUID);
        if (nukeBossBar != null) {
            try { nukeBossBar.removeAll(); } catch (Exception ignore) {}
            if(debug) logger.fine("[Dbg][NukeNotify] Removed BossBar for " + playerUUID);
        }

        // Try to remove player from active set - returns true if they were present
        if (nukeActivePlayers.remove(playerUUID)) {
            Player player = Bukkit.getPlayer(playerUUID); // Get player instance if online
            if (player != null && player.isOnline()) {
                // --- Reset Title ---
                try { player.resetTitle(); } catch (Exception ignore) {}
                if(debug) logger.info("[Dbg][NukeNotify] Reset title for " + player.getName());
                // --- End Reset Title ---

                // Send completion message
                String messageKey = aborted ? "listeners.nuke.complete_aborted" : "listeners.nuke.complete";
                String defaultMessage = aborted ? "&cNuke aborted." : "&aNuke complete!";
                ChatUtil.sendMessage(player, messageManager.getMessage(messageKey, defaultMessage));
                if (debug) logger.info("[Dbg][NukeNotify] Sent completion message ('" + messageKey + "') to " + player.getName());
            } else {
                // Player was in set but is now offline
                if (debug) logger.info("[Dbg][NukeNotify] Player " + playerUUID + " removed from active Nuke set but is offline.");
            }
            // Clear block count tracker for the player (implement this if needed)
            clearActiveBlockCount(playerUUID);
        } else {
            // Player wasn't in the set when notify was called (e.g., called twice, or cleaned up by quit listener first)
            if (debug) logger.info("[Dbg][NukeNotify] notifyNukeComplete called for " + playerUUID + ", but they were not in the active set.");
        }
    }

    // --- Placeholder methods for tracking block counts (implement if needed) ---
    private long getActiveBlockCountFromLastNuke(UUID uuid) { return 0; /* TODO: Implement tracking if needed */ }
    private void clearActiveBlockCount(UUID uuid) { /* TODO: Implement tracking if needed */ }
    // --- End Placeholder methods ---


    // --- Helper Methods for Effects ---
    private void playSoundAt(Player player, Location location, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline() && location != null && location.getWorld() != null && sound != null) {
            try {
                player.playSound(location, sound, SoundCategory.PLAYERS, volume, pitch);
            } catch (Exception e) {
                if(isDebugMode()) logger.log(Level.WARNING, "[Sound] Error playing sound " + sound.name() + " for " + player.getName(), e);
            }
        }
    }

    private void sendTitleToPlayer(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player != null && player.isOnline()) {
            try {
                player.sendTitle(ColorUtils.translateColors(title), ColorUtils.translateColors(subtitle), fadeIn, stay, fadeOut);
            } catch (Exception e) {
                if(isDebugMode()) logger.log(Level.WARNING, "[Title] Error sending title to " + player.getName(), e);
            }
        }
    }

    private void spawnParticleEffect(World world, Particle particle, Location location, int count, double spread, @Nullable Particle.DustOptions options) {
        if (world != null && particle != null && location != null && count > 0) {
            try {
                if (options != null && particle == Particle.REDSTONE) {
                    world.spawnParticle(particle, location, count, spread, spread, spread, 0, options);
                } else {
                    world.spawnParticle(particle, location, count, spread, spread, spread, 0.1); // Use speed for non-dust
                }
            } catch (Exception e) {
                if(isDebugMode()) logger.log(Level.WARNING, "[Particle] Error spawning particle " + particle.name() + " at " + location, e);
            }
        }
    }
    // --- End Helper Methods for Effects ---



    /**
     * Inner class to handle breaking multiple blocks over several ticks for area-effect enchants.
     */
    private class AreaBlockBreakTask extends BukkitRunnable {
        private final Player player; // Player who triggered the effect
        private final ItemStack pickaxe; // Pickaxe used (for drop calculations if needed)
        private final Queue<Block> remainingBlocks; // Use Queue for efficient processing order
        private final boolean breakBedrock;
        private final Location effectLocation; // Origin/center of the effect for sounds/particles
        private final String enchantName; // Name of the enchant (for logging)
        private final PlayerData taskPlayerData; // Snapshot of PlayerData when task started
        private final boolean debug;
        private final UUID playerUUID; // Store UUID for safety checks
        private final boolean directSetToAir; // True for Nuke/Disc, false for Explosive? (Or always true?)

        public AreaBlockBreakTask(Player p, ItemStack pick, List<Block> blocks, boolean bb, Location el, String name, PlayerData pd, boolean directSetToAir){
            this.player = p;
            this.pickaxe = pick;
            this.remainingBlocks = new LinkedList<>(blocks); // Use LinkedList as a Queue
            this.breakBedrock = bb;
            this.effectLocation = el; // Store center location
            this.enchantName = name;
            this.taskPlayerData = pd; // Use the data snapshot
            this.debug = isDebugMode();
            this.playerUUID = p.getUniqueId();
            this.directSetToAir = directSetToAir; // If true, bypasses breakNaturally
            if(debug) BlockBreakListener.this.logger.info("[AreaTask:"+name+"] Created for " + p.getName() + " with " + remainingBlocks.size() + " blocks. DirectAir: " + directSetToAir);
        }

        @Override
        public void run(){
            // --- Pre-execution Checks ---
            if (player == null || !player.isOnline()) {
                handleCompletion(true); // Aborted
                if (debug) logger.info("[Dbg][" + enchantName + " Task] Cancelled for " + playerUUID + ": Player offline.");
                return;
            }
            // If Nuke, ensure player is still marked active
            if (enchantName.equalsIgnoreCase("NukeTNT") && !nukeActivePlayers.contains(playerUUID)) {
                handleCompletion(true); // Aborted (state cleared elsewhere)
                if (debug) logger.info("[Dbg][" + enchantName + " Task] Cancelled for " + playerUUID + ": Player no longer in active nuke set.");
                return;
            }
            //--- End Pre-execution Checks ---

            int processedThisTick = 0;
            long startTickTime = System.nanoTime(); // For tick time limiting
            long timeLimit = BlockBreakListener.MAX_NANOS_PER_TICK;

            // Process blocks until limit reached or queue empty
            while (!remainingBlocks.isEmpty() && processedThisTick < BlockBreakListener.MAX_BLOCKS_PER_TICK) {
                // Check time limit inside the loop
                if (System.nanoTime() - startTickTime > timeLimit) {
                    if(debug) logger.warning("[Dbg][" + enchantName + " Task] Tick time limit (" + (timeLimit/1_000_000.0) + "ms) exceeded for " + player.getName() + ". Processed: " + processedThisTick +". Remaining: " + remainingBlocks.size());
                    break; // Stop processing for this tick
                }

                Block b = remainingBlocks.poll(); // Get and remove next block from queue
                if (b == null) continue; // Should not happen with !isEmpty check, but safety

                // Check if block is still valid and allowed *at the time of processing*
                if (BlockBreakListener.this.isBreakable(b, breakBedrock) &&
                        !b.hasMetadata(BlockBreakListener.METADATA_ENCHANT_BREAK))
                {
                    Material originalMaterial = b.getType(); // Get material *before* breaking
                    b.setMetadata(BlockBreakListener.METADATA_ENCHANT_BREAK, new FixedMetadataValue(BlockBreakListener.this.plugin, true));

                    ProcessResult res = ProcessResult.IGNORED;
                    try {
                        // Process sell/pickup/count for this block
                        // Pass null for event as this is tasked
                        res = BlockBreakListener.this.processSingleBlockBreak(player, b, originalMaterial, pickaxe, null, taskPlayerData);
                    } catch (Exception singleProcEx) {
                        logger.log(Level.SEVERE, "[AreaTask:"+enchantName+"] Exc in processSingleBlockBreak for "+originalMaterial, singleProcEx);
                        res = ProcessResult.FAILED; // Mark as failed
                    }

                    // Break/Set Air *after* processing drops
                    if (res != ProcessResult.FAILED) {
                        try {
                            if (directSetToAir) { // Nuke/Disc logic
                                if (b.getType() != Material.AIR) b.setType(Material.AIR, false); // Apply physics later if needed
                                if(debug && processedThisTick < 3) logger.finest("[AreaTask:"+enchantName+"] Set AIR (Direct): " + originalMaterial);
                            } else { // Explosive logic (or others)
                                // Only break naturally if drops weren't handled (sold/picked up)
                                if (res != ProcessResult.SOLD && res != ProcessResult.PICKED_UP) {
                                    if (!b.breakNaturally(pickaxe)) {
                                        // Log failure, but don't necessarily stop the whole task
                                        if(debug) BlockBreakListener.this.logger.warning("[AreaTask:"+enchantName+"] breakNaturally FAILED for " + originalMaterial + " at " + b.getLocation());
                                    } else {
                                        if(debug && processedThisTick < 3) logger.finest("[AreaTask:"+enchantName+"] Broke Naturally: " + originalMaterial);
                                    }
                                } else {
                                    // If sold/picked up, ensure it's air now
                                    if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                                    if(debug && processedThisTick < 3) logger.finest("[AreaTask:"+enchantName+"] Set AIR (Sold/Pickup): " + originalMaterial);
                                }
                            }
                        } catch (Exception breakEx) {
                            // Log error but continue processing other blocks
                            logger.log(Level.WARNING, "[AreaTask:"+enchantName+"] Error breaking/setting block " + originalMaterial + " at " + b.getLocation() + ": " + breakEx.getMessage());
                        }
                    } // end if !failed

                    // Schedule metadata removal (run shortly after, handles cases where block disappears)
                    Bukkit.getScheduler().runTaskLater(BlockBreakListener.this.plugin, () -> {
                        try { if (b.hasMetadata(BlockBreakListener.METADATA_ENCHANT_BREAK)) b.removeMetadata(BlockBreakListener.METADATA_ENCHANT_BREAK, BlockBreakListener.this.plugin); }
                        catch (Exception ignore) {} // Ignore if block no longer exists
                    }, 1L);

                    processedThisTick++;
                } else {
                    // Log why block was skipped if debugging
                    if(debug && processedThisTick < 5) {
                        String skipReason = !BlockBreakListener.this.isBreakable(b, breakBedrock) ? "Not Breakable" :
                                (b.hasMetadata(BlockBreakListener.METADATA_ENCHANT_BREAK) ? "Has Metadata" :
                                        (!BlockBreakListener.this.worldGuardHook.isEnchantAllowed(b.getLocation()) ? "WG Denied" : "Unknown"));
                        logger.finest("[AreaTask:"+enchantName+"] Skipped block " + b.getType() + " Reason: " + skipReason);
                    }
                }
            } // End while loop

            // --- Tick Effects (Optional - maybe only on first/last tick?) ---
            // Moved effects inside the loop to potentially spread them out or reduce frequency
            // Example: Play sound only if blocks were processed this tick
            if (processedThisTick > 0 && effectLocation != null && effectLocation.getWorld() != null) {
                // (Sound/Particle logic similar to previous version, maybe less frequent)
            }
            // --- End Tick Effects ---

            if (debug) {
                double timeMs = (System.nanoTime() - startTickTime) / 1_000_000.0;
                BlockBreakListener.this.logger.fine("[Dbg][" + enchantName + " Task Tick] P: " + player.getName() + " | Proc: " + processedThisTick + " | Rem: " + remainingBlocks.size() + " | Time: " + String.format("%.3f", timeMs) + " ms");
            }

            // --- Completion Check ---
            if (remainingBlocks.isEmpty()) {
                handleCompletion(false); // Normal completion
                if (debug) BlockBreakListener.this.logger.info("[Dbg][" + enchantName + "] Task Finished for " + player.getName() + ".");
            }
            // --- End Completion Check ---
        } // End run()

        // Helper to handle task completion and cleanup
        private void handleCompletion(boolean aborted) {
            try {
                this.cancel(); // Stop the task
            } catch (IllegalStateException ignore) {}

            // Notify Nuke completion if applicable
            if (enchantName.equalsIgnoreCase("NukeTNT")) {
                // Run notification on main thread
                runTaskSync(() -> BlockBreakListener.this.notifyNukeComplete(playerUUID, aborted)); // Pass true if aborted or no blocks left
            }
        }

    } // End AreaBlockBreakTask class
} // End BlockBreakListener class