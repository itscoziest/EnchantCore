package com.strikesenchantcore.config;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull; // Import NotNull
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*; // Import necessary java.util classes
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger
import java.util.stream.Collectors;

/**
 * Manages loading and accessing settings from pickaxe.yml, including
 * appearance, behavior, leveling, first-join defaults, and level-up rewards.
 */
public class PickaxeConfig {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    private File pickaxeFile;
    private FileConfiguration pickaxeConfig;

    // --- Cached General Pickaxe Settings ---
    private String pickaxeNameFormat = "&bEnchantCore Pickaxe &7(Level %enchantcore_level%)";
    private Material pickaxeMaterial = Material.DIAMOND_PICKAXE;
    private int customModelData = 0;
    private List<String> pickaxeLoreFormat = List.of(); // Default set in load() if missing
    private boolean keepInventory = true;
    private boolean preventDrop = true;
    private boolean preventStore = true;
    private boolean allowInventoryMove = true;
    private String enchantLoreFormat = "&7- %enchant_name% &f%enchant_level%/%enchant_max_level%";
    // --- End General ---

    // --- Cached Leveling Settings ---
    private String levelingFormulaType = "EXPONENTIAL";
    private long baseBlocksRequired = 100;
    private double levelingMultiplier = 1.5;
    private int maxLevel = 1000;
    // --- End Leveling ---

    // --- Cached Progress Bar Settings ---
    private String progressBarFilledSymbol = "|";
    private String progressBarEmptySymbol = "-";
    private String progressBarFilledColor = "&a";
    private String progressBarEmptyColor = "&7";
    private int progressBarLength = 20;
    // --- End Progress Bar ---

    // --- Cached First Join Pickaxe Settings ---
    private boolean firstJoinEnabled = true;
    private boolean firstJoinCheckExisting = true;
    private String firstJoinName = ""; // Empty means use default pickaxe name
    @Nullable private Material firstJoinMaterial = null; // Null means use default pickaxe material
    private int firstJoinLevel = 1;
    private long firstJoinBlocksMined = 0;
    private List<String> firstJoinEnchants = Collections.emptyList(); // Use Collections.emptyList for default
    // --- End First Join ---

    // --- Cached Level Up Rewards Settings ---
    private boolean levelRewardsEveryEnable = false;
    private String levelRewardsEveryMessage = "";
    private List<String> levelRewardsEveryCommands = Collections.emptyList();

    private boolean levelRewardsMilestoneEnable = false;
    private int levelRewardsMilestoneInterval = 10;
    private String levelRewardsMilestoneMessage = "";
    private List<String> levelRewardsMilestoneCommands = Collections.emptyList();

    private boolean levelRewardsSpecificEnable = false;
    private String levelRewardsSpecificDefaultMessage = "";
    // Map: Level -> RewardData (containing optional message and commands)
    private Map<Integer, LevelRewardData> levelRewardsSpecificLevelsMap = Collections.emptyMap();

    /** Helper class for specific level rewards data storage. */
    public static class LevelRewardData {
        @Nullable public final String message; // Can be null if using the default specific message
        @NotNull public final List<String> commands; // Never null, use empty list

        LevelRewardData(@Nullable String message, @Nullable List<String> commands) {
            this.message = message; // Store null if not provided
            // Ensure commands list is never null and is unmodifiable
            this.commands = (commands != null) ? Collections.unmodifiableList(new ArrayList<>(commands)) : Collections.emptyList();
        }
    }
    // --- End Rewards Settings ---


    public PickaxeConfig(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        setup();
        // load() is called by ConfigManager
    }

    /** Sets up the file and configuration object. Creates default if missing. */
    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        pickaxeFile = new File(plugin.getDataFolder(), "pickaxe.yml");
        if (!pickaxeFile.exists()) {
            plugin.saveResource("pickaxe.yml", false);
            logger.info("Created default pickaxe.yml");
        }
        pickaxeConfig = new YamlConfiguration();
        try {
            pickaxeConfig.load(pickaxeFile);
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Could not load pickaxe.yml during setup!", e);
        }
    }

    /** Loads all settings from the pickaxe.yml file into cached fields. */
    public void load() {
        // Ensure file exists, reload from disk
        if (!pickaxeFile.exists()) {
            logger.warning("pickaxe.yml not found! Attempting to recreate...");
            setup(); // Tries to recreate and load initial
        } else {
            try {
                pickaxeConfig.load(pickaxeFile);
            } catch (IOException | InvalidConfigurationException e) {
                logger.log(Level.SEVERE, "Could not reload pickaxe.yml! Using potentially stale values.", e);
                // Continue with current values if reload fails
                return;
            }
        }

        // --- Load General Pickaxe Section ---
        ConfigurationSection pickaxeSection = pickaxeConfig.getConfigurationSection("Pickaxe");
        if (pickaxeSection != null) {
            pickaxeNameFormat = pickaxeSection.getString("Name", "&bEnchantCore Pickaxe &7(Level %enchantcore_level%)");
            try {
                String matName = pickaxeSection.getString("Material", "DIAMOND_PICKAXE").toUpperCase();
                pickaxeMaterial = Material.valueOf(matName);
                if(pickaxeMaterial == Material.AIR) {
                    logger.warning("Pickaxe Material cannot be AIR. Using DIAMOND_PICKAXE.");
                    pickaxeMaterial = Material.DIAMOND_PICKAXE;
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid Material specified in Pickaxe.Material: " + pickaxeSection.getString("Material") + ". Using DIAMOND_PICKAXE.");
                pickaxeMaterial = Material.DIAMOND_PICKAXE; // Fallback
            }
            customModelData = pickaxeSection.getInt("CustomModelData", 0);
            List<String> rawLore = pickaxeSection.getStringList("Lore");
            // Set default lore if missing or empty in config
            if (rawLore == null || rawLore.isEmpty()) {
                this.pickaxeLoreFormat = List.of( // Default lore definition
                        "&8&m------------------------",
                        "&eBlocks Mined: &f%enchantcore_blocks_mined%/%enchantcore_blocks_required%",
                        "&ePickaxe Level: &f%enchantcore_level% &7[%enchantcore_progress_bar%&7]",
                        "&8&m------------------------",
                        "&7Enchantments:" // Marker line
                );
                if (rawLore == null) { // Log only if the section was entirely missing
                    logger.warning("Pickaxe.Lore section missing in pickaxe.yml. Using default lore.");
                }
            } else {
                // Process colors immediately on load for lore format
                this.pickaxeLoreFormat = rawLore.stream().map(ChatUtil::color).collect(Collectors.toList());
            }
            // Behavior settings
            keepInventory = pickaxeSection.getBoolean("Keep-Inventory", true);
            preventDrop = pickaxeSection.getBoolean("Prevent-Drop", true);
            preventStore = pickaxeSection.getBoolean("Prevent-Store", true);
            allowInventoryMove = pickaxeSection.getBoolean("Allow-Inventory-Move", true);
        } else {
            logger.severe("Pickaxe section missing in pickaxe.yml! Using default values for ALL general pickaxe settings.");
            // Reset all general settings to defaults if section is missing
            pickaxeNameFormat = "&bEnchantCore Pickaxe &7(Level %enchantcore_level%)";
            pickaxeMaterial = Material.DIAMOND_PICKAXE;
            customModelData = 0;
            this.pickaxeLoreFormat = List.of("&8&m------------------------", "&eBlocks Mined: &f%enchantcore_blocks_mined%/%enchantcore_blocks_required%", "&ePickaxe Level: &f%enchantcore_level% &7[%enchantcore_progress_bar%&7]", "&8&m------------------------", "&7Enchantments:");
            keepInventory = true; preventDrop = true; preventStore = true; allowInventoryMove = true;
        }


        // --- Load Leveling Section (including Rewards) ---
        ConfigurationSection levelingSection = pickaxeConfig.getConfigurationSection("Pickaxe.Leveling"); // Relative to root
        if (levelingSection != null) {
            levelingFormulaType = levelingSection.getString("Formula-Type", "EXPONENTIAL").toUpperCase();
            baseBlocksRequired = Math.max(1L, levelingSection.getLong("Base-Blocks-Required", 100)); // Ensure positive
            levelingMultiplier = levelingSection.getDouble("Multiplier", 1.5);
            maxLevel = levelingSection.getInt("Max-Level", 1000); // Allow 0 or negative for unlimited

            // *** Load Rewards Sub-Section ***
            ConfigurationSection rewardsSection = levelingSection.getConfigurationSection("rewards");
            if (rewardsSection != null) {
                // Every Level Rewards
                ConfigurationSection everySection = rewardsSection.getConfigurationSection("every-level");
                if (everySection != null) {
                    levelRewardsEveryEnable = everySection.getBoolean("enable", false);
                    levelRewardsEveryMessage = ChatUtil.color(everySection.getString("message", "")); // Color message format
                    levelRewardsEveryCommands = everySection.getStringList("commands");
                } else { resetEveryLevelRewards(); /* Use defaults if section missing */ }

                // Milestone Rewards
                ConfigurationSection milestoneSection = rewardsSection.getConfigurationSection("milestone");
                if (milestoneSection != null) {
                    levelRewardsMilestoneEnable = milestoneSection.getBoolean("enable", false);
                    levelRewardsMilestoneInterval = milestoneSection.getInt("interval", 10);
                    if (levelRewardsMilestoneInterval <= 0) {
                        logger.warning("Milestone reward interval in pickaxe.yml must be positive. Disabling milestone rewards.");
                        levelRewardsMilestoneEnable = false;
                        levelRewardsMilestoneInterval = 10; // Reset to default for safety
                    }
                    levelRewardsMilestoneMessage = ChatUtil.color(milestoneSection.getString("message", "")); // Color message format
                    levelRewardsMilestoneCommands = milestoneSection.getStringList("commands");
                } else { resetMilestoneRewards(); /* Use defaults */ }

                // Specific Level Rewards
                ConfigurationSection specificSection = rewardsSection.getConfigurationSection("specific-levels");
                if (specificSection != null) {
                    levelRewardsSpecificEnable = specificSection.getBoolean("enable", false);
                    levelRewardsSpecificDefaultMessage = ChatUtil.color(specificSection.getString("default-message", "")); // Color default message
                    ConfigurationSection levelsMapSection = specificSection.getConfigurationSection("levels");
                    Map<Integer, LevelRewardData> loadedSpecificLevels = new HashMap<>();
                    if (levelsMapSection != null) {
                        // Parse each defined level under 'levels'
                        for (String levelKey : levelsMapSection.getKeys(false)) {
                            try {
                                int specificLevel = Integer.parseInt(levelKey);
                                if (specificLevel <= 0) {
                                    logger.warning("Invalid specific level key '" + levelKey + "' (must be > 0) in pickaxe.yml rewards. Skipping.");
                                    continue;
                                }
                                ConfigurationSection rewardDataSection = levelsMapSection.getConfigurationSection(levelKey);
                                if (rewardDataSection != null) {
                                    // Get message (allow null), color it if present
                                    String specificMsgRaw = rewardDataSection.getString("message");
                                    String specificMsgColored = (specificMsgRaw != null) ? ChatUtil.color(specificMsgRaw) : null;
                                    List<String> specificCmds = rewardDataSection.getStringList("commands");
                                    // Create and store reward data
                                    loadedSpecificLevels.put(specificLevel, new LevelRewardData(specificMsgColored, specificCmds));
                                } else {
                                    logger.warning("Reward data missing for specific level key '" + levelKey + "' in pickaxe.yml rewards.");
                                }
                            } catch (NumberFormatException e) {
                                logger.warning("Invalid number format for specific level key '" + levelKey + "' in pickaxe.yml rewards. Skipping.");
                            }
                        }
                    }
                    // Store the loaded map (already unmodifiable from constructor)
                    levelRewardsSpecificLevelsMap = loadedSpecificLevels.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(loadedSpecificLevels);
                } else { resetSpecificLevelRewards(); /* Use defaults */ }
            } else { // Rewards section is missing
                logger.warning("Pickaxe.Leveling.rewards section missing in pickaxe.yml. No level rewards loaded.");
                resetAllLevelRewards(); // Reset all reward types to default
            }
            // *** End Load Rewards ***

        } else { // Leveling section missing
            logger.warning("Pickaxe.Leveling section missing in pickaxe.yml. Using defaults for leveling and rewards.");
            // Reset leveling and rewards to defaults
            levelingFormulaType = "EXPONENTIAL"; baseBlocksRequired = 100; levelingMultiplier = 1.5; maxLevel = 1000;
            resetAllLevelRewards();
        }
        // If Pickaxe section was missing initially, leveling/rewards already defaulted above.


        // --- Load Progress Bar Section ---
        ConfigurationSection progressBarSection = pickaxeConfig.getConfigurationSection("ProgressBar");
        if (progressBarSection != null) {
            progressBarFilledSymbol = progressBarSection.getString("Symbol-Filled", "|");
            progressBarEmptySymbol = progressBarSection.getString("Symbol-Empty", "-");
            progressBarFilledColor = progressBarSection.getString("Color-Filled", "&a"); // Store raw color code
            progressBarEmptyColor = progressBarSection.getString("Color-Empty", "&7"); // Store raw color code
            progressBarLength = Math.max(1, progressBarSection.getInt("Length", 20)); // Ensure positive length
        } else {
            logger.warning("ProgressBar section missing in pickaxe.yml. Using defaults.");
            progressBarFilledSymbol = "|"; progressBarEmptySymbol = "-"; progressBarFilledColor = "&a"; progressBarEmptyColor = "&7"; progressBarLength = 20;
        }


        // --- Load Enchant Lore Format ---
        // Color this on load as it's a single format string used repeatedly
        enchantLoreFormat = ChatUtil.color(pickaxeConfig.getString("Enchant-Lore-Format", "&7- %enchant_name% &f%enchant_level%/%enchant_max_level%"));


        // --- Load First Join Pickaxe Section ---
        ConfigurationSection firstJoinSection = pickaxeConfig.getConfigurationSection("FirstJoinPickaxe");
        if (firstJoinSection != null) {
            firstJoinEnabled = firstJoinSection.getBoolean("Enabled", true);
            firstJoinCheckExisting = firstJoinSection.getBoolean("CheckExisting", true);
            firstJoinName = firstJoinSection.getString("Name", ""); // Empty = use default name format
            String matString = firstJoinSection.getString("Material", ""); // Empty = use default material
            if (!matString.isEmpty()) {
                try {
                    Material fjMat = Material.valueOf(matString.toUpperCase());
                    firstJoinMaterial = (fjMat == Material.AIR) ? null : fjMat; // Don't allow AIR override
                    if(fjMat == Material.AIR) logger.warning("FirstJoinPickaxe Material cannot be AIR. Using default pickaxe material.");
                }
                catch (IllegalArgumentException e) {
                    logger.warning("Invalid Material specified in FirstJoinPickaxe.Material: " + matString + ". Using default pickaxe material.");
                    firstJoinMaterial = null; // Use default if invalid
                }
            } else { firstJoinMaterial = null; } // Use default if empty string
            firstJoinLevel = Math.max(1, firstJoinSection.getInt("Level", 1)); // Ensure at least level 1
            firstJoinBlocksMined = Math.max(0L, firstJoinSection.getLong("BlocksMined", 0L)); // Ensure non-negative
            firstJoinEnchants = firstJoinSection.getStringList("Enchants"); // Can be null/empty
            if (firstJoinEnchants == null) firstJoinEnchants = Collections.emptyList(); // Ensure non-null
        } else {
            logger.warning("FirstJoinPickaxe section missing in pickaxe.yml. Using defaults.");
            // Reset first join settings to defaults
            firstJoinEnabled = true; firstJoinCheckExisting = true; firstJoinName = ""; firstJoinMaterial = null;
            firstJoinLevel = 1; firstJoinBlocksMined = 0; firstJoinEnchants = Collections.emptyList();
        }


        logger.info("pickaxe.yml settings loaded/reloaded.");
    }

    // --- Helper methods to reset reward sections to defaults ---
    private void resetEveryLevelRewards() {
        levelRewardsEveryEnable = false; levelRewardsEveryMessage = ""; levelRewardsEveryCommands = Collections.emptyList();
    }
    private void resetMilestoneRewards() {
        levelRewardsMilestoneEnable = false; levelRewardsMilestoneInterval = 10; levelRewardsMilestoneMessage = ""; levelRewardsMilestoneCommands = Collections.emptyList();
    }
    private void resetSpecificLevelRewards() {
        levelRewardsSpecificEnable = false; levelRewardsSpecificDefaultMessage = ""; levelRewardsSpecificLevelsMap = Collections.emptyMap();
    }
    private void resetAllLevelRewards() {
        resetEveryLevelRewards();
        resetMilestoneRewards();
        resetSpecificLevelRewards();
    }


    // --- Getters for Cached Settings ---
    // General
    @NotNull public String getPickaxeNameFormat() { return pickaxeNameFormat; }
    @NotNull public Material getPickaxeMaterial() { return pickaxeMaterial; } // Should have a default
    public int getCustomModelData() { return customModelData; }
    @NotNull public List<String> getPickaxeLoreFormat() { return pickaxeLoreFormat; } // Already colored, return directly
    public boolean isKeepInventory() { return keepInventory; }
    public boolean isPreventDrop() { return preventDrop; }
    public boolean isPreventStore() { return preventStore; }
    public boolean isAllowInventoryMove() { return allowInventoryMove; }
    @NotNull public String getEnchantLoreFormat() { return enchantLoreFormat; } // Already colored

    // Leveling
    @NotNull public String getLevelingFormulaType() { return levelingFormulaType; }
    public long getBaseBlocksRequired() { return baseBlocksRequired; }
    public double getLevelingMultiplier() { return levelingMultiplier; }
    public int getMaxLevel() { return maxLevel; } // 0 or negative means unlimited

    // Progress Bar
    @NotNull public String getProgressBarFilledSymbol() { return progressBarFilledSymbol; }
    @NotNull public String getProgressBarEmptySymbol() { return progressBarEmptySymbol; }
    @NotNull public String getProgressBarFilledColor() { return progressBarFilledColor; } // Raw color code
    @NotNull public String getProgressBarEmptyColor() { return progressBarEmptyColor; } // Raw color code
    public int getProgressBarLength() { return progressBarLength; }

    // First Join
    public boolean isFirstJoinEnabled() { return firstJoinEnabled; }
    public boolean isFirstJoinCheckExisting() { return firstJoinCheckExisting; }
    @NotNull public String getFirstJoinName() { return firstJoinName; } // Empty means use default name format
    @Nullable public Material getFirstJoinMaterial() { return firstJoinMaterial; } // Null means use default material
    public int getFirstJoinLevel() { return firstJoinLevel; }
    public long getFirstJoinBlocksMined() { return firstJoinBlocksMined; }
    @NotNull public List<String> getFirstJoinEnchants() { return Collections.unmodifiableList(firstJoinEnchants); } // Return unmodifiable

    // Level Rewards
    // Every Level
    public boolean isLevelRewardsEveryEnable() { return levelRewardsEveryEnable; }
    @NotNull public String getLevelRewardsEveryMessage() { return levelRewardsEveryMessage; } // Already colored
    @NotNull public List<String> getLevelRewardsEveryCommands() { return Collections.unmodifiableList(levelRewardsEveryCommands); } // Return unmodifiable
    // Milestone
    public boolean isLevelRewardsMilestoneEnable() { return levelRewardsMilestoneEnable; }
    public int getLevelRewardsMilestoneInterval() { return levelRewardsMilestoneInterval; }
    @NotNull public String getLevelRewardsMilestoneMessage() { return levelRewardsMilestoneMessage; } // Already colored
    @NotNull public List<String> getLevelRewardsMilestoneCommands() { return Collections.unmodifiableList(levelRewardsMilestoneCommands); } // Return unmodifiable
    // Specific Level
    public boolean isLevelRewardsSpecificEnable() { return levelRewardsSpecificEnable; }
    @NotNull public String getLevelRewardsSpecificDefaultMessage() { return levelRewardsSpecificDefaultMessage; } // Already colored
    @NotNull public Map<Integer, LevelRewardData> getLevelRewardsSpecificLevelsMap() { return levelRewardsSpecificLevelsMap; } // Already unmodifiable

    /** Direct access to the config object - use with caution */
    @Nullable public FileConfiguration getConfig() { return pickaxeConfig; }

} // End of PickaxeConfig class