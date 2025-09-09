package com.strikesenchantcore.enchants;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.ConfigManager;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.util.ColorUtils; // Needed for final translation
import com.strikesenchantcore.util.ChatUtil; // Can be used for utility, but ColorUtils handles translation
import com.strikesenchantcore.util.VaultHook;
import com.strikesenchantcore.util.PDCUtil; // Needed for PDC key generation

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

// --- ADDED/KEPT IMPORTS ---
import org.bukkit.ChatColor; // <<<--- ADDED THIS IMPORT
import java.text.DecimalFormat;
import java.text.NumberFormat; // Import NumberFormat
import java.util.Locale;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger; // Use Logger
import java.util.stream.Collectors;
// --- END IMPORTS ---


/**
 * Wraps the configuration and logic for a single custom or vanilla enchantment.
 * Handles cost calculation, GUI item creation, and placeholder replacement.
 */
public class EnchantmentWrapper {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger instance
    private final String configKey; // Original key from enchants.yml (e.g., "efficiency")

    // --- Fields loaded from config ---
    private String rawName;             // Unique internal identifier (e.g., "efficiency", "tokenator")
    private String nameFormat;          // Display name format (e.g., "#a1e8a1&lEfficiency") - WITH format codes
    private String guiNameFormat;       // GUI display name format - WITH format codes
    private Material material = Material.BOOK; // Default GUI material
    private String base64Texture = null; // For custom heads (unused by default)
    private int customModelData = 0;     // For resource packs
    private boolean enabled = true;     // Is the enchant usable?
    private int inGuiSlot = -1;         // Slot in the /enchant GUI
    private int maxLevel = 1;           // Max level achievable
    private double cost = 0;            // Base cost for level 1->2 (or first level)
    private double increaseCostBy = 0;  // Amount cost increases per level (depends on formula)
    private String costFormula = "LINEAR"; // How cost scales ("LINEAR" or "EXPONENTIAL")
    private List<String> descriptionList = Collections.emptyList(); // Raw description lines from config
    private List<String> configuredLore = Collections.emptyList(); // Raw GUI lore format lines from config
    private int pickaxeLevelRequired = 0; // Min pickaxe level needed to upgrade
    private boolean isVanilla = false;    // Is this wrapping a vanilla Minecraft enchantment?
    private String minecraftEnchantKey = null; // Key like "minecraft:efficiency" if vanilla
    @Nullable private Enchantment bukkitEnchantment = null; // Cached Bukkit Enchantment instance if vanilla
    @Nullable private ConfigurationSection customSettings; // Section for enchant-specific settings (chance, radius, etc.)
    @NotNull private NamespacedKey pdcLevelKey; // PDC key for storing this enchant's level on a pickaxe
    private EnchantType type = EnchantType.ACTIVE; // Default, determined during load
    private ConfigManager.CurrencyType currencyType;

    // --- Formatting Helpers (Static Final for efficiency) ---
    // These are thread-safe and can be reused
    private static final DecimalFormat FORMAT_ONE_DECIMAL = new DecimalFormat("#,##0.0");
    private static final DecimalFormat FORMAT_PERCENTAGE = new DecimalFormat("#,##0.00##");
    // Use NumberFormat for integer formatting (handles commas based on Locale)
    private static final NumberFormat FORMAT_INTEGER = NumberFormat.getIntegerInstance(Locale.US); // Use US Locale for consistency
    // --- End Formatting Helpers ---

    // Enchant Type Enum
    public enum EnchantType { ACTIVE, PASSIVE }

    /**
     * Constructor - Loads enchantment data from a configuration section.
     * @param plugin The main EnchantCore plugin instance.
     * @param configKey The key used for this enchant in enchants.yml.
     * @param section The ConfigurationSection for this enchantment.
     */
    public EnchantmentWrapper(@NotNull EnchantCore plugin, @NotNull String configKey, @Nullable ConfigurationSection section) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configKey = configKey;

        if (section == null) {
            // Critical error if the section is missing
            logger.severe("ConfigurationSection is NULL for enchant key: '" + configKey + "'. Enchantment cannot load!");
            // Set minimal defaults to prevent NPEs, but mark as disabled
            this.rawName = configKey.toLowerCase();
            this.nameFormat = "&cError: " + rawName;
            this.guiNameFormat = "&cError: " + rawName;
            // Generate PDC key even on error to avoid NPEs later, though it won't be used effectively
            this.pdcLevelKey = new NamespacedKey(plugin, "enchant_" + this.rawName + "_error");
            this.enabled = false;
            return; // Stop loading process
        }

        loadFromConfig(section); // Load data from the provided section
    }

    /**
     * Loads all fields from the provided ConfigurationSection.
     * Does NOT translate color codes here; stores raw formats.
     * @param section The ConfigurationSection to load from.
     */
    private void loadFromConfig(@NotNull ConfigurationSection section) {
        final boolean debug = plugin.getConfigManager().isDebugMode();

        // --- Basic Properties ---
        // Use config 'RawName' if provided, otherwise default to the section key
        this.rawName = section.getString("RawName", configKey).toLowerCase().trim();
        if (this.rawName.isEmpty()) {
            logger.warning("Enchantment with config key '" + configKey + "' has an empty RawName! Using config key as RawName.");
            this.rawName = configKey.toLowerCase().trim();
        }
        // Generate the NamespacedKey for PDC storage based on the rawName
        this.pdcLevelKey = new NamespacedKey(plugin, "enchant_" + this.rawName);
        // Load display names (store with format codes)
        this.nameFormat = section.getString("Name", "&f" + this.rawName); // Default to raw name if Name missing
        this.guiNameFormat = section.getString("GuiName", this.nameFormat); // Default GUI name to regular name
        this.enabled = section.getBoolean("Enabled", true); // Default to enabled
        if (debug) logger.finest("Loading enchant '" + configKey + "' -> RawName: " + this.rawName + ", Enabled: " + this.enabled);
        // --- End Basic ---


        // --- Appearance ---
        try {
            String matName = section.getString("Material", "BOOK").toUpperCase();
            this.material = Material.valueOf(matName);
            if (this.material == Material.AIR) { // Don't allow AIR as material
                logger.warning("Invalid material 'AIR' for enchant '" + rawName + "'. Defaulting to BOOK.");
                this.material = Material.BOOK;
            }
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid material '" + section.getString("Material") + "' for enchant '" + rawName + "'. Defaulting to BOOK.");
            this.material = Material.BOOK;
        }
        this.base64Texture = section.getString("Base64", null); // For player heads
        this.customModelData = section.getInt("CustomModelData", 0);
        this.inGuiSlot = section.getInt("InGuiSlot", -1); // -1 means not shown unless calculated
        // --- End Appearance ---


        // --- Leveling and Cost ---
        this.maxLevel = Math.max(1, section.getInt("MaxLevel", 1)); // Ensure max level is at least 1
        this.cost = section.getDouble("Cost", 0.0);
        this.increaseCostBy = section.getDouble("IncreaseCostBy", 0.0);
        this.pickaxeLevelRequired = Math.max(0, section.getInt("PickaxeLevelRequired", 0)); // Ensure non-negative

// --- ADDED: Load the Currency type for this specific enchant ---
        String currencyString = section.getString("Currency", null);
        if (currencyString != null) {
            try {
                this.currencyType = ConfigManager.CurrencyType.valueOf(currencyString.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid Currency '" + currencyString + "' for enchant '" + rawName + "'. Defaulting to global setting.");
                this.currencyType = plugin.getConfigManager().getCurrencyType(); // Fallback to global
            }
        } else {
            // If 'Currency' is not set in the enchant's config, use the global default from config.yml
            this.currencyType = plugin.getConfigManager().getCurrencyType();
        }
// --- END ADDED ---

// Load and validate CostFormula
        this.costFormula = section.getString("CostFormula", "LINEAR").toUpperCase();
        if (!this.costFormula.equals("LINEAR") && !this.costFormula.equals("EXPONENTIAL")) {
            logger.warning("Invalid CostFormula '" + section.getString("CostFormula") + "' for enchant '" + rawName + "'. Defaulting to LINEAR.");
            this.costFormula = "LINEAR";
        }
// Sanity check cost values for exponential to prevent issues
        if ("EXPONENTIAL".equals(this.costFormula) && this.increaseCostBy <= 1.0 && this.maxLevel != 1) {
            logger.warning("Exponential enchant '" + rawName + "' has IncreaseCostBy <= 1.0 (" + this.increaseCostBy + "). This will result in non-increasing or decreasing costs! Consider using LINEAR or IncreaseCostBy > 1.0.");
        }
        if ("LINEAR".equals(this.costFormula) && this.increaseCostBy < 0 && this.maxLevel != 1) {
            logger.warning("Linear enchant '" + rawName + "' has a negative IncreaseCostBy (" + this.increaseCostBy + "). Cost will decrease per level.");
        } // Ensure non-negative
        // Load and validate CostFormula
        this.costFormula = section.getString("CostFormula", "LINEAR").toUpperCase();
        if (!this.costFormula.equals("LINEAR") && !this.costFormula.equals("EXPONENTIAL")) {
            logger.warning("Invalid CostFormula '" + section.getString("CostFormula") + "' for enchant '" + rawName + "'. Defaulting to LINEAR.");
            this.costFormula = "LINEAR";
        }
        // Sanity check cost values for exponential to prevent issues
        if ("EXPONENTIAL".equals(this.costFormula) && this.increaseCostBy <= 1.0 && this.maxLevel != 1) {
            logger.warning("Exponential enchant '" + rawName + "' has IncreaseCostBy <= 1.0 (" + this.increaseCostBy + "). This will result in non-increasing or decreasing costs! Consider using LINEAR or IncreaseCostBy > 1.0.");
            // Optionally default IncreaseCostBy to 1.001 or similar? For now, just warn.
            // this.increaseCostBy = 1.001;
        }
        if ("LINEAR".equals(this.costFormula) && this.increaseCostBy < 0 && this.maxLevel != 1) {
            logger.warning("Linear enchant '" + rawName + "' has a negative IncreaseCostBy (" + this.increaseCostBy + "). Cost will decrease per level.");
        }
        // --- End Leveling ---


        // --- Text Lists (Store raw with format codes) ---
        this.descriptionList = section.getStringList("Description"); // Can be empty
        this.configuredLore = section.getStringList("lore"); // Can be empty
        // --- End Text ---


        // --- Determine Type (Passive/Active) ---
        // Simple determination based on known passive enchant keys
        // Add other passive types like 'jump', 'fly' if implemented
        if (this.rawName.equals("haste") || this.rawName.equals("speed")) {
            this.type = EnchantType.PASSIVE;
        } else {
            this.type = EnchantType.ACTIVE; // Default to active
        }
        if (debug) logger.finest(" -> Determined Type: " + this.type + " for " + this.rawName);
        // --- End Type ---


        // --- Vanilla Enchantment Linking ---
        this.minecraftEnchantKey = section.getString("MinecraftEnchantKey", null);
        this.isVanilla = false; // Reset default
        this.bukkitEnchantment = null;
        if (this.minecraftEnchantKey != null && !this.minecraftEnchantKey.isEmpty()) {
            NamespacedKey key = NamespacedKey.fromString(this.minecraftEnchantKey.toLowerCase());
            if (key != null) {
                // Try to get the Bukkit enchantment from the registry
                try {
                    this.bukkitEnchantment = Registry.ENCHANTMENT.get(key);
                    this.isVanilla = (this.bukkitEnchantment != null);
                    if (!isVanilla && debug) {
                        logger.warning("[Debug] Could not find vanilla Bukkit Enchantment for key: '" + this.minecraftEnchantKey + "' used by enchant '" + rawName + "'.");
                    } else if (isVanilla && debug) {
                        logger.finest(" -> Linked to vanilla enchant: " + this.bukkitEnchantment.getKey());
                    }
                } catch (Exception e) { // Catch potential errors during registry lookup
                    logger.log(Level.WARNING, "Error looking up vanilla enchantment for key '" + key + "' in enchant '" + rawName + "': " + e.getMessage());
                    this.isVanilla = false;
                    this.bukkitEnchantment = null;
                }
            } else {
                logger.warning("Invalid MinecraftEnchantKey format: '" + this.minecraftEnchantKey + "' for enchant '" + rawName + "'. Must be 'namespace:key'.");
                this.isVanilla = false;
            }
        }
        // --- End Vanilla Linking ---


        // --- Custom Settings Section ---
        // Store the whole section for specific handlers to access
        this.customSettings = section.getConfigurationSection("Settings");
        // --- End Custom Settings ---
    }

    /**
     * Calculates the cost to upgrade TO a specific target level.
     * Uses the configured cost formula (LINEAR or EXPONENTIAL).
     *
     * @param targetLevel The level being upgraded TO (e.g., 1 for cost of level 1, 2 for cost of level 2).
     * @return The calculated cost, or -1.0 if targetLevel is invalid or exceeds max level. Returns Double.MAX_VALUE on overflow.
     */
    public double getCostForLevel(int targetLevel) {
        // --- Validation ---
        if (targetLevel <= 0) return -1.0; // Cannot upgrade to level 0 or less
        // Check against max level if defined
        if (this.maxLevel > 0 && targetLevel > this.maxLevel) return -1.0; // Cannot upgrade beyond max level
        // --- End Validation ---

        double calculatedCost;
        int levelFactor = Math.max(0, targetLevel - 1); // Number of upgrade steps taken (0 for level 1, 1 for level 2, etc.)

        try {
            if ("EXPONENTIAL".equalsIgnoreCase(this.costFormula)) {
                // Cost formula: base * (multiplier ^ (targetLevel - 1))
                calculatedCost = this.cost; // Cost for level 1
                if (targetLevel > 1) {
                    // Use Math.pow for exponentiation
                    double effectiveMultiplier = Math.max(1.00001, this.increaseCostBy); // Ensure multiplier > 1 for growth
                    // Check for potential overflow BEFORE calculation
                    if (levelFactor > 0 && effectiveMultiplier > 1.0 && this.cost > 0 &&
                            (levelFactor * Math.log(effectiveMultiplier) + Math.log(this.cost) > Math.log(Double.MAX_VALUE))) {
                        return Double.MAX_VALUE;
                    }
                    calculatedCost *= Math.pow(effectiveMultiplier, levelFactor);
                }
            } else { // LINEAR (or default)
                // Cost formula: base + (increment * (targetLevel - 1))
                double increment = this.increaseCostBy;
                // Check for potential overflow BEFORE calculation
                if (increment > 0 && levelFactor > 0 && increment > (Double.MAX_VALUE - this.cost) / levelFactor) {
                    return Double.MAX_VALUE;
                }
                if (increment < 0 && levelFactor > 0 && this.cost < increment * -levelFactor) { // Avoid negative cost resulting from large negative increment
                    calculatedCost = 0; // Cost becomes 0 if increment makes it negative
                } else {
                    calculatedCost = this.cost + (increment * levelFactor);
                }
            }
        } catch (ArithmeticException e) {
            logger.warning("ArithmeticException calculating cost for enchant " + rawName + " level " + targetLevel);
            return Double.MAX_VALUE; // Indicate overflow/error
        }

        // Final checks and formatting
        if (Double.isInfinite(calculatedCost) || Double.isNaN(calculatedCost)) {
            return Double.MAX_VALUE; // Overflow occurred
        }

        // Return the calculated cost, ensuring it's not negative
        return Math.max(0.0, calculatedCost);
    }

    /**
     * Creates the ItemStack representation of this enchantment for the GUI.
     * Applies placeholders and formatting based on current levels and config.
     * Handles color translation at the end.
     *
     * @param currentEnchantLevel The current level of THIS enchant on the pickaxe.
     * @param currentPickaxeLevel The current level of the PICKAXE itself.
     * @param currencyType The currency type being used (TOKENS or VAULT).
     * @param vaultHook VaultHook instance (can be null if not using Vault).
     * @return The formatted ItemStack for the GUI.
     */
    @NotNull
    public ItemStack createGuiItem(int currentEnchantLevel, int currentPickaxeLevel, ConfigManager.CurrencyType currencyType, @Nullable VaultHook vaultHook) {
        ItemStack item = new ItemStack(this.material);
        ItemMeta meta = item.getItemMeta();

        // *** CHECK IF META IS NULL FIRST ***
        if (meta == null) {
            logger.warning("Failed to get ItemMeta for GUI item: " + this.material + " (Enchant: " + rawName + ")");
            ItemStack errorItem = new ItemStack(Material.BARRIER);
            ItemMeta errorMeta = errorItem.getItemMeta();
            if (errorMeta != null) {
                errorMeta.setDisplayName(ChatColor.RED + "Error: " + rawName);
                errorItem.setItemMeta(errorMeta);
            }
            return errorItem; // Return error item
        }

        // --- Cache Managers ---
        MessageManager messageManager = plugin.getMessageManager();
        // --- End Cache ---

        // --- Calculate State ---
        boolean isMaxed = (this.maxLevel > 0 && currentEnchantLevel >= this.maxLevel);
        boolean reqsMet = (this.pickaxeLevelRequired <= 0 || currentPickaxeLevel >= this.pickaxeLevelRequired);
        double costForNextSingleLevel = isMaxed ? -1.0 : getCostForLevel(currentEnchantLevel + 1);
        String naText = messageManager.getMessage("gui.text_not_applicable", "&7N/A");
        // --- End State ---

        // --- Set Display Name ---
        String rawGuiNameString = this.guiNameFormat + (currentEnchantLevel > 0 ? " " + currentEnchantLevel : "");
        meta.setDisplayName(ColorUtils.translateColors(rawGuiNameString));
        // --- End Display Name ---

        // --- Set Model Data ---
        if (this.customModelData > 0) meta.setCustomModelData(this.customModelData);
        // --- End Model Data ---

        // --- Generate Lore ---
        List<String> loreLinesRaw = new ArrayList<>();
        // ... (rest of lore generation logic remains the same) ...
        if (!this.configuredLore.isEmpty()) {
            for (String lineFromConfig : this.configuredLore) {
                String processedLine = applyPlaceholdersToLine(lineFromConfig, currentEnchantLevel, costForNextSingleLevel, currencyType, vaultHook, naText, isMaxed, messageManager);
                if (processedLine != null) {
                    loreLinesRaw.add(processedLine);
                }
            }
        } else {
            // Fallback lore generation...
            if (plugin.getConfigManager().isDebugMode()) { // Null check needed? Assumed ConfigManager exists if wrapper does
                logger.finer("[EnchantCore] Enchant '" + rawName + "' has no 'lore:' defined. Using fallback GUI lore.");
            }
            if (this.descriptionList != null && !this.descriptionList.isEmpty()) {
                for(String descLine : this.descriptionList) {
                    loreLinesRaw.add(applyPlaceholdersToLine(descLine, currentEnchantLevel, costForNextSingleLevel, currencyType, vaultHook, naText, isMaxed, messageManager));
                }
                loreLinesRaw.add(messageManager.getMessage("gui.text_spacer", " "));
            }
            loreLinesRaw.add(applyPlaceholdersToLine(messageManager.getMessage("gui.lore_current_level", "&eLevel: &f%current_level%"), currentEnchantLevel, costForNextSingleLevel, currencyType, vaultHook, naText, isMaxed, messageManager));
            loreLinesRaw.add(applyPlaceholdersToLine(messageManager.getMessage("gui.lore_max_level", "&eMax: &f%max_level%"), currentEnchantLevel, costForNextSingleLevel, currencyType, vaultHook, naText, isMaxed, messageManager));
            loreLinesRaw.add(applyPlaceholdersToLine(messageManager.getMessage("gui.lore_price", "&eCost: &f%cost%"), currentEnchantLevel, costForNextSingleLevel, currencyType, vaultHook, naText, isMaxed, messageManager));
            if (customSettings != null) {
                addConditionalLoreLines(loreLinesRaw, currentEnchantLevel, costForNextSingleLevel, currencyType, vaultHook, naText, isMaxed, messageManager);
            }
            loreLinesRaw.add(messageManager.getMessage("gui.text_spacer", " "));
        }
        // Add prompts/status...
        if (!isMaxed) {
            if (!reqsMet) {
                loreLinesRaw.add(messageManager.getMessage("gui.pickaxe_level_required", "&cRequires Pickaxe Level %level%!")
                        .replace("%level%", String.valueOf(this.pickaxeLevelRequired)));
            } else {
                loreLinesRaw.addAll(messageManager.getMessageList("gui.click_prompts",
                        Arrays.asList("&aLeft-Click: &f+1", "&aRight-Click: &f+10", "&aShift+R-Click: &f+50")));
            }
        } else {
            loreLinesRaw.add(messageManager.getMessage("gui.text_spacer", " "));
            loreLinesRaw.add(messageManager.getMessage("gui.max_level_generic", "&aMax Level Reached"));
        }
        // Final lore processing...
        meta.setLore(loreLinesRaw.stream()
                .filter(Objects::nonNull)
                .map(ColorUtils::translateColors)
                .collect(Collectors.toList()));
        // --- End Lore Generation ---


        // --- Set Flags and Glow ---
        // *** ADD FLAGS *AFTER* THE NULL CHECK AND OTHER META OPERATIONS ***
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_UNBREAKABLE);

        if (currentEnchantLevel > 0) {
            if (!this.isVanilla) {
                meta.addEnchant(Enchantment.LUCK, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        // --- End Flags and Glow ---


        // Apply the completed meta
        if (!item.setItemMeta(meta)) {
            logger.warning("Failed to set ItemMeta for GUI item: " + this.material + " (Enchant: " + rawName + ")");
        }
        return item;
    }

    /**
     * Helper method to add lore lines based on common 'Settings' entries.
     * Called only during fallback lore generation.
     */
    private void addConditionalLoreLines(List<String> loreLinesRaw, int currentEnchantLevel, double costForNextSingleLevel, ConfigManager.CurrencyType currencyType, @Nullable VaultHook vaultHook, String naText, boolean isMaxed, MessageManager messageManager) {
        boolean addedSeparator = false; // Use boolean flag, not lambda

        // Check and add lines for specific settings
        if (customSettings.contains("ChanceBase")) {
            if (!addedSeparator) { // Check flag before adding separator
                loreLinesRaw.add(messageManager.getMessage("gui.text_separator_attributes", "&m--------------------"));
                addedSeparator = true;
            }
            loreLinesRaw.add(applyPlaceholdersToLine(messageManager.getMessage("gui.lore_chance", "&eChance: &f%chance%%"), currentEnchantLevel, costForNextSingleLevel, currencyType, vaultHook, naText, isMaxed, messageManager));
        }
        if (customSettings.contains("RadiusTier") || customSettings.contains("Radius") || customSettings.contains("RadiusBase")) {
            if (!addedSeparator) { // Check flag
                loreLinesRaw.add(messageManager.getMessage("gui.text_separator_attributes", "&m--------------------"));
                addedSeparator = true;
            }
            loreLinesRaw.add(applyPlaceholdersToLine(messageManager.getMessage("gui.lore_radius_blocks", "&eRadius: &f%radius% blocks"), currentEnchantLevel, costForNextSingleLevel, currencyType, vaultHook, naText, isMaxed, messageManager));
        }
        if (customSettings.contains("RewardMinBase") && customSettings.contains("RewardMaxBase")) {
            if (!addedSeparator) { // Check flag
                loreLinesRaw.add(messageManager.getMessage("gui.text_separator_attributes", "&m--------------------"));
                addedSeparator = true;
            }
            String key = "gui.lore_amount_" + (rawName.equalsIgnoreCase("tokenator") || rawName.equalsIgnoreCase("blessing") ? "tokens" : "money");
            String def = "&eAmount: &f%min_amount% - %max_amount%";
            loreLinesRaw.add(applyPlaceholdersToLine(messageManager.getMessage(key, def), currentEnchantLevel, costForNextSingleLevel, currencyType, vaultHook, naText, isMaxed, messageManager));
        }
        if (customSettings.contains("DurationBase") && customSettings.contains("MultiplierBase")) {
            if (!addedSeparator) { // Check flag
                loreLinesRaw.add(messageManager.getMessage("gui.text_separator_attributes", "&m--------------------"));
                addedSeparator = true;
            }
            String key = "gui.lore_effect_duration_multiplier";
            String def = "&eEffect: &fx%multiplier% &7for &f%duration%s";
            loreLinesRaw.add(applyPlaceholdersToLine(messageManager.getMessage(key, def), currentEnchantLevel, costForNextSingleLevel, currencyType, vaultHook, naText, isMaxed, messageManager));
        }
        // Add more conditional lines here for other common settings if needed
    }

    /**
     * Applies all relevant placeholders to a single line of text.
     * Handles cost formatting, level display, and specific enchant settings placeholders.
     * Returns the raw string with placeholders replaced, but potentially still containing format codes.
     *
     * @param line                       The input string line.
     * @param currentEnchantLevel        Current level of this enchant.
     * @param costForNextPotentialLevel Cost for the next single level upgrade (-1 if maxed/error).
     * @param currencyType               The currency being used (legacy, now uses internal currencyType).
     * @param vaultHook                  VaultHook instance (can be null).
     * @param naText                     The string to use for "Not Applicable".
     * @param isActuallyMaxed            True if the enchant is truly at its max level.
     * @param messageManager             MessageManager instance for accessing messages.
     * @return The processed string, or the original line if input is null.
     */
    @Nullable
    private String applyPlaceholdersToLine(
            @Nullable String line,
            int currentEnchantLevel,
            double costForNextPotentialLevel,
            ConfigManager.CurrencyType currencyType, // This parameter is now less important but kept for compatibility
            @Nullable VaultHook vaultHook,
            @NotNull String naText, // Assumed not null
            boolean isActuallyMaxed,
            @NotNull MessageManager messageManager // Pass manager instance
    ) {
        if (line == null) return null; // Return null if line is null
        String result = line; // Start with original line

        // --- Basic Info Placeholders ---
        // Use internal fields which store the raw formats
        result = result.replace("%enchant_name%", this.nameFormat);
        result = result.replace("%level%", String.valueOf(currentEnchantLevel));
        result = result.replace("%current_level%", String.valueOf(currentEnchantLevel));
        result = result.replace("%max_level%", (this.maxLevel > 0 ? String.valueOf(this.maxLevel) : messageManager.getMessage("gui.text_unlimited", "Unlimited")));
        result = result.replace("%pickaxe_level%", String.valueOf(this.pickaxeLevelRequired));
        result = result.replace("%pickaxe_level_required%", String.valueOf(this.pickaxeLevelRequired));
        // --- End Basic Info ---


        // --- Cost Placeholder (UPDATED LOGIC) ---
        if (result.contains("%cost%")) {
            String costDisplayValue;
            if (isActuallyMaxed) {
                costDisplayValue = messageManager.getMessage("gui.max_level_suffix", "&7(Maxed)");
            } else if (costForNextPotentialLevel < 0 || costForNextPotentialLevel == Double.MAX_VALUE) {
                costDisplayValue = naText; // Use cached N/A text
            } else {
                String formattedCost;
                String currencyName;

                // Use this enchant's specific currency type
                ConfigManager.CurrencyType enchantCurrency = this.getCurrencyType();

                switch (enchantCurrency) {
                    case GEMS:
                        formattedCost = FORMAT_INTEGER.format((long) Math.ceil(costForNextPotentialLevel));
                        currencyName = " " + messageManager.getMessage("currency.gems_name_plural", "Gems");
                        break;
                    case TOKENS:
                        formattedCost = FORMAT_INTEGER.format((long) Math.ceil(costForNextPotentialLevel));
                        currencyName = " " + messageManager.getMessage("currency.tokens_name_plural", "Tokens");
                        break;
                    case VAULT:
                    default:
                        formattedCost = (vaultHook != null && vaultHook.isEnabled())
                                ? vaultHook.format(costForNextPotentialLevel)
                                : String.format(Locale.US, "%,.2f", costForNextPotentialLevel);
                        currencyName = ""; // Vault format usually includes the symbol
                        break;
                }
                costDisplayValue = formattedCost + currencyName;
            }
            result = result.replace("%cost%", costDisplayValue);
        }
        // --- End Cost ---

        // --- Description Placeholder (First line only) ---
        if (result.contains("%description%")) {
            String description = (this.descriptionList != null && !this.descriptionList.isEmpty())
                    ? this.descriptionList.get(0) : ""; // Use first line or empty
            result = result.replace("%description%", description);
        }
        // --- Multiline Description (if needed) ---
        // Placeholder: %description_multiline% (Example) - Replaces with all description lines separated by newline
        if (result.contains("%description_multiline%") && this.descriptionList != null) {
            String multiLineDesc = String.join("\n", this.descriptionList); // Join with newline
            result = result.replace("%description_multiline%", multiLineDesc);
        }
        // --- End Description ---


        // --- Placeholders Based on 'Settings' ---
        if (customSettings != null) {
            int levelFactor = Math.max(0, currentEnchantLevel > 0 ? currentEnchantLevel - 1 : 0); // Steps taken

            if (result.contains("%burst_count%")) {
                int base = customSettings.getInt("BurstCountBase", 0);
                int increase = customSettings.getInt("BurstCountIncreasePerLevel", 0);
                result = result.replace("%burst_count%", String.valueOf(base + (increase * levelFactor)));

            }

            if (result.contains("%gems_per_block%")) {
                int base = customSettings.getInt("GemsPerBlockBase", 0);
                int increase = customSettings.getInt("GemsPerBlockIncreasePerLevel", 0);
                result = result.replace("%gems_per_block%", String.valueOf(base + (increase * levelFactor)));
            }

            if (result.contains("%burst_radius%")) {
                result = result.replace("%burst_radius%", customSettings.getString("BurstRadius", "0"));
            }

            // Chance (Formatted as percentage)
            if (result.contains("%chance%")) {
                // Handle escaped %%chance%% if needed: result = result.replace("%%chance%%", "{TEMP_CHANCE}");
                double chanceVal = customSettings.getDouble("ChanceBase", -1.0); // Use -1 to indicate not set
                if (chanceVal >= 0) {
                    chanceVal += (customSettings.getDouble("ChanceIncreasePerLevel", 0.0) * levelFactor);
                    chanceVal = Math.min(100.0, Math.max(0.0, chanceVal * 100.0)); // Calculate %, clamp [0, 100]
                    result = result.replace("%chance%", FORMAT_PERCENTAGE.format(chanceVal)); // Format
                } else {
                    result = result.replace("%chance%", naText); // Setting not found
                }
                // result = result.replace("{TEMP_CHANCE}", "%chance%"); // Restore escaped
            }


            if (result.contains("%tokens_per_block%")) {
                if (customSettings.contains("TokensPerBlockBase")) {
                    int tokensBase = customSettings.getInt("TokensPerBlockBase", 3);
                    int tokensInc = customSettings.getInt("TokensPerBlockIncrease", 1);
                    int tokensPerBlock = tokensBase + (tokensInc * levelFactor);
                    result = result.replace("%tokens_per_block%", String.valueOf(Math.max(1, tokensPerBlock)));
                } else {
                    result = result.replace("%tokens_per_block%", naText);
                }
            }


            // Radius (Formatted to one decimal place)
            if (result.contains("%radius%")) {
                if (this.rawName.equalsIgnoreCase("explosive") && customSettings.contains("RadiusTier")) { // Specific logic for Explosive tiers
                    int radiusTier = customSettings.getInt("RadiusTier", 2);
                    int actualRadius = 3;
                    switch (radiusTier) {
                        case 1:
                            actualRadius = 2;
                            break;
                        case 3:
                            actualRadius = 4;
                            break;
                        case 4:
                            actualRadius = 5;
                            break;
                        case 5:
                            actualRadius = 6;
                            break;
                    }
                    result = result.replace("%radius%", String.valueOf(actualRadius));
                } else if (customSettings.contains("Radius")) { // Flat radius
                    result = result.replace("%radius%", String.valueOf(customSettings.getInt("Radius")));
                } else if (customSettings.contains("RadiusBase")) { // Scaled radius
                    double radiusVal = customSettings.getDouble("RadiusBase", 0.0) + (customSettings.getDouble("RadiusIncreasePerLevel", 0.0) * levelFactor);
                    result = result.replace("%radius%", FORMAT_ONE_DECIMAL.format(radiusVal));
                } else {
                    result = result.replace("%radius%", naText);
                }
            }

            // Min/Max Amounts (Formatted as integers)
            if (result.contains("%min_amount%") || result.contains("%max_amount%")) {
                if (customSettings.contains("RewardMinBase") && customSettings.contains("RewardMaxBase")) {
                    double minBase = customSettings.getDouble("RewardMinBase", 0.0);
                    double minInc = customSettings.getDouble("RewardMinIncreasePerLevel", 0.0);
                    double maxBase = customSettings.getDouble("RewardMaxBase", 0.0);
                    double maxInc = customSettings.getDouble("RewardMaxIncreasePerLevel", 0.0);
                    // Calculate current min/max as doubles first
                    double currentMin = Math.max(0.0, minBase + (minInc * levelFactor));
                    double currentMax = Math.max(currentMin, maxBase + (maxInc * levelFactor)); // Ensure max >= min
                    // Format as integers using static formatter
                    result = result.replace("%min_amount%", FORMAT_INTEGER.format((long) Math.floor(currentMin)));
                    result = result.replace("%max_amount%", FORMAT_INTEGER.format((long) Math.floor(currentMax)));
                } else {
                    result = result.replace("%min_amount%", naText).replace("%max_amount%", naText);
                }
            }

            // Duration (Formatted as integer seconds)
            if (result.contains("%duration%")) {
                if (customSettings.contains("DurationBase")) {
                    int durationVal = customSettings.getInt("DurationBase", 0) + (customSettings.getInt("DurationIncreasePerLevel", 0) * levelFactor);
                    result = result.replace("%duration%", String.valueOf(Math.max(0, durationVal)));
                } else {
                    result = result.replace("%duration%", naText);
                }
            }

            // Multiplier (Formatted to one decimal place)
            if (result.contains("%multiplier%")) {
                if (customSettings.contains("MultiplierBase")) {
                    double multVal = customSettings.getDouble("MultiplierBase", 1.0) + (customSettings.getDouble("MultiplierIncreasePerLevel", 0) * levelFactor);
                    result = result.replace("%multiplier%", FORMAT_ONE_DECIMAL.format(Math.max(0.0, multVal))); // Ensure non-negative
                } else {
                    result = result.replace("%multiplier%", naText);
                }
            }

            // --- Generic Handling for other settings (Use with caution) ---
            // This allows simple placeholders like %my_custom_setting% if defined under Settings:
            // Be careful not to overwrite placeholders handled above.
            for (String key : customSettings.getKeys(false)) {
                String placeholder = "%" + key.toLowerCase().replace("-", "_") + "%";
                // Check if it's a simple placeholder not handled by specific logic
                if (result.contains(placeholder) && !isComplexCalculatedPlaceholder(placeholder)) {
                    result = result.replace(placeholder, customSettings.getString(key, naText)); // Replace with string value
                }
            }
            // --- End Generic Handling ---

        } else {
            // If customSettings is null, replace complex placeholders with N/A
            result = result.replace("%chance%", naText).replace("%radius%", naText)
                    .replace("%min_amount%", naText).replace("%max_amount%", naText)
                    .replace("%duration%", naText).replace("%multiplier%", naText);
            // Add any other complex placeholders here
        }
        // --- End Settings Placeholders ---

        // Handle escaped percentages (%% -> %) last
        result = result.replace("%%", "%");

        // Return the processed line, still containing format codes (&, #)
        return result;
    }

    /**
     * Helper to identify placeholders that have specific calculation logic
     * and shouldn't be replaced by the generic setting handler.
     * @param placeholder The placeholder string (e.g., "%chance%").
     * @return True if it's a complex placeholder, false otherwise.
     */
    private boolean isComplexCalculatedPlaceholder(String placeholder) {
        switch(placeholder) {
            case "%chance%":
            case "%radius%":
            case "%min_amount%":
            case "%max_amount%":
            case "%duration%":
            case "%multiplier%":
            case "%cost%":
            case "%level%":
            case "%current_level%":
            case "%max_level%":
            case "%pickaxe_level%":
            case "%pickaxe_level_required%":
            case "%description%":
            case "%description_multiline%": // Example if added
            case "%enchant_name%":
            case "%burst_count%":
            case "%burst_radius%":
            case "%gems_per_block%":
                return true;
            default:
                return false;
        }
    }


    // --- Standard Getters ---
    // Returns name format WITH codes
    public String getNameFormat() { return nameFormat; }
    // Returns GUI name format WITH codes
    public String getGuiNameFormat() { return guiNameFormat; }
    // Returns name translated to § codes
    public String getDisplayName() { return ColorUtils.translateColors(nameFormat); }
    // Returns GUI name translated to § codes
    public String getGuiName() { return ColorUtils.translateColors(guiNameFormat); }
    // Other getters...
    public List<String> getDescriptionList() { return Collections.unmodifiableList(descriptionList); }
    public List<String> getConfiguredLore() { return Collections.unmodifiableList(configuredLore); }
    public boolean isPassive() { return this.type == EnchantType.PASSIVE; }
    public String getConfigKey() { return configKey; }
    public String getRawName() { return rawName; }
    public Material getMaterial() { return material; }
    @Nullable public String getBase64Texture() { return base64Texture; }
    public int getCustomModelData() { return customModelData; }
    public boolean isEnabled() { return enabled; }
    public int getInGuiSlot() { return inGuiSlot; }
    public int getMaxLevel() { return maxLevel; }
    public double getCost() { return cost; }
    public double getIncreaseCostBy() { return increaseCostBy; }
    public String getCostFormula() { return costFormula; }
    public int getPickaxeLevelRequired() { return pickaxeLevelRequired; }
    public boolean isVanilla() { return isVanilla; }
    @Nullable public String getMinecraftEnchantKey() { return minecraftEnchantKey; }
    @Nullable public Enchantment getBukkitEnchantment() { return bukkitEnchantment; }
    @Nullable public ConfigurationSection getCustomSettings() { return customSettings; }
    @NotNull public NamespacedKey getPdcLevelKey() { return pdcLevelKey; } // Already ensured not null in constructor/load
    public EnchantType getType() { return type; }
    public ConfigManager.CurrencyType getCurrencyType() { return currencyType; }

    @Override
    public String toString() {
        return "EnchantmentWrapper{" +
                "configKey='" + configKey + '\'' +
                ", rawName='" + rawName + '\'' +
                ", enabled=" + enabled +
                ", type=" + type +
                ", maxLevel=" + maxLevel +
                ", isVanilla=" + isVanilla +
                '}';
    }
}