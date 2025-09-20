package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.ConfigManager;
import com.strikesenchantcore.config.EnchantManager; // Import EnchantManager
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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.ChatColor; // For ChatColor constants like ChatColor.RED
import org.bukkit.SoundCategory; // Import SoundCategory
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// --- ADDED/KEPT IMPORTS ---
import java.util.ArrayList;
import java.util.Arrays; // Import Arrays for default lore list
import java.util.List;
import java.util.Map;
import java.util.Objects; // Import Objects for null check stream
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger
import java.text.NumberFormat;
import java.util.Locale;
import java.util.stream.Collectors;
// --- END IMPORTS ---

public class EnchantGUI implements InventoryHolder {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    private final Player player;
    private final PlayerData playerData;
    private final ItemStack pickaxe; // The pickaxe instance this GUI was opened with
    private final Inventory inventory;

    // --- Cached Managers ---
    private final EnchantRegistry enchantRegistry;
    private final PickaxeManager pickaxeManager;
    private final PlayerDataManager playerDataManager;
    private final VaultHook vaultHook;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final EnchantManager enchantManager; // Cache EnchantManager
    // --- End Cached Managers ---

    // Menu button slots
    private static final int[] TOKEN_MENU_SLOTS = {0, 1, 2}; // Main enchants (current implementation)
    private static final int[] GEMS_MENU_SLOTS = {3, 4, 5}; // Gems enchants (future)
    private static final int[] REBIRTH_MENU_SLOTS = {6, 7, 8}; // Rebirth enchants (future)
    private static final int[] ADDITIONAL_MENU_SLOTS = {45, 47, 51, 53}; // Additional menus (future)

    // Changed: Now using the close button slot for the info item
    private int getInfoItemSlot() {
        return 49; // Keep it fixed at slot 49
    }

    public EnchantGUI(@NotNull EnchantCore plugin, @NotNull Player player, @NotNull PlayerData playerData, @NotNull ItemStack pickaxe) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.player = player;
        this.playerData = playerData;
        this.pickaxe = pickaxe; // Store the specific pickaxe instance

        // --- Initialize Cached Managers ---
        this.enchantRegistry = plugin.getEnchantRegistry();
        this.pickaxeManager = plugin.getPickaxeManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.vaultHook = plugin.getVaultHook();
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        this.enchantManager = plugin.getEnchantManager(); // Initialize EnchantManager cache
        // --- End Initialization ---

        // Validate managers before proceeding
        if (enchantRegistry == null || pickaxeManager == null || playerDataManager == null ||
                vaultHook == null || configManager == null || messageManager == null || enchantManager == null) {
            String nullManager = enchantRegistry == null ? "EnchantRegistry" :
                    pickaxeManager == null ? "PickaxeManager" :
                            // ... add others
                            "Unknown Manager";
            logger.severe("Manager is null during EnchantGUI creation: " + nullManager + "! Cannot create GUI.");
            throw new IllegalStateException("One or more managers are null during EnchantGUI creation for player " + player.getName());
        }

        // --- Create Inventory ---
        String titleFormat = enchantManager.getGuiTitleFormat();
        String title = pickaxeManager.applyPlaceholders(titleFormat, player, playerData, PDCUtil.getPickaxeLevel(pickaxe), PDCUtil.getPickaxeBlocksMined(pickaxe), pickaxe);
        int guiSize = enchantManager.getGuiSize();
        this.inventory = Bukkit.createInventory(this, guiSize, title);
        // --- End Inventory Creation ---

        populateGUI(); // Fill the GUI with items
    }

    /**
     * Populates the GUI with background filler, enchantment items, info item, and menu buttons.
     */
    private void populateGUI() {
        if (inventory == null) {
            logger.severe("Inventory is null in populateGUI for player " + player.getName());
            return;
        }

        fillBackground(); // This can be removed/commented out if you don't want a background
        addMenuButtons();

        Map<String, Integer> currentEnchantsOnPickaxe = pickaxeManager.getAllEnchantLevels(this.pickaxe);
        int currentPickaxeLevel = PDCUtil.getPickaxeLevel(this.pickaxe);

        for (EnchantmentWrapper enchant : enchantRegistry.getAllEnchants()) {
            // This is the key change: It skips Gem enchants
            if (!enchant.isEnabled() || enchant.getCurrencyType() == ConfigManager.CurrencyType.GEMS) {
                continue;
            }

            int slot = enchant.getInGuiSlot();
            if (slot >= 0 && slot < inventory.getSize() && !isMenuButtonSlot(slot)) {
                try {
                    int currentLevelOfThisEnchant = currentEnchantsOnPickaxe.getOrDefault(enchant.getRawName().toLowerCase(), 0);
                    inventory.setItem(slot, enchant.createGuiItem(currentLevelOfThisEnchant, currentPickaxeLevel, enchant.getCurrencyType(), vaultHook));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error creating GUI item for enchant " + enchant.getRawName() + " for player " + player.getName(), e);
                }
            }
        }

        addInfoItem();
    }

    /**
     * Adds menu navigation buttons to the GUI.
     */
    private void addMenuButtons() {
        // Main enchants menu buttons (slots 0, 1, 2)
        for (int slot : TOKEN_MENU_SLOTS) { // Renamed from MAIN_MENU_SLOTS for clarity
            ItemStack mainButton = createGuiItemHelper(Material.BARRIER, "&c&lToken Enchants",
                    List.of("&7Click to view main enchants", "&c&lUNDER DEVELOPMENT"), 0, false);
            inventory.setItem(slot, mainButton);
        }

        // Gems/Gems enchants menu buttons (slots 3, 4, 5)
        for (int slot : GEMS_MENU_SLOTS) { // Renamed from GEMS_MENU_SLOTS for clarity
            ItemStack gemsButton = createGuiItemHelper(Material.BARRIER, "&c&lGems Enchants",
                    List.of("&7Click to view gems enchants", "&c&lUNDER DEVELOPMENT"), 0, false);
            inventory.setItem(slot, gemsButton);
        }

        // Rebirth enchants menu buttons (slots 6, 7, 8)
        for (int slot : REBIRTH_MENU_SLOTS) {
            ItemStack rebirthButton = createGuiItemHelper(Material.BARRIER, "&d&lRebirth Enchants",
                    List.of("&7Click to view rebirth enchants", "&c&lUNDER DEVELOPMENT"), 0, false);
            inventory.setItem(slot, rebirthButton);
        }

        // --- RESTORED: Additional menu buttons at the bottom ---
        if (45 < inventory.getSize()) {
            ItemStack pickaxeSkinsButton = createGuiItemHelper(Material.BARRIER, "&6&lPickaxe Skins",
                    List.of("&7Click to view pickaxe skins"), 0, false);
            inventory.setItem(45, pickaxeSkinsButton);
        }
        if (47 < inventory.getSize()) {
            ItemStack mortarButton = createGuiItemHelper(Material.BARRIER, "&6&lMortar",
                    List.of("&7Click to access mortar upgrades", "&7and view activation status"), 0, false);
            inventory.setItem(47, mortarButton);
        }
        if (51 < inventory.getSize()) {
            ItemStack crystalsButton = createGuiItemHelper(Material.BARRIER, "&6&lCrystals",
                    List.of("&7Click to view crystals", "&c&lUNDER DEVELOPMENT"), 0, false);
            inventory.setItem(51, crystalsButton);
        }
        if (53 < inventory.getSize()) {
            ItemStack attachmentsButton = createGuiItemHelper(Material.BARRIER, "&6&lAttachments",
                    List.of("&7Click to manage your attachments", "&7and boost enchant proc rates"), 0, true);
            inventory.setItem(53, attachmentsButton);
        }
    }

    /**
     * Checks if a slot is used for menu buttons.
     */
    private boolean isMenuButtonSlot(int slot) {
        // This corrected version includes ALL of your menu buttons
        return (slot >= 0 && slot <= 8) || slot == 45 || slot == 47 || slot == 51 || slot == 53;
    }

    /**
     * Fills empty slots in the GUI with the configured filler item, if enabled.
     */
    private void fillBackground() {
        Material fillerMat = enchantManager.getGuiFillerMaterial();
        // *** Use CORRECT getter name ***
        String fillerNameFormat = enchantManager.getGuiFillerNameFormat();
        int fillerModelData = enchantManager.getGuiFillerCustomModelData();
        boolean fillEmpty = enchantManager.isGuiFillEmptySlots();

        if (!fillEmpty || fillerMat == null || fillerMat == Material.AIR) return;

        // Create the filler item once using the format string
        ItemStack filler = createGuiItemHelper(fillerMat, fillerNameFormat, null, fillerModelData, false);

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Creates and adds the informational item (displaying the pickaxe) to the GUI.
     * Now placed in what was previously the close button slot.
     */
    private void addInfoItem() {
        try {
            pickaxeManager.updatePickaxe(this.pickaxe, player);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating pickaxe meta before creating info item for " + player.getName(), e);
            inventory.setItem(getInfoItemSlot(), createGuiItemHelper(Material.BARRIER, "&cError Displaying Pickaxe", List.of("&7Update failed"), 0, false));
            return;
        }

        ItemStack infoPickaxeClone = this.pickaxe.clone();
        ItemMeta meta = infoPickaxeClone.getItemMeta();
        if (meta == null) {
            logger.warning("Failed to get ItemMeta from cloned pickaxe for info item (Player: " + player.getName() + ")");
            inventory.setItem(getInfoItemSlot(), createGuiItemHelper(Material.BARRIER, "&cError Displaying Pickaxe", List.of("&7Could not get meta"), 0, false));
            return;
        }

        List<String> currentLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!currentLore.isEmpty() && currentLore.get(currentLore.size()-1) != null && !currentLore.get(currentLore.size()-1).trim().isEmpty()) {
            currentLore.add("");
        }

        ConfigManager.CurrencyType currency = configManager.getCurrencyType();
        String balanceLine;
        if (currency == ConfigManager.CurrencyType.VAULT && vaultHook != null && vaultHook.isEnabled()) {
            balanceLine = messageManager.getMessage("gui.balance_vault_format", "&eBalance: &f%balance%")
                    .replace("%balance%", vaultHook.format(vaultHook.getBalance(player)));
        } else if (currency == ConfigManager.CurrencyType.TOKENS && playerData != null) {
            balanceLine = messageManager.getMessage("gui.balance_tokens_format", "&eBalance: &f%balance% Tokens")
                    .replace("%balance%", NumberFormat.getNumberInstance(Locale.US).format(playerData.getTokens()));
        } else {
            if (currency == ConfigManager.CurrencyType.VAULT && (vaultHook == null || !vaultHook.isEnabled())) {
                logger.fine("Vault balance unavailable for info item (Vault disabled/null). Player: " + player.getName());
            } else if (currency == ConfigManager.CurrencyType.TOKENS && playerData == null) {
                logger.warning("Token balance unavailable for info item (PlayerData is null). Player: " + player.getName());
            }
            balanceLine = messageManager.getMessage("gui.balance_unavailable", "&eBalance: &cUnavailable");
        }
        currentLore.add(ChatUtil.color(balanceLine));

        meta.setLore(currentLore);

        if (!infoPickaxeClone.setItemMeta(meta)) {
            logger.warning("Failed to set ItemMeta for info item clone (Player: " + player.getName() + ")");
        }
        inventory.setItem(getInfoItemSlot(), infoPickaxeClone);
    }

    /**
     * Handles player clicks within the Enchant GUI.
     * Processes upgrade attempts, checks costs, applies changes, and provides feedback.
     *
     * @param player        The player who clicked.
     * @param slot          The raw slot index clicked.
     * @param clickedItem   The ItemStack in the clicked slot (can be null).
     * @param pickaxeContext The specific ItemStack instance of the pickaxe this GUI corresponds to.
     * @param clickType     The type of click performed.
     */
    public void handleClick(Player player, int slot, @Nullable ItemStack clickedItem, @Nullable ItemStack pickaxeContext, ClickType clickType) {
        // --- ADDED: Handles menu clicks first ---
        if (handleMenuButtonClick(player, slot)) {
            return;
        }
        // --- END ADDED ---

        // --- YOUR ORIGINAL PURCHASE LOGIC (RESTORED) ---
        final boolean debug = configManager.isDebugMode();
        if (pickaxeContext == null || pickaxeContext.getType() == Material.AIR || !PDCUtil.isEnchantCorePickaxe(pickaxeContext)) {
            ChatUtil.sendMessage(player, "&cError: Pickaxe context lost or invalid. Please reopen the menu.");
            player.closeInventory();
            logger.warning("handleClick called for " + player.getName() + " but pickaxeContext was null, AIR, or not an EC Pickaxe.");
            return;
        }
        String translatedFillerName = ColorUtils.translateColors(enchantManager.getGuiFillerNameFormat());
        if (clickedItem == null || clickedItem.getType() == Material.AIR ||
                (enchantManager.isGuiFillEmptySlots() &&
                        clickedItem.getType() == enchantManager.getGuiFillerMaterial() &&
                        clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName() &&
                        Objects.equals(clickedItem.getItemMeta().getDisplayName(), translatedFillerName))) {
            return;
        }
        if (slot == getInfoItemSlot()) {
            return;
        }
        EnchantmentWrapper clickedEnchant = null;
        for (EnchantmentWrapper enchant : enchantRegistry.getAllEnchants()) {
            if (enchant.getInGuiSlot() == slot) {
                clickedEnchant = enchant;
                break;
            }
        }
        if (clickedEnchant == null || !clickedEnchant.isEnabled()) {
            return;
        }
        final String enchantKey = clickedEnchant.getRawName();
        int levelsToAdd;
        switch (clickType) {
            case LEFT: levelsToAdd = 1; break;
            case RIGHT: levelsToAdd = 10; break;
            case SHIFT_RIGHT: levelsToAdd = 50; break;
            default: return;
        }
        int currentLevel = pickaxeManager.getEnchantLevel(pickaxeContext, enchantKey);
        int maxEnchantLevel = clickedEnchant.getMaxLevel();
        if (maxEnchantLevel > 0 && currentLevel >= maxEnchantLevel) {
            ChatUtil.sendMessage(player, messageManager.getMessage("gui.max_level_reached", "&cThis enchant is already max level!"));
            playSoundEffect(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        int potentialTargetLevel = currentLevel + levelsToAdd;
        int actualTargetLevel = (maxEnchantLevel > 0) ? Math.min(potentialTargetLevel, maxEnchantLevel) : potentialTargetLevel;
        int actualLevelsBeingAdded = actualTargetLevel - currentLevel;
        if (actualLevelsBeingAdded <= 0) {
            ChatUtil.sendMessage(player, messageManager.getMessage("gui.max_level_reached", "&cThis enchant is already max level!"));
            playSoundEffect(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        int requiredPickLevel = clickedEnchant.getPickaxeLevelRequired();
        int currentPickLevel = PDCUtil.getPickaxeLevel(pickaxeContext);
        if (requiredPickLevel > 0 && currentPickLevel < requiredPickLevel) {
            ChatUtil.sendMessage(player, messageManager.getMessage("gui.pickaxe_level_required", "&cRequires Pickaxe Level %level%!").replace("%level%", String.valueOf(requiredPickLevel)));
            playSoundEffect(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        double totalCost = 0.0;
        final int MAX_LEVEL_CALC_ITERATIONS = 2000;
        int iterations = 0;
        for (int i = 1; i <= actualLevelsBeingAdded; i++) {
            if (iterations++ > MAX_LEVEL_CALC_ITERATIONS) {
                logger.severe("Cost calculation loop exceeded max iterations (" + MAX_LEVEL_CALC_ITERATIONS + ") for " + enchantKey + ". Aborting upgrade for player " + player.getName());
                ChatUtil.sendMessage(player, "&cError calculating cost for high level jump. Please try smaller increments or contact admin.");
                return;
            }
            int levelToCalc = currentLevel + i;
            double costForThisLevel = clickedEnchant.getCostForLevel(levelToCalc);
            if (costForThisLevel < 0) {
                ChatUtil.sendMessage(player, messageManager.getMessage("gui.cost_calc_error", "&cCannot calculate upgrade cost for level " + levelToCalc + "."));
                playSoundEffect(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            if (costForThisLevel > 0 && totalCost > Double.MAX_VALUE - costForThisLevel) {
                totalCost = Double.MAX_VALUE;
                break;
            }
            totalCost += costForThisLevel;
        }
        ConfigManager.CurrencyType currency = configManager.getCurrencyType();
        boolean canAfford = false;
        boolean transactionSuccess = false;
        String formattedCostString = "";
        if (totalCost <= 0) {
            canAfford = true;
            transactionSuccess = true;
            formattedCostString = (currency == ConfigManager.CurrencyType.VAULT && vaultHook != null && vaultHook.isEnabled()) ? vaultHook.format(0) : "0";
        } else if (currency == ConfigManager.CurrencyType.TOKENS) {
            formattedCostString = NumberFormat.getNumberInstance(Locale.US).format((long)Math.ceil(totalCost));
            if (playerData != null && playerData.hasEnoughTokens(totalCost)) {
                canAfford = true;
                if (playerData.removeTokens(totalCost)) {
                    transactionSuccess = true;
                    plugin.getPlayerDataManager().savePlayerData(playerData, true);
                }
            }
        } else { // VAULT
            if (vaultHook != null && vaultHook.isEnabled()) {
                formattedCostString = vaultHook.format(totalCost);
                if (vaultHook.hasEnough(player, totalCost)) {
                    canAfford = true;
                    transactionSuccess = vaultHook.withdraw(player, totalCost);
                }
            } else {
                ChatUtil.sendMessage(player, messageManager.getMessage("gui.vault_unavailable", "&cVault economy not available."));
                playSoundEffect(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }
        if (!canAfford && totalCost > 0) {
            String msgFormat = (currency == ConfigManager.CurrencyType.TOKENS) ? messageManager.getMessage("gui.not_enough_tokens", "&cNot enough Tokens! Cost: &e%cost% Tokens") : messageManager.getMessage("gui.not_enough_money", "&cNot enough Money! Cost: &e%cost%");
            ChatUtil.sendMessage(player, msgFormat.replace("%cost%", formattedCostString));
            playSoundEffect(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (!transactionSuccess && totalCost > 0) {
            ChatUtil.sendMessage(player, messageManager.getMessage("gui.transaction_error", "&cUpgrade failed! Transaction error."));
            playSoundEffect(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (transactionSuccess) {
            if (!pickaxeManager.setEnchantLevel(pickaxeContext, enchantKey, actualTargetLevel)) {
                logger.severe("Failed to set PDC enchant level for " + enchantKey + " on " + player.getName() + "'s pickaxe after successful transaction!");
                ChatUtil.sendMessage(player, "&cCritical error applying upgrade! Please contact an admin.");
                return;
            }
            pickaxeManager.updatePickaxe(pickaxeContext, player);
            int updatedPickLevel = PDCUtil.getPickaxeLevel(pickaxeContext);
            ItemStack updatedGuiItem = clickedEnchant.createGuiItem(actualTargetLevel, updatedPickLevel, currency, vaultHook);
            inventory.setItem(slot, updatedGuiItem);
            addInfoItem();
            String msgFormatKey = (actualLevelsBeingAdded == 1) ? (currency == ConfigManager.CurrencyType.TOKENS ? "gui.upgrade_success_tokens" : "gui.upgrade_success_vault") : (currency == ConfigManager.CurrencyType.TOKENS ? "gui.upgrade_multiple_success_tokens" : "gui.upgrade_multiple_success_vault");
            String defaultMsg = "&aUpgraded %enchant%!";
            ChatUtil.sendMessage(player, messageManager.getMessage(msgFormatKey, defaultMsg).replace("%enchant%", clickedEnchant.getDisplayName()).replace("%levels_added%", String.valueOf(actualLevelsBeingAdded)).replace("%level%", String.valueOf(actualTargetLevel)).replace("%cost%", formattedCostString));
            playSoundEffect(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }

    // --- CORRECTED SECTION START ---
    /**
     * Handles menu button clicks and returns true if a menu button was clicked.
     */
    private boolean handleMenuButtonClick(Player player, int slot) {
        // Clicks the Gems Menu Button
        for (int gemsSlot : GEMS_MENU_SLOTS) {
            if (slot == gemsSlot) {
                playSoundEffect(player, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new GemsGUI(plugin, player, playerData, this.pickaxe).open();
                return true;
            }
        }
        // Clicks the Rebirth Menu Button
        for (int rebirthSlot : REBIRTH_MENU_SLOTS) {
            if (slot == rebirthSlot) {
                playSoundEffect(player, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new RebirthGUI(plugin, player, playerData, this.pickaxe).open();
                return true;
            }
        }

        // Clicks any other menu button (Tokens, Skins, etc.)
        if (isMenuButtonSlot(slot)) {
            playSoundEffect(player, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            if (slot == 45) { // Pickaxe Skins button
                try {
                    new PickaxeSkinsGUI(plugin, player, playerData, this.pickaxe).open();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error opening PickaxeSkinsGUI for " + player.getName(), e);
                    ChatUtil.sendMessage(player, "&cError opening pickaxe skins menu.");
                    player.closeInventory();
                }
            } else if (slot == 47) { // Mortar button
                try {
                    new MortarGUI(plugin, player).open();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error opening MortarGUI for " + player.getName(), e);
                    ChatUtil.sendMessage(player, "&cError opening mortar menu.");
                    player.closeInventory();
                }
            } else if (slot == 51) { // Crystals button
                try {
                    new CrystalsGUI(plugin, player).open();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error opening CrystalsGUI for " + player.getName(), e);
                    ChatUtil.sendMessage(player, "&cError opening crystals menu.");
                    player.closeInventory();
                }
            } else if (slot == 53) { // Attachments button
                try {
                    new AttachmentsGUI(plugin, player).open();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error opening AttachmentsGUI for " + player.getName(), e);
                    ChatUtil.sendMessage(player, "&cError opening attachments menu.");
                    player.closeInventory();
                }
            } else {
                // All other menu buttons send the under development message
                ChatUtil.sendMessage(player, "&c&lUNDER DEVELOPMENT");
            }
            return true;
        }
        return false;
    }
    // --- CORRECTED SECTION END ---

    /** Opens the GUI inventory for the player. */
    public void open() {
        if (this.inventory == null) {
            logger.severe("Attempted to open EnchantGUI for " + player.getName() + " but inventory was null (creation failed).");
            ChatUtil.sendMessage(player, "&cCould not open enchant menu due to an internal error.");
            return;
        }
        player.openInventory(inventory);
    }

    /** Returns the Inventory associated with this GUI instance. */
    @NotNull
    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            logger.severe("getInventory() called on EnchantGUI but inventory is null!");
            return Bukkit.createInventory(this, 9, ChatColor.RED + "Error"); // Fallback dummy
        }
        return inventory;
    }

    /**
     * Helper method to create simple GUI items like fillers and close buttons.
     *
     * @param m Material
     * @param name Display name (colors translated)
     * @param lore Lore lines (colors translated)
     * @param model Custom model data (0 if none)
     * @param enchantedGlow Add fake glow effect?
     * @return The created ItemStack.
     */
    private ItemStack createGuiItemHelper(Material m, String name, @Nullable List<String> lore, int model, boolean enchantedGlow) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        // *** CHECK IF META IS NULL FIRST ***
        if (meta != null) {
            meta.setDisplayName(ColorUtils.translateColors(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().filter(Objects::nonNull).map(ColorUtils::translateColors).collect(Collectors.toList()));
            } else {
                meta.setLore(null);
            }
            if (model > 0) meta.setCustomModelData(model);

            // *** ADD FLAGS *INSIDE* THE NULL CHECK ***
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_UNBREAKABLE);

            if (enchantedGlow) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); // Hide the dummy enchant
            } else {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); // Also hide if not glowing
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Helper method to play a sound for the player, with error logging.
     */
    private void playSoundEffect(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline() && sound != null) {
            try {
                player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
            } catch (Exception e) {
                if(configManager != null && configManager.isDebugMode()) logger.log(Level.WARNING, "Error playing sound " + sound.name() + " for " + player.getName(), e);
            }
        }
    }

    public ItemStack getPickaxe() {
        return this.pickaxe;
    }

} // End of EnchantGUI class