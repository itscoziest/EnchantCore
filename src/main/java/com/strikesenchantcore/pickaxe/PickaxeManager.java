package com.strikesenchantcore.pickaxe;

// Added necessary imports
import com.strikesenchantcore.config.MessageManager; // Not directly used here, but good practice
import com.strikesenchantcore.util.ChatUtil; // Not directly used here
import org.bukkit.Sound; // Not directly used here
// --- Keep existing imports ---
import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.PickaxeConfig;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.enchants.EnchantRegistry;
import com.strikesenchantcore.util.ColorUtils;
import com.strikesenchantcore.config.SkinConfig;
import com.strikesenchantcore.util.PDCUtil;
import com.strikesenchantcore.util.PapiHook;
import me.clip.placeholderapi.PlaceholderAPI; // Keep for PapiHook usage
import com.strikesenchantcore.util.ItemsAdderUtil;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;


// --- ADDED/KEPT IMPORTS ---
import java.util.ArrayList;
import java.util.Collection; // <<<--- ADDED THIS IMPORT
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator; // For potential sorting
import java.util.logging.Level;
import java.util.logging.Logger; // Use Logger
import java.util.stream.Collectors;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;
// --- END IMPORTS ---

public class PickaxeManager {

    private final EnchantCore plugin;
    private final Logger logger;
    private final ItemsAdderUtil itemsAdderUtil;// Cache logger
    // Cache NumberFormat instance - reuse it
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

    public PickaxeManager(EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger(); // Get logger instance
        this.itemsAdderUtil = plugin.getItemsAdderUtil(); // Initialize ItemsAdder utility
    }

    /**
     * Checks if the player has enough blocks mined to level up the pickaxe.
     * If so, increments the level, updates PlayerData and the pickaxe's PDC,
     * sends level-up messages/sounds, and processes level-up rewards.
     *
     * @param player     The online player (used for messages, sounds, rewards). Can be null if checking offline data.
     * @param playerData The player's data object.
     * @param pickaxe    The pickaxe ItemStack being checked/updated.
     * @return True if the pickaxe leveled up at least once, false otherwise.
     */
    public boolean checkForLevelUp(@Nullable Player player, @NotNull PlayerData playerData, @NotNull ItemStack pickaxe) {
        // Ensure it's our pickaxe first
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) {
            return false;
        }

        final boolean debug = plugin.getConfigManager().isDebugMode();
        int currentLevel = playerData.getPickaxeLevel(); // Level *before* potential level up
        long blocksMined = playerData.getBlocksMined();
        int maxLevel = plugin.getPickaxeConfig().getMaxLevel(); // Get max level once
        boolean leveledUpOverall = false;

        if (maxLevel > 0 && currentLevel >= maxLevel) {
            return false; // Already at max level
        }

        long blocksRequiredForNextLevel = getBlocksRequiredForLevel(currentLevel + 1);

        // Loop in case multiple level ups occurred from added blocks
        while (blocksRequiredForNextLevel != Long.MAX_VALUE && blocksMined >= blocksRequiredForNextLevel) {
            // Double check max level inside loop
            if (maxLevel > 0 && currentLevel >= maxLevel) {
                if (debug) logger.fine("[LevelUpCheck] Reached max level ("+maxLevel+") during multi-level check for " + playerData.getPlayerUUID());
                break;
            }

            currentLevel++; // Increment to the new level reached
            playerData.setPickaxeLevel(currentLevel); // Update PlayerData
            PDCUtil.setPickaxeLevel(pickaxe, currentLevel); // Update item PDC
            leveledUpOverall = true; // Mark that a level up occurred

            if(debug) logger.info("[LevelUpCheck] Pickaxe leveled up to "+currentLevel+" for " + playerData.getPlayerUUID());

            // --- Process Rewards for the NEW level reached ---
            if (player != null && player.isOnline()) { // Only process rewards if player is online
                try {
                    processLevelUpRewards(player, currentLevel); // Pass the new level
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error processing level up rewards for level " + currentLevel + " for player " + player.getName(), e);
                }
            }
            // --- End Rewards Processing ---

            // Save data (asynchronously)
            plugin.getPlayerDataManager().savePlayerData(playerData, true);

            // Send standard level up message/sound (if player is online)
            if (player != null && player.isOnline()) {
                sendLevelUpFeedback(player, currentLevel);
            }

            // Check requirement for the *next* potential level for the loop condition
            if (maxLevel > 0 && currentLevel >= maxLevel) {
                if (debug) logger.fine("[LevelUpCheck] Reached max level ("+maxLevel+") after processing level " + currentLevel);
                break; // Exit loop after processing final level rewards
            }
            blocksRequiredForNextLevel = getBlocksRequiredForLevel(currentLevel + 1);
        } // End while loop

        return leveledUpOverall; // Return true if any level up happened
    }

    /**
     * Sends the level-up message and sound to the player. Runs synchronously.
     * @param player The player.
     * @param newLevel The level they just reached.
     */
    private void sendLevelUpFeedback(Player player, int newLevel) {
        // Ensure this runs on the main thread for Bukkit API calls
        if (!plugin.isEnabled()) return; // Safety check
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player == null || !player.isOnline()) return; // Check again inside task

            MessageManager messageManager = plugin.getMessageManager();
            String levelUpMsgFormat = messageManager.getMessage("pickaxe.level_up", "&a&lLEVEL UP! &7Your pickaxe reached level &e%level%&7!");
            String levelUpMsg = levelUpMsgFormat.replace("%level%", String.valueOf(newLevel));

            // Send message
            player.sendMessage(levelUpMsg); // MessageManager handles color

            // Play sound
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error playing level up sound for " + player.getName(), e);
            }
        });
    }


    /**
     * Determines and dispatches level-up rewards based on configuration.
     * @param player The player who leveled up.
     * @param newLevel The new level the player reached.
     */
    private void processLevelUpRewards(Player player, int newLevel) {
        PickaxeConfig pConfig = plugin.getPickaxeConfig();
        if (pConfig == null) {
            logger.warning("[LevelReward] Cannot process rewards: PickaxeConfig is null!");
            return;
        }
        final boolean debug = plugin.getConfigManager().isDebugMode();

        // 1. Check Every Level Rewards
        if (pConfig.isLevelRewardsEveryEnable()) {
            if (debug) logger.info("[Debug][LevelReward] Checking 'every-level' rewards for level " + newLevel);
            dispatchLevelUpRewards(player, newLevel, pConfig.getLevelRewardsEveryMessage(), pConfig.getLevelRewardsEveryCommands());
        }

        // 2. Check Milestone Rewards
        int milestoneInterval = pConfig.getLevelRewardsMilestoneInterval();
        if (pConfig.isLevelRewardsMilestoneEnable() && milestoneInterval > 0 && newLevel % milestoneInterval == 0) {
            if (debug) logger.info("[Debug][LevelReward] Checking 'milestone' rewards (Interval: " + milestoneInterval + ") for level " + newLevel);
            dispatchLevelUpRewards(player, newLevel, pConfig.getLevelRewardsMilestoneMessage(), pConfig.getLevelRewardsMilestoneCommands());
        }

        // 3. Check Specific Level Rewards
        if (pConfig.isLevelRewardsSpecificEnable()) {
            Map<Integer, PickaxeConfig.LevelRewardData> specificMap = pConfig.getLevelRewardsSpecificLevelsMap();
            PickaxeConfig.LevelRewardData rewardData = specificMap.get(newLevel); // Get data for this specific level
            if (rewardData != null) { // Check if a reward is defined for this level
                if (debug) logger.info("[Debug][LevelReward] Checking 'specific-levels' rewards for level " + newLevel);
                // Use specific message if defined, otherwise use the default specific message
                String messageToSend = (rewardData.message != null && !rewardData.message.isEmpty())
                        ? rewardData.message
                        : pConfig.getLevelRewardsSpecificDefaultMessage(); // Fallback to default specific message
                dispatchLevelUpRewards(player, newLevel, messageToSend, rewardData.commands);
            }
        }
    }

    /**
     * Executes the commands and sends the message for a level-up reward.
     * Runs synchronously on the main thread.
     * @param player The player receiving the reward.
     * @param level The level reached.
     * @param messageFormat The message format string (can be null or empty).
     * @param commands The list of commands to execute (can be null or empty).
     */
    private void dispatchLevelUpRewards(Player player, int level, @Nullable String messageFormat, @Nullable List<String> commands) {
        // Ensure execution on the main thread
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player == null || !player.isOnline()) return; // Final check

            final boolean debug = plugin.getConfigManager().isDebugMode();

            // Send Message
            if (messageFormat != null && !messageFormat.isEmpty()) {
                final String message = messageFormat.replace("%player%", player.getName())
                        .replace("%level%", String.valueOf(level));
                // ChatUtil handles PAPI if needed and color translation
                ChatUtil.sendMessage(player, message);
            }

            // Execute Commands
            if (commands != null && !commands.isEmpty()) {
                PapiHook papi = plugin.getPapiHook(); // Get PAPI hook instance once
                boolean papiEnabled = papi != null && papi.isHooked();

                for (String command : commands) {
                    if (command == null || command.trim().isEmpty()) continue; // Skip empty commands

                    String processedCommand = command.replace("%player%", player.getName())
                            .replace("%level%", String.valueOf(level));

                    // Apply PAPI placeholders if enabled
                    if (papiEnabled) {
                        try {
                            processedCommand = PlaceholderAPI.setPlaceholders(player, processedCommand);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "PAPI Error processing reward command '" + command + "' for " + player.getName(), e);
                        }
                    }

                    // Dispatch command via console
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                    if (debug) {
                        logger.info("[Debug][LevelReward] Executed: '" + processedCommand + "' for " + player.getName() + " at level " + level);
                    }
                }
            }
        });
    }


    /**
     * Creates a new EnchantCore pickaxe with the specified initial level and blocks mined count.
     * Uses default configuration values from PickaxeConfig.
     * @param level Initial level.
     * @param blocksMined Initial blocks mined count.
     * @return The created ItemStack, or null on error.
     */
    @Nullable
    public ItemStack createPickaxe(int level, long blocksMined) {
        logger.log(Level.FINER, "[EnchantCore][CreatePickaxe] Attempting (Level: " + level + ", Blocks: " + blocksMined + ")");
        PickaxeConfig pConfig = plugin.getPickaxeConfig();
        if (pConfig == null) {
            logger.log(Level.SEVERE, "[EnchantCore][Error] PickaxeConfig is null during createPickaxe!");
            return null;
        }
        Material material = pConfig.getPickaxeMaterial();
        String itemsAdderID = pConfig.getPickaxeItemsAdderID();

// DEBUG: Print what we're trying to create
        logger.info("DEBUG: Creating pickaxe with Material: " + material + ", ItemsAdder ID: " + itemsAdderID);

// Create ItemStack using ItemsAdder if available, otherwise vanilla material
        logger.info("DEBUG: Creating first join pickaxe with Material: " + material + ", ItemsAdder ID: " + itemsAdderID);

        ItemStack pickaxe = itemsAdderUtil.createItemStack(material.name(), itemsAdderID);
        if (pickaxe == null) {
            logger.log(Level.SEVERE, "[EnchantCore][Error] Failed to create pickaxe ItemStack! Material: " + material + ", ItemsAdder ID: " + itemsAdderID);
            return null;
        }
        // --- Tagging and Initial Data ---
        // Tag it FIRST before setting other PDC values
        PDCUtil.tagAsEnchantCorePickaxe(pickaxe);
        // Verify tagging succeeded (important!)
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) {
            logger.severe("[EnchantCore][Error] Failed to tag pickaxe during creation! Material: " + material);
            return null;
        }
        // Set initial PDC data AFTER successful tagging
        PDCUtil.setPickaxeLevel(pickaxe, level);
        PDCUtil.setPickaxeBlocksMined(pickaxe, blocksMined);
        // --- End Tagging ---

        // Perform an initial update to apply name, lore, flags etc. (without player context initially)
        try {
            pickaxe = updatePickaxe(pickaxe, null); // Pass null for player initially
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[EnchantCore][Error] Exception during initial updatePickaxe call!", e);
            return null; // Return null if the initial update fails
        }

        if (pickaxe == null) { // Should not happen if updatePickaxe is robust, but check
            logger.log(Level.SEVERE, "[EnchantCore][Error] Pickaxe became null after initial updatePickaxe call!");
            return null;
        }
        logger.log(Level.FINER, "[EnchantCore][CreatePickaxe] Creation successful.");
        return pickaxe;
    }

    /**
     * Creates a default pickaxe using level 1 and 0 blocks mined.
     * @return The created ItemStack, or null on error.
     */
    @Nullable
    public ItemStack createDefaultPickaxe() {
        // Calls createPickaxe with default starting values
        return createPickaxe(1, 0L);
    }

    /**
     * Creates a pickaxe specifically configured for first join, potentially with overrides
     * for name, material, level, blocks, and starting enchantments from pickaxe.yml.
     * @param player The player joining (used for placeholder context). Can be null.
     * @return The created ItemStack, or null on error.
     */
    @Nullable
    public ItemStack createFirstJoinPickaxe(@Nullable Player player) {
        PickaxeConfig pConfig = plugin.getPickaxeConfig();
        EnchantRegistry registry = plugin.getEnchantRegistry();

        if (pConfig == null || registry == null) {
            logger.severe("Cannot create first join pickaxe: PickaxeConfig or EnchantRegistry is null!");
            return null;
        }

        // Determine material and ItemsAdder ID, with proper fallbacks
        Material material = pConfig.getFirstJoinMaterial();
        String itemsAdderID = pConfig.getFirstJoinItemsAdderID();

// Fallback to default pickaxe settings if first-join overrides aren't set
        if (material == null || material == Material.AIR) {
            material = pConfig.getPickaxeMaterial(); // Fallback material
        }
        if (itemsAdderID == null || itemsAdderID.trim().isEmpty()) {
            itemsAdderID = pConfig.getPickaxeItemsAdderID(); // Fallback ItemsAdder ID
        }

        if (material == null || material == Material.AIR) { // Final check
            logger.severe("Cannot create first join pickaxe: No valid material found in PickaxeConfig!");
            return null;
        }

        // Get other first-join settings
        int level = pConfig.getFirstJoinLevel();
        long blocksMined = pConfig.getFirstJoinBlocksMined();
        List<String> startingEnchants = pConfig.getFirstJoinEnchants();
        String nameFormatOverride = pConfig.getFirstJoinName();

        // Use override name format if provided, otherwise use the default
        String nameFormatToUse = (nameFormatOverride != null && !nameFormatOverride.isEmpty())
                ? nameFormatOverride : pConfig.getPickaxeNameFormat();

        // --- Create and Tag Item ---
        ItemStack pickaxe = itemsAdderUtil.createItemStack(material.name(), itemsAdderID);
        if (pickaxe == null) {
            logger.severe("Failed to create first join pickaxe ItemStack! Material: " + material + ", ItemsAdder ID: " + itemsAdderID);
            return null;
        }
        PDCUtil.tagAsEnchantCorePickaxe(pickaxe); // Apply the tag
        // *** Corrected Check: Verify tag *after* applying it ***
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) {
            logger.severe("Failed to tag first join pickaxe for material: " + material);
            return null;
        }
        // Set initial level and blocks AFTER successful tagging
        PDCUtil.setPickaxeLevel(pickaxe, level);
        PDCUtil.setPickaxeBlocksMined(pickaxe, blocksMined);
        // --- End Tagging ---


        // --- Apply Starting Enchantments ---
        if (startingEnchants != null && !startingEnchants.isEmpty()) {
            final boolean debug = plugin.getConfigManager().isDebugMode();
            for (String enchantString : startingEnchants) {
                if (enchantString == null || enchantString.trim().isEmpty()) continue;
                String[] parts = enchantString.trim().split("\\s+"); // Split by whitespace
                if (parts.length != 2) {
                    logger.warning("[FirstJoinPickaxe] Invalid enchant format in pickaxe.yml: '" + enchantString + "'. Skipping.");
                    continue;
                }
                String rawName = parts[0].toLowerCase();
                int enchantLevel;
                try {
                    enchantLevel = Integer.parseInt(parts[1]);
                    if (enchantLevel <= 0) throw new NumberFormatException("Level must be positive");
                } catch (NumberFormatException e) {
                    logger.warning("[FirstJoinPickaxe] Invalid level '" + parts[1] + "' for enchant '" + rawName + "'. Skipping.");
                    continue;
                }

                EnchantmentWrapper enchant = registry.getEnchant(rawName);
                if (enchant == null || !enchant.isEnabled()) { // Check if enchant exists and is enabled
                    if (enchant == null) logger.warning("[FirstJoinPickaxe] Unknown enchant raw name '" + rawName + "'. Skipping.");
                    else logger.warning("[FirstJoinPickaxe] Enchant '" + rawName + "' is disabled. Skipping.");
                    continue;
                }

                // Apply level, respecting enchant's max level
                int finalLevel = enchant.getMaxLevel() > 0 ? Math.min(enchantLevel, enchant.getMaxLevel()) : enchantLevel;
                if (finalLevel <= 0) finalLevel = 1; // Ensure at least level 1

                if (!setEnchantLevel(pickaxe, rawName, finalLevel)) { // Use manager method, check success
                    logger.warning("[FirstJoinPickaxe] Failed to set level for starting enchant: " + rawName + " " + finalLevel);
                } else if (debug) {
                    logger.info("[Debug][FirstJoinPickaxe] Applied starting enchant: " + rawName + " " + finalLevel);
                }
            }
        }
        // --- End Starting Enchantments ---


        // --- Initial Update for Name/Lore ---
        // Get PlayerData for context if player is available
        PlayerData playerData = null;
        if (player != null) {
            playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (playerData == null) { // Try loading if needed
                playerData = plugin.getPlayerDataManager().loadPlayerData(player.getUniqueId());
            }
        }

        // Apply name format first (placeholders need level/blocks from PDC)
        String finalName = applyPlaceholders(nameFormatToUse, player, playerData, level, blocksMined, pickaxe);
        ItemMeta tempMeta = pickaxe.getItemMeta();
        if (tempMeta != null) {
            tempMeta.setDisplayName(finalName);
            if (!pickaxe.setItemMeta(tempMeta)) { // Set meta with only name updated for now
                logger.warning("[FirstJoinPickaxe] Failed to set initial display name.");
            }
        } else {
            logger.warning("[FirstJoinPickaxe] Meta became null after setting PDC data.");
        }


        // Perform a full update to set lore, flags, proper enchants, etc.
        try {
            pickaxe = updatePickaxe(pickaxe, player); // Pass player for full context
            if (pickaxe == null) throw new IllegalStateException("updatePickaxe returned null"); // More specific error
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[FirstJoinPickaxe] Exception during final updatePickaxe call!", e);
            return null;
        }

        return pickaxe;
    }


    /**
     * Updates the pickaxe's name, lore, flags, and enchantments based on its current PDC data.
     * Applies placeholders using player context if provided.
     * This is the core method for keeping the pickaxe item visually up-to-date.
     *
     * @param pickaxe The pickaxe ItemStack to update (must be an EnchantCore pickaxe).
     * @param player  The player holding/viewing the pickaxe (used for placeholder context). Can be null.
     * @return The updated ItemStack, or the original stack if it wasn't an EnchantCore pickaxe or if an error occurred.
     */
    @NotNull
    public ItemStack updatePickaxe(@NotNull ItemStack pickaxe, @Nullable OfflinePlayer player) {
        // Initial checks
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) {
            if(plugin.getConfigManager().isDebugMode()) logger.finest("[UpdatePickaxe] Item is not an EC pickaxe, skipping update.");
            return pickaxe;
        }
        ItemMeta meta = pickaxe.getItemMeta();
        if (meta == null) {
            logger.log(Level.SEVERE, "[EnchantCore][Update] ItemMeta is null! Cannot update pickaxe " + pickaxe.getType());
            return pickaxe; // Return original on error
        }

        PickaxeConfig pConfig = plugin.getPickaxeConfig();
        EnchantRegistry registry = plugin.getEnchantRegistry();
        if (pConfig == null || registry == null) {
            logger.log(Level.SEVERE, "[EnchantCore][Update] PickaxeConfig or EnchantRegistry is null!");
            return pickaxe; // Return original on error
        }
        final boolean debug = plugin.getConfigManager().isDebugMode();
        if (debug) logger.finest("[UpdatePickaxe] Starting update for player: " + (player != null ? player.getName() : "None"));


        // --- Cache Config Values ---
        String nameFormat = pConfig.getPickaxeNameFormat();
        List<String> baseLoreFormat = pConfig.getPickaxeLoreFormat(); // Already colored by config loader
        String enchantLineFormat = pConfig.getEnchantLoreFormat(); // Already colored
        int modelData = pConfig.getCustomModelData();
        // --- End Cache ---


        // --- Get PlayerData ---
        PlayerData playerData = null;
        if (player != null) {
            playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (playerData == null) { // Load if not cached
                playerData = plugin.getPlayerDataManager().loadPlayerData(player.getUniqueId());
            }
        }
        if (debug && player != null && playerData == null) {
            logger.warning("[UpdatePickaxe] Failed to load PlayerData for " + player.getName() + " during update.");
        }
        // --- End PlayerData ---


        // --- Get Pickaxe Stats from PDC ---
        int level = PDCUtil.getPickaxeLevel(pickaxe);
        long blocksMined = PDCUtil.getPickaxeBlocksMined(pickaxe);
        Map<String, Integer> enchantLevels = getAllEnchantLevels(pickaxe); // Get enchant levels from PDC
        // --- End Pickaxe Stats ---


        // --- Calculate Progress Info ---
        long blocksRequiredForNext = getBlocksRequiredForLevel(level + 1);
        long blocksRequiredForCurrent = getBlocksRequiredForLevel(level);
        long blocksInLevel = Math.max(0, blocksMined - blocksRequiredForCurrent);
        // totalForLevel represents the number of blocks needed to complete the *current* level span
        long totalForLevel = (blocksRequiredForNext == Long.MAX_VALUE || blocksRequiredForNext <= blocksRequiredForCurrent)
                ? Long.MAX_VALUE // Effectively infinite if max level or error
                : (blocksRequiredForNext - blocksRequiredForCurrent);
        // --- End Progress Info ---


        // --- Apply Name ---
        String finalName = applyPlaceholders(nameFormat, player, playerData, level, blocksMined, pickaxe);
        meta.setDisplayName(finalName);
        // --- End Apply Name ---


        // --- Apply Lore ---
        List<String> finalLore = new ArrayList<>();
        String progressBar = createProgressBar(blocksInLevel, totalForLevel); // Create progress bar once

        // Apply base lore lines with placeholders
        for (String line : baseLoreFormat) {
            String processedLine = line; // Already colored from config
            // Handle progress bar specifically
            if (processedLine.contains("%enchantcore_progress_bar%")) {
                processedLine = processedLine.replace("%enchantcore_progress_bar%", progressBar); // ProgressBar is already colored
            }
            // Apply other placeholders (handles PAPI and final coloring)
            finalLore.add(applyPlaceholders(processedLine, player, playerData, level, blocksMined, pickaxe));
        }

        // Add Enchantment Lore section
        boolean appliedAnyEnchant = false;
        // Get all registered enchants (use Collection import here)
        Collection<EnchantmentWrapper> allRegisteredEnchants = registry.getAllEnchants();

        for (EnchantmentWrapper enchant : allRegisteredEnchants) {
            // Use lowercase raw name for lookup in the map from PDC
            int enchantLevel = enchantLevels.getOrDefault(enchant.getRawName().toLowerCase(), 0);
            if (enchantLevel > 0) { // Only add if enchant exists on pickaxe
                String enchantNameForDisplay = enchant.getDisplayName(); // Gets name with § codes
                String maxLevelDisplay = (enchant.getMaxLevel() > 0) ? String.valueOf(enchant.getMaxLevel()) : "Max";

                String enchantLine = enchantLineFormat // Already colored from config
                        .replace("%enchant_name%", enchantNameForDisplay)
                        .replace("%enchant_level%", String.valueOf(enchantLevel))
                        .replace("%enchant_max_level%", maxLevelDisplay);

                finalLore.add(enchantLine); // Add the fully processed, colored line
                appliedAnyEnchant = true;
            }
        }
        meta.setLore(finalLore); // Set the complete lore list
        // --- End Apply Lore ---


        // --- Apply Model Data ---
        NamespacedKey skinKey = new NamespacedKey(plugin, "pickaxe_skin");
        String appliedSkinId = meta.getPersistentDataContainer().get(skinKey, PersistentDataType.STRING);
        boolean hasSkinApplied = appliedSkinId != null;

        if (hasSkinApplied) {
            // Skin is applied - we need to restore the ItemsAdder appearance
            // This handles cases where the server restarted and ItemsAdder data was lost
            if (plugin.getSkinConfig() != null) {
                SkinConfig.SkinData skinData = plugin.getSkinConfig().getSkin(appliedSkinId);
                if (skinData != null) {
                    // Recreate the ItemsAdder item to get the correct custom model data
                    ItemStack freshSkinItem;
                    if (skinData.hasItemsAdderID()) {
                        freshSkinItem = plugin.getItemsAdderUtil().createItemStack(
                                skinData.getMaterial().name(),
                                skinData.getItemsAdderID()
                        );
                        if (freshSkinItem != null) {
                            ItemMeta freshMeta = freshSkinItem.getItemMeta();
                            if (freshMeta != null && freshMeta.hasCustomModelData()) {
                                // Apply the correct custom model data from ItemsAdder
                                meta.setCustomModelData(freshMeta.getCustomModelData());
                                // Also ensure the material type is correct
                                pickaxe.setType(freshSkinItem.getType());
                                if (plugin.getConfigManager().isDebugMode()) {
                                    logger.info("DEBUG: Restored skin " + appliedSkinId + " with custom model data: " + freshMeta.getCustomModelData());
                                }
                            }
                        }
                    } else if (skinData.getCustomModelData() > 0) {
                        // Vanilla custom model data
                        meta.setCustomModelData(skinData.getCustomModelData());
                    }
                } else {
                    logger.warning("Applied skin '" + appliedSkinId + "' not found in skins.yml - removing skin data");
                    meta.getPersistentDataContainer().remove(skinKey);
                }
            }
        } else {
            // No skin applied, use the default custom model data from config
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            } else {
                if (meta.hasCustomModelData()) meta.setCustomModelData(null); // Remove if 0 or less
            }
        }
        // --- End Model Data ---


        // --- Apply Unbreakable & Damage ---
        meta.setUnbreakable(true);
        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(0); // Ensure durability bar is full
        }
        // --- End Unbreakable ---


        // --- Apply Item Flags & Vanilla Enchantments ---
        // Hide miscellaneous flags
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_DYE);
        // Clear existing Bukkit enchants first to ensure clean application
        if (meta.hasEnchants()) {
            if (debug) logger.finest("[UpdatePickaxe] Clearing existing Bukkit enchants before applying updates.");
            for (Enchantment currentEnchant : new ArrayList<>(meta.getEnchants().keySet())) {
                meta.removeEnchant(currentEnchant);
            }
        }

        // Apply *actual* Bukkit enchantments ONLY for configured vanilla ones based on PDC levels
        boolean appliedRealVanillaEnchant = false;
        for (Map.Entry<String, Integer> entry : enchantLevels.entrySet()) {
            String pdcEnchantKey = entry.getKey(); // Already lowercase from getAllEnchantLevels
            int pdcLevel = entry.getValue();
            if (pdcLevel <= 0) continue;

            EnchantmentWrapper wrapper = registry.getEnchant(pdcEnchantKey); // Use lowercase key
            if (wrapper != null && wrapper.isVanilla() && wrapper.getBukkitEnchantment() != null) {
                Enchantment bukkitEnchantment = wrapper.getBukkitEnchantment();
                try {
                    // Apply the enchantment, allowing levels beyond vanilla max if needed (use with caution)
                    meta.addEnchant(bukkitEnchantment, pdcLevel, true); // true = ignore level restriction
                    appliedRealVanillaEnchant = true;
                    if (debug) logger.finest("[UpdatePickaxe] Applied vanilla enchant: " + bukkitEnchantment.getKey() + " L" + pdcLevel);
                } catch (IllegalArgumentException e) { // Catch potential errors
                    logger.log(Level.WARNING, "[EnchantCore][Update] Failed to apply vanilla enchant " + bukkitEnchantment.getKey() + " L" + pdcLevel + ": " + e.getMessage());
                }
            }
        }

        // Add fake glow (using Luck) if there are custom enchants AND no real vanilla enchants were applied
        if (appliedAnyEnchant && !appliedRealVanillaEnchant) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); // Hide the fake glow enchant
            if (debug) logger.finest("[UpdatePickaxe] Applied fake glow.");
        } else if (appliedRealVanillaEnchant) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); // Also hide real enchants from lore display
            if (debug) logger.finest("[UpdatePickaxe] Hiding real vanilla enchants.");
        } else {
            // Ensure HIDE_ENCHANTS is NOT present if there are no enchants at all
            meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        // --- End Flags & Enchants ---


        // --- Final Meta Application ---
        if (!pickaxe.setItemMeta(meta)) {
            logger.log(Level.WARNING, "[EnchantCore][Update] setItemMeta returned false! Update may not have applied correctly.");
        } else if (debug) {
            logger.finest("[UpdatePickaxe] Update successful for player: " + (player != null ? player.getName() : "None"));
        }
        // --- End Final Meta ---

        return pickaxe; // Return the modified pickaxe
    }

    /**
     * Applies placeholders to a given string, including EnchantCore specific ones and PAPI placeholders.
     * Handles final color translation.
     *
     * @param text       The text containing placeholders.
     * @param player     The player context (OfflinePlayer) for PAPI. Can be null.
     * @param playerData The PlayerData for internal placeholders. Can be null.
     * @param level      The current pickaxe level.
     * @param blocksMined Current blocks mined count.
     * @param pickaxe    The pickaxe item (currently unused here, but kept for potential future use).
     * @return The processed string with placeholders replaced and colors translated.
     */
    public String applyPlaceholders(String text, @Nullable OfflinePlayer player, @Nullable PlayerData playerData, int level, long blocksMined, @Nullable ItemStack pickaxe) {
        if (text == null || text.isEmpty()) return ""; // Return empty if input is null/empty
        String result = text; // Start with the input text (potentially already colored from config)

        // --- Calculate Progress Values (avoid recalculating if already done in updatePickaxe) ---
        // Note: This recalculates these values. If called frequently outside updatePickaxe, consider passing them in.
        long requiredForNext = getBlocksRequiredForLevel(level + 1);
        long requiredForCurrent = getBlocksRequiredForLevel(level);
        long blocksInLevel = Math.max(0, blocksMined - requiredForCurrent);
        long totalForLevel = (requiredForNext == Long.MAX_VALUE || requiredForNext <= requiredForCurrent)
                ? Long.MAX_VALUE : (requiredForNext - requiredForCurrent);
        // --- End Progress Values ---

        // --- Replace EnchantCore Placeholders ---
        // Basic Stats
        result = result.replace("%enchantcore_level%", String.valueOf(level));
        result = result.replace("%enchantcore_blocks_mined%", numberFormat.format(blocksMined));
        result = result.replace("%enchantcore_blocks_required%", requiredForNext == Long.MAX_VALUE ? "Max" : numberFormat.format(requiredForNext));
        // Progress within current level
        result = result.replace("%enchantcore_blocks_progress%", numberFormat.format(blocksInLevel));
        result = result.replace("%enchantcore_blocks_needed_for_level%", totalForLevel == Long.MAX_VALUE ? "Max" : numberFormat.format(totalForLevel));

        // Player Name
        if (player != null) {
            result = result.replace("%player%", player.getName() != null ? player.getName() : ""); // Handle potential null name
        } else {
            result = result.replace("%player%", ""); // Replace with empty if no player context
        }
        // Player Data (Tokens)
        if (playerData != null) {
            result = result.replace("%enchantcore_tokens%", String.valueOf(playerData.getTokens()));
            result = result.replace("%enchantcore_tokens_formatted%", playerData.getFormattedTokens()); // Assumes PlayerData has this method
        } else {
            result = result.replace("%enchantcore_tokens%", "0");
            result = result.replace("%enchantcore_tokens_formatted%", "0");
        }
        // --- End EnchantCore Placeholders ---


        // --- Apply PAPI Placeholders ---
        PapiHook papi = plugin.getPapiHook();
        // Check player is online for PAPI
        if (papi != null && papi.isHooked() && player != null && player.isOnline()) {
            try {
                // Pass the *online* Player instance
                result = PlaceholderAPI.setPlaceholders(player.getPlayer(), result);
            } catch (Exception e) {
                // Log PAPI errors but don't crash
                logger.log(Level.SEVERE, "Error applying PAPI placeholders for player " + (player.getName() != null ? player.getName() : player.getUniqueId()), e);
            }
        }
        // --- End PAPI Placeholders ---


        // --- Final Color Translation ---
        // Apply color codes (& and #) AFTER all placeholders are replaced
        return ColorUtils.translateColors(result);
        // --- End Final Color ---
    }

    /**
     * Calculates the TOTAL blocks mined required to REACH the start of the target level.
     * Returns Long.MAX_VALUE if the level is unreachable (beyond max level or due to overflow).
     * @param targetLevel The level to calculate the requirement for (e.g., 2 means blocks to reach level 2).
     * @return Total blocks required, or Long.MAX_VALUE.
     */
    public long getBlocksRequiredForLevel(int targetLevel) {
        PickaxeConfig config = plugin.getPickaxeConfig();
        if (config == null) {
            logger.severe("Cannot getBlocksRequiredForLevel: PickaxeConfig is null!");
            return Long.MAX_VALUE; // Indicate error/unreachable
        }

        // --- Base Cases and Max Level Check ---
        if (targetLevel <= 1) return 0; // Level 1 requires 0 total blocks

        int maxLevel = config.getMaxLevel();
        // If max level is set and target is beyond it, it's unreachable
        // (Target level n requires calculating cost up to n-1 -> n step)
        if (maxLevel > 0 && targetLevel > maxLevel + 1) {
            if(plugin.getConfigManager().isDebugMode()) logger.finest("[getBlocksReq] Target level " + targetLevel + " exceeds max+1 ("+(maxLevel+1)+")");
            return Long.MAX_VALUE;
        }
        // Special case: requirement for the level *immediately after* max level is MAX_VALUE
        if (maxLevel > 0 && targetLevel == maxLevel + 1) {
            if(plugin.getConfigManager().isDebugMode()) logger.finest("[getBlocksReq] Target level " + targetLevel + " is max+1 ("+(maxLevel+1)+"). Returning MAX.");
            return Long.MAX_VALUE;
        }
        // --- End Checks ---

        // --- Get Formula Parameters ---
        long base = config.getBaseBlocksRequired();
        double multiplier = config.getLevelingMultiplier();
        String formulaType = config.getLevelingFormulaType().toUpperCase(); // Ensure uppercase

        // Sanitize inputs (prevent non-positive values that break formulas)
        if (base <= 0) base = 100; // Default base if config is invalid
        if (formulaType.equals("EXPONENTIAL")) {
            if (multiplier <= 1.0) multiplier = 1.00001; // Ensure exponential growth
        } else { // Linear
            if (multiplier < 0) multiplier = 0; // Prevent negative linear increments
        }
        // --- End Parameters ---

        try {
            // Call the internal calculation method
            return getBlocksRequiredForLevelInternal(targetLevel, base, multiplier, formulaType, maxLevel);
        } catch (ArithmeticException e) {
            logger.warning("ArithmeticException calculating blocks required for level " + targetLevel);
            return Long.MAX_VALUE; // Indicate overflow or calculation error
        }
    }

    /**
     * Internal recursive or iterative method to calculate total blocks needed.
     * Uses long to prevent intermediate overflow where possible.
     * NOTE: This calculation can be intensive for very high target levels.
     * @param level Target level to reach.
     * @param base Base cost (blocks for 1->2 or linear start).
     * @param multiplier Exponential factor or linear increment.
     * @param formulaType "EXPONENTIAL" or "LINEAR".
     * @param maxLevel Configured max level (0 for unlimited).
     * @return Total blocks needed, or Long.MAX_VALUE on overflow/unreachable.
     */
    private long getBlocksRequiredForLevelInternal(int level, long base, double multiplier, String formulaType, int maxLevel) {
        if (level <= 1) return 0; // Base case

        // Check if target exceeds max level again (safety)
        if (maxLevel > 0 && level > maxLevel + 1) return Long.MAX_VALUE;

        long totalBlocksNeeded = 0;
        double currentLevelCost = (double) base; // Use double for exponential intermediate steps

        try {
            if ("EXPONENTIAL".equalsIgnoreCase(formulaType)) {
                // Sum the cost of each step: (1->2) + (2->3) + ... + (level-1 -> level)
                for (int i = 2; i <= level; i++) {
                    // Check for overflow *before* adding the current step's cost
                    long roundedCost = (long) Math.ceil(currentLevelCost);
                    if (roundedCost < 0 || totalBlocksNeeded > Long.MAX_VALUE - roundedCost) {
                        if(plugin.getConfigManager().isDebugMode()) logger.warning("[getBlocksInternal Exp] Overflow detected calculating total for level " + i);
                        return Long.MAX_VALUE;
                    }
                    totalBlocksNeeded += roundedCost;

                    // Calculate cost for the *next* step (if not the last step)
                    if (i < level) {
                        // Check for overflow *before* multiplying for the next step's cost
                        if (currentLevelCost > Double.MAX_VALUE / multiplier) {
                            if(plugin.getConfigManager().isDebugMode()) logger.warning("[getBlocksInternal Exp] Overflow detected calculating cost for level " + (i+1));
                            return Long.MAX_VALUE;
                        }
                        currentLevelCost *= multiplier;
                    }
                    // Early exit if we have calculated up to the max level step
                    if (maxLevel > 0 && i >= maxLevel + 1) { // Should be caught by initial check, but safeguard
                        return Long.MAX_VALUE;
                    }
                }
            } else { // LINEAR
                long linearIncrement = (long) multiplier; // Treat multiplier as the flat amount to add each level
                currentLevelCost = base; // Reset to base for linear

                for (int i = 2; i <= level; i++) {
                    // Check for overflow *before* adding cost for the current level step
                    long costToAdd = (long)currentLevelCost; // Use long for addition check
                    if (costToAdd < 0 || totalBlocksNeeded > Long.MAX_VALUE - costToAdd) {
                        if(plugin.getConfigManager().isDebugMode()) logger.warning("[getBlocksInternal Lin] Overflow detected calculating total for level " + i);
                        return Long.MAX_VALUE;
                    }
                    totalBlocksNeeded += costToAdd; // Add cost for current step

                    // Calculate cost for the *next* step
                    if (i < level) {
                        // Check for overflow *before* adding increment
                        if (currentLevelCost > Long.MAX_VALUE - linearIncrement) {
                            if(plugin.getConfigManager().isDebugMode()) logger.warning("[getBlocksInternal Lin] Overflow detected calculating cost for level " + (i+1));
                            return Long.MAX_VALUE;
                        }
                        currentLevelCost += linearIncrement;
                    }
                    // Early exit check
                    if (maxLevel > 0 && i >= maxLevel + 1) {
                        return Long.MAX_VALUE;
                    }
                }
            }
            return Math.max(0, totalBlocksNeeded); // Ensure non-negative result
        } catch (ArithmeticException e) {
            // Catch potential overflows during casting or calculations not caught by checks
            logger.warning("ArithmeticException during internal block calculation for level " + level + ": " + e.getMessage());
            return Long.MAX_VALUE;
        }
    }


    /**
     * Creates a string representation of a progress bar.
     * @param currentProgressInLevel Blocks mined within the current level range.
     * @param totalRequiredForLevel Total blocks needed to complete the current level range.
     * @return Formatted progress bar string with colors.
     */
    public String createProgressBar(long currentProgressInLevel, long totalRequiredForLevel) {
        PickaxeConfig config = plugin.getPickaxeConfig();
        if (config == null) return "[ProgressBar Error: Config Null]";

        // --- Cache Config Values ---
        int length = config.getProgressBarLength();
        String filledSymbol = config.getProgressBarFilledSymbol();
        String emptySymbol = config.getProgressBarEmptySymbol();
        String filledColorCode = config.getProgressBarFilledColor(); // Has & codes
        String emptyColorCode = config.getProgressBarEmptyColor(); // Has & codes
        // --- End Cache ---

        // Basic validation
        if (length <= 0) length = 1; // Ensure length is positive
        if (filledSymbol == null) filledSymbol = "|";
        if (emptySymbol == null) emptySymbol = "-";
        if (filledColorCode == null) filledColorCode = "&a";
        if (emptyColorCode == null) emptyColorCode = "&7";


        // Handle max level / invalid total case
        if (totalRequiredForLevel == Long.MAX_VALUE || totalRequiredForLevel <= 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(ColorUtils.translateColors(filledColorCode)); // Apply color
            for (int i = 0; i < length; i++) sb.append(filledSymbol); // Fill bar
            return sb.toString();
        }

        // Sanitize current progress
        long displayProgress = Math.max(0, Math.min(currentProgressInLevel, totalRequiredForLevel)); // Clamp progress [0, total]

        // Calculate filled segments
        // Use double for division to get accurate fraction
        double fractionFilled = (double) displayProgress / totalRequiredForLevel;
        int filledCount = (int) Math.floor(fractionFilled * length);
        // Clamp filledCount just in case of floating point inaccuracies near 1.0
        filledCount = Math.max(0, Math.min(length, filledCount));
        int emptyCount = length - filledCount;

        // Build the string efficiently
        StringBuilder sb = new StringBuilder(length * 3); // Estimate capacity
        // Append filled part
        sb.append(ColorUtils.translateColors(filledColorCode)); // Apply color
        for (int i = 0; i < filledCount; i++) sb.append(filledSymbol);
        // Append empty part
        sb.append(ColorUtils.translateColors(emptyColorCode)); // Apply color
        for (int i = 0; i < emptyCount; i++) sb.append(emptySymbol);

        return sb.toString();
    }


    /**
     * Finds the first EnchantCore pickaxe in a player's inventory (main hand, off hand, storage).
     * @param player The online player.
     * @return The ItemStack if found, null otherwise.
     */
    @Nullable
    public ItemStack findPickaxe(@Nullable Player player) {
        if (player == null || !player.isOnline()) return null;
        PlayerInventory inventory = player.getInventory();

        // Prioritize main hand
        ItemStack mainHand = inventory.getItemInMainHand();
        if (PDCUtil.isEnchantCorePickaxe(mainHand)) return mainHand;

        // Check off hand
        ItemStack offHand = inventory.getItemInOffHand();
        if (PDCUtil.isEnchantCorePickaxe(offHand)) return offHand;

        // Check main storage slots
        for (ItemStack item : inventory.getStorageContents()) { // Includes hotbar + main inventory area
            if (PDCUtil.isEnchantCorePickaxe(item)) return item;
        }

        // Optionally check armor or extra slots if relevant, but unlikely for a pickaxe

        return null; // Not found
    }


    /**
     * Gets the level of a specific enchantment stored in the pickaxe's PDC.
     * @param pickaxe The EnchantCore pickaxe ItemStack.
     * @param enchantKey The raw name (config key) of the enchantment (case-insensitive).
     * @return The enchantment level, or 0 if not found or not an EC pickaxe.
     */
    public int getEnchantLevel(@NotNull ItemStack pickaxe, @NotNull String enchantKey) {
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) return 0;
        if (enchantKey == null || enchantKey.isEmpty()) return 0;

        EnchantmentWrapper enchant = plugin.getEnchantRegistry().getEnchant(enchantKey); // Handles lowercase
        if (enchant == null) return 0; // Enchant definition not found

        NamespacedKey pdcKey = enchant.getPdcLevelKey();
        if (pdcKey == null) { // Should not happen if wrapper loaded correctly
            logger.severe("PDC Key is NULL for enchant: " + enchantKey + " in EnchantmentWrapper!");
            return 0;
        }
        // Get value, default to 0 if key doesn't exist on the item
        return PDCUtil.getInt(pickaxe, pdcKey, 0);
    }


    /**
     * Sets the level of a specific enchantment in the pickaxe's PDC.
     * Removes the key if level is 0 or less.
     * @param pickaxe The EnchantCore pickaxe ItemStack.
     * @param enchantKey The raw name (config key) of the enchantment (case-insensitive).
     * @param level The level to set.
     * @return True if successful, false otherwise (e.g., not EC pickaxe, enchant not found, meta error).
     */
    public boolean setEnchantLevel(@NotNull ItemStack pickaxe, @NotNull String enchantKey, int level) {
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) return false;
        if (enchantKey == null || enchantKey.isEmpty()) return false;

        EnchantmentWrapper enchant = plugin.getEnchantRegistry().getEnchant(enchantKey); // Handles lowercase
        if (enchant == null) {
            logger.warning("Attempted set level for unknown enchant key: " + enchantKey);
            return false;
        }
        NamespacedKey pdcKey = enchant.getPdcLevelKey();
        if (pdcKey == null) {
            logger.severe("PDC Key is NULL for enchant: " + enchantKey + ". Cannot set level.");
            return false;
        }

        ItemMeta meta = pickaxe.getItemMeta();
        if (meta == null) {
            logger.severe("Failed set enchant level: ItemMeta is null for pickaxe!");
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Set or remove the PDC value
        if (level > 0) {
            pdc.set(pdcKey, PersistentDataType.INTEGER, level);
        } else {
            // Only remove if the key actually exists
            if (pdc.has(pdcKey, PersistentDataType.INTEGER)) {
                pdc.remove(pdcKey);
            }
        }

        // Apply the modified meta back
        if (!pickaxe.setItemMeta(meta)) {
            logger.warning("Failed set enchant level: setItemMeta returned false for " + enchantKey);
            return false; // Failed to apply meta
        }
        return true; // Success
    }


    /**
     * Retrieves all EnchantCore enchantment levels stored in the pickaxe's PDC.
     * @param pickaxe The EnchantCore pickaxe ItemStack.
     * @return A Map where the key is the lowercase raw enchantment name and the value is the level. Returns an empty map if not an EC pickaxe or on error.
     */
    @NotNull
    public Map<String, Integer> getAllEnchantLevels(@NotNull ItemStack pickaxe) {
        Map<String, Integer> levels = new HashMap<>();
        if (!PDCUtil.isEnchantCorePickaxe(pickaxe)) return levels; // Return empty map

        ItemMeta meta = pickaxe.getItemMeta();
        if (meta == null) {
            logger.warning("Could not getAllEnchantLevels: ItemMeta is null.");
            return levels; // Return empty map
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Iterate through all *known* enchantments from the registry
        EnchantRegistry registry = plugin.getEnchantRegistry();
        if (registry == null) {
            logger.severe("Cannot getAllEnchantLevels: EnchantRegistry is null!");
            return levels;
        }
        for (EnchantmentWrapper enchant : registry.getAllEnchants()) { // Use Collection import
            if (enchant == null) continue; // Should not happen

            NamespacedKey key = enchant.getPdcLevelKey();
            // Check if the item has this specific enchantment key in its PDC
            if (key != null && pdc.has(key, PersistentDataType.INTEGER)) {
                int level = pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
                if (level > 0) {
                    // Store using the consistent lowercase raw name
                    levels.put(enchant.getRawName().toLowerCase(), level);
                }
            }
        }
        return levels;
    }

} // End of PickaxeManager class