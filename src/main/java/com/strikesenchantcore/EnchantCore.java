package com.strikesenchantcore;

// --- Existing Imports ---
import com.strikesenchantcore.commands.*;
import com.strikesenchantcore.config.*;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.enchants.EnchantRegistry;
import com.strikesenchantcore.gui.EnchantGUIListener;
import com.strikesenchantcore.listeners.*;
import com.strikesenchantcore.pickaxe.PickaxeManager;
import com.strikesenchantcore.tasks.PassiveEffectTask;
import com.strikesenchantcore.util.VaultHook;
import com.strikesenchantcore.util.PapiHook;
import com.strikesenchantcore.util.WorldGuardHook;

// +++ Import for local StrikesLicenseManager +++
import com.strikesenchantcore.util.StrikesLicenseManager;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bstats.bukkit.Metrics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets; // For encoding
import java.util.logging.Level;
import java.util.logging.Logger;

// Note: Removed unused imports like HashMap, HashSet, Map, UUID if not directly used in this specific file's logic shown.
// If your other methods (not shown but implied to exist) use them, ensure they are present.

public final class EnchantCore extends JavaPlugin {

    final String GREEN = "\u001B[32m";
    final String RESET = "\u001B[0m";
    private static EnchantCore instance;
    // Managers and other fields
    private ConfigManager configManager;
    private EnchantManager enchantManager;
    private PickaxeConfig pickaxeConfig;
    private PlayerDataManager playerDataManager;
    private PickaxeManager pickaxeManager; // Duplicate declaration from your original, ensure this is intended or remove one
    private EnchantRegistry enchantRegistry;
    private AutoSellConfig autoSellConfig;
    private MessageManager messageManager;
    private VaultHook vaultHook;
    private PapiHook papiHook;
    private WorldGuardHook worldGuardHook;
    private BukkitTask passiveEffectTask;
    private BlockBreakListener blockBreakListener;
    private static final int BSTATS_PLUGIN_ID = 22479;

    // +++ License Configuration (using local StrikesLicenseManager) +++
    // Obfuscated LICENSE_API_USER_ID "a1f66"
    // Original: "a1f66"
    // Key: 7
    // 'a' (97) + 7 = 104 ('h')
    // '1' (49) + 7 = 56  ('8')
    // 'f' (102) + 7 = 109 ('m')
    // '6' (54) + 7 = 61  ('=')
    // '6' (54) + 7 = 61  ('=')
    private static final byte[] IllIIIlIlIIllIIllIlIlllIIllIlllIllIllIIlllIIlIIllIIlllIlIIlllIlllIlIIIlllIIllIIIllIIllIlllIlllIlIIlllIlIllIIlIllIIllIIllIIIllIllIllIIlIlIllIIlllIIIllIIIlIIIlIllIIIlIIIlllIIlIIllIIIllIIIllIIlIIllIIIllIIlIllIIllIlllIllIIlIIIl = {
            (byte) ('a' + 7), (byte) ('1' + 7), (byte) ('f' + 7),
            (byte) ('6' + 7), (byte) ('6' + 7)
    };
    private static final int IIllIIIllIllIIlllIlllIIIllIlIIIllIIlIIllIIllIlllIIIllIllIllIIIlIlIllIIlIIlIIllIIllIlIIllIIIllIlIlIIIlIIlIllIllIlIlllIllIIIllIIllIIllIIlllIIllIIlllIllIIlllIlIIlllIIlIIIlllIIlllIIIlllIlllIlllIIlIlIIlIllIIIlllIlIlIllIIIlIIll = 7;

    private static String getObfuscatedLicenseApiUserId() {
        byte[] originalBytes = new byte[IllIIIlIlIIllIIllIlIlllIIllIlllIllIllIIlllIIlIIllIIlllIlIIlllIlllIlIIIlllIIllIIIllIIllIlllIlllIlIIlllIlIllIIlIllIIllIIllIIIllIllIllIIlIlIllIIlllIIIllIIIlIIIlIllIIIlIIIlllIIlIIllIIIllIIIllIIlIIllIIIllIIlIllIIllIlllIllIIlIIIl.length];
        for (int i = 0; i < IllIIIlIlIIllIIllIlIlllIIllIlllIllIllIIlllIIlIIllIIlllIlIIlllIlllIlIIIlllIIllIIIllIIllIlllIlllIlIIlllIlIllIIlIllIIllIIllIIIllIllIllIIlIlIllIIlllIIIllIIIlIIIlIllIIIlIIIlllIIlIIllIIIllIIIllIIlIIllIIIllIIlIllIIllIlllIllIIlIIIl.length; i++) {
            originalBytes[i] = (byte) (IllIIIlIlIIllIIllIlIlllIIllIlllIllIllIIlllIIlIIllIIlllIlIIlllIlllIlIIIlllIIllIIIllIIllIlllIlllIlIIlllIlIllIIlIllIIllIIllIIIllIllIllIIlIlIllIIlllIIIllIIIlIIIlIllIIIlIIIlllIIlIIllIIIllIIIllIIlIIllIIIllIIlIllIIllIlllIllIIlIIIl[i] - IIllIIIllIllIIlllIlllIIIllIlIIIllIIlIIllIIllIlllIIIllIllIllIIIlIlIllIIlIIlIIllIIllIlIIllIIIllIlIlIIIlIIlIllIllIlIlllIllIIIllIIllIIllIIlllIIllIIlllIllIIlllIlIIlllIIlIIIlllIIlllIIIlllIlllIlllIIlIlIIlIllIIIlllIlIlIllIIIlIIll);
        }
        return new String(originalBytes, StandardCharsets.UTF_8);
    }

    private static final String IIllIIllIIllIlIIIlIllIlIIIlIIlIIlIIlllIlIIlIIIllIIlllIIllIIlIlllIIllIIIlIIIlIIlllIIIlIIllIIlIIIlllIllIllIIlllIllIllIIllIIlIIlIlIIIlIIIllIIllIIllIIIlIIlIIlIlllIllIIlIIlIlllIIllIlIIllIIllIIIllIllIIllIIllIllIIlllIllIIllIl = "%%__BUILTBYBIT__%%";
    private static final String IIllIlIllIIIllIIlllIIIllIlIIllIIIlIIIllIlllIlIlllIllIIIllIllIllIIIlllIIllIIlIIIlllIIllIIIllIIllIllIIIlllIIlllIIllIIIlllIlIllIIIlIIlllIlIIIlllIIllIIllIIIlllIIllIIIllIIIllIIIllIlIlllIIllIIIllIIlllIIllIIlIIlIIllIllIIllIlllIllIlIllIIIlllIllIll = "%%__USER__%%";
    private static final String IIllIIllIIllIllIIIllIIlllIIIllIllIIllIIIllIlllIlllIIllIIlIIIlIlIllIIlIIllIIlIIIlIllIlllIIllIllIIllIlIlIllIIllIIIllIIllIIIlllIlIIllIIlIllIIlIIlIIIlIIlIIlllIIlllIIllIIlIIllIIllIIlllIIlllIIlIlIIIlllIIIlllIIIlllIIlIIllIlIIlllIlllIll = "%%__STEAM64__%%";
    private static final String IllIllIlllIIlIIlllIllIIlIIlllIIlIIlllIIllIIlIlIIllIIIllIIllIIllIIIlIIlIlIllIIIllIlllIllIIIlIIIllIIIllIIlIIlIIlIIllIIllIllIIlIIIllIIlIllIIlIIlIIllIlIIIllIllIIIllIIllIIIllIllIIlllIIllIIllIllIIlllIIllIIlllIlIIlIIIllIIlIIlIIl = "%%__STEAM32__%%";
    private static final String IIlllIIlIllIIIllIllIIIlIlIllIllIIlIIlIllIIllIlIIlllIIIllIIIlIIlIIIllIIIlIlllIllIIlllIIllIIIlllIIllIIlIIlIIlIIIlIIIllIIlIlIIIllIIIllIIllIIIlllIIIllIIIllIIlIIlIlIllIllIlIIlllIlllIIIllIIlIIIlIllIlIllIlIllIIllIllIllIIlIl = "%%__TIMESTAMP__%%";
    @Override
    public void onLoad() {
        instance = this; // Set instance early
        // WorldGuard flag registration
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            getLogger().info("WorldGuard found, attempting to register flags...");
            try {
                // Ensure WorldGuardHook is in the correct package if not in default com.strikesenchantcore.util
                Class.forName("com.strikesenchantcore.util.WorldGuardHook");
                WorldGuardHook.registerFlag();
            } catch (ClassNotFoundException e) {
                getLogger().log(Level.WARNING, "WorldGuardHook class not found during onLoad. Flag registration deferred or failed.");
            } catch (LinkageError | Exception e) { // Catch LinkageError as well for safety
                getLogger().log(Level.WARNING, "Could not register WorldGuard flag during onLoad: " + e.getMessage(), e);
            }
        } else {
            getLogger().info("WorldGuard not found during onLoad, flag registration skipped.");
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        final Logger log = getLogger();
        log.info("=== Enabling EnchantCore v" + getDescription().getVersion() + " ===");

        saveDefaultConfig();
        org.bukkit.configuration.file.FileConfiguration bukkitConfig = getConfig();

        // +++ License Validation Block (using local StrikesLicenseManager) +++
        log.info("Validating license...");
        String licenseKeyFromConfig = bukkitConfig.getString("license", "YOUR_LICENSE_KEY_HERE"); // Key path from config

        if ("YOUR_LICENSE_KEY_HERE".equals(licenseKeyFromConfig) || licenseKeyFromConfig.trim().isEmpty()) {
            log.severe("============================================================");
            log.severe("                     ⚠️  LICENSE ERROR ⚠️                  ");
            log.severe("------------------------------------------------------------");
            log.severe(" EnchantCore could not start due to a missing or default key.");
            log.severe("");
            log.severe(" ➤ Please open your config.yml file.");
            log.severe(" ➤ Set a valid license key under the 'license:' field.");
            log.severe("");
            log.severe(" Plugin will now be disabled to prevent unauthorized use.");
            log.severe("");
            log.severe(" Need help? Join our support Discord:");
            log.severe(" ➥ https://discord.gg/ym4bZDsnC3");
            log.severe("============================================================");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            StrikesLicenseManager licenseManager = new StrikesLicenseManager(getObfuscatedLicenseApiUserId());

            log.info("Verifying your license key...");
            StrikesLicenseManager.ValidationType validationStatus = licenseManager.verify(licenseKeyFromConfig);

            if (validationStatus == StrikesLicenseManager.ValidationType.VALID) {
                log.info(GREEN + "==============================================================");
                log.info(GREEN + "                    ✅  LICENSE VALIDATED ✅                 ");
                log.info(GREEN + "--------------------------------------------------------------");
                log.info(GREEN + "                  EnchantCore license validated");
                log.info(GREEN + "                 Thank you for using our plugin!");
                log.info(GREEN + "                  Join our Discord for support:");
                log.info(GREEN + "                  https://discord.gg/ym4bZDsnC3");
                log.info(GREEN + "==============================================================" + RESET);
            }
            else {
                log.severe("============================================================");
                log.severe("                ❌  LICENSE VALIDATION FAILED ❌           ");
                log.severe("------------------------------------------------------------");

                String failureReason;
                if (validationStatus == StrikesLicenseManager.ValidationType.EXPIRED) {
                    failureReason = " ➤ Reason: Your license is EXPIRED. Please renew it.";
                } else {
                    failureReason = " ➤ Reason: " + validationStatus.name();
                }

                log.severe(failureReason);
                log.severe(" ➤ Key Snippet: " + licenseKeyFromConfig.substring(0, Math.min(licenseKeyFromConfig.length(), 4)) + "****");
                log.severe("");
                log.severe(" ➤ Please verify your license key in config.yml.");
                log.severe(" ➤ Contact support if the problem persists.");
                log.severe("");
                log.severe(" Discord Support: https://discord.gg/ym4bZDsnC3");
                log.severe("");
                log.severe(" EnchantCore will now be disabled to prevent misuse.");
                log.severe("============================================================");

                Bukkit.getScheduler().cancelTasks(this);
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }

        } catch (Exception e) {
            log.severe("============================================================");
            log.severe("             💥  LICENSE VERIFICATION ERROR  💥             ");
            log.severe("------------------------------------------------------------");
            log.severe(" A critical error occurred during license validation.");
            log.severe("");
            log.severe(" ➤ Possible causes:");
            log.severe("    - Invalid API response");
            log.severe("    - Network connection issue");
            log.severe("    - Internal server error");
            log.severe("");
            log.severe(" ➤ Please open a ticket in our Discord for assistance:");
            log.severe("    https://discord.gg/ym4bZDsnC3");
            log.severe("");
            log.log(Level.SEVERE, " ➤ Underlying error details:", e);
            log.severe("");
            log.severe(" EnchantCore will now be disabled.");
            log.severe("============================================================");

            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // +++ END OF LICENSE VALIDATION BLOCK +++

        log.info("Initializing configuration managers...");
        this.messageManager = new MessageManager(this);
        this.enchantManager = new EnchantManager(this);
        this.pickaxeConfig = new PickaxeConfig(this);
        this.autoSellConfig = new AutoSellConfig(this);
        this.configManager = new ConfigManager(this);

        if (this.configManager != null) {
            log.info("Loading all configurations through ConfigManager...");
            this.configManager.loadConfigs(); // This should load individual configs like messages.yml etc.
        } else {
            log.severe("Core ConfigManager failed to initialize. Disabling EnchantCore.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Check if specific managers (that are loaded by ConfigManager or have their own configs) are initialized
        if (this.pickaxeConfig == null || this.enchantManager == null || this.messageManager == null || this.autoSellConfig == null) {
            log.severe("!!! Critical: One or more specific config managers are null AFTER ConfigManager.loadConfigs(). Check constructors or ConfigManager logic. Disabling EnchantCore. !!!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        log.info("Setting up hooks...");
        this.vaultHook = new VaultHook(this);
        if (!vaultHook.setupEconomy()) {
            log.warning("Vault hook failed or no economy provider found. Vault-dependent features disabled.");
        }
        this.papiHook = new PapiHook(this);
        if (papiHook.setupPlaceholderAPI()) {
            if (papiHook.registerPlaceholders()) {
                log.info("PlaceholderAPI hooked and placeholders registered.");
            } else {
                log.warning("PlaceholderAPI hooked but failed to register placeholders.");
            }
        } else {
            log.info("PlaceholderAPI not found, placeholders disabled.");
        }

        // Initialize WorldGuardHook instance if not already done by onLoad (e.g. if onLoad failed or plugin was reloaded)
        if (this.worldGuardHook == null && Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            this.worldGuardHook = new WorldGuardHook(this); // Assuming constructor takes EnchantCore instance
            this.worldGuardHook.initializeWorldGuardInstance(); // Ensure this method exists and works
        }
        // Check if WorldGuardHook is enabled (flag registered and hook working)
        if (this.worldGuardHook == null || !this.worldGuardHook.isEnabled()) {
            log.warning("WorldGuard hook failed or flag registration issue. WorldGuard region features disabled.");
        }
        log.info("Hooks setup complete.");

        log.info("Initializing core managers...");
        this.playerDataManager = new PlayerDataManager(this);
        this.pickaxeManager = new PickaxeManager(this); // Assuming this initializes one of the pickaxeManager fields
        this.enchantRegistry = new EnchantRegistry(this);
        if (playerDataManager == null || this.pickaxeManager == null || enchantRegistry == null) { // Check this.pickaxeManager
            log.severe("!!! Critical logic manager (PlayerData, Pickaxe, or EnchantRegistry) failed to initialize. Disabling EnchantCore. !!!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.enchantRegistry.loadEnchantsFromConfig();
        log.info("Core managers initialized.");

        log.info("Registering listeners and commands...");
        registerListeners();
        registerCommands();
        log.info("Listeners and commands registered.");

        initializeMetrics();
        startRepeatingTasks();

        if (playerDataManager != null) {
            playerDataManager.loadOnlinePlayers();
        } else {
            log.severe("PlayerDataManager was null before loadOnlinePlayers could be called!");
        }

        log.info("=== EnchantCore Enabled Successfully ===");
    }

    @Override
    public void onDisable() {
        getLogger().info("=== Disabling EnchantCore v" + getDescription().getVersion() + " ===");
        getLogger().info("Cancelling tasks...");
        if (passiveEffectTask != null && !passiveEffectTask.isCancelled()) {
            try { passiveEffectTask.cancel(); } catch (Exception e) { getLogger().warning("Error cancelling PassiveEffectTask: " + e.getMessage()); }
        }
        // Cancel all tasks registered by this plugin.
        try { Bukkit.getScheduler().cancelTasks(this); } catch (Exception e) { getLogger().warning("Error cancelling general plugin tasks: " + e.getMessage()); }
        getLogger().info("Tasks cancelled.");

        getLogger().info("Performing Nuke cleanup...");
        if (blockBreakListener != null) { // Check if listener was initialized
            try {
                blockBreakListener.cleanupOnDisable();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error during BlockBreakListener cleanup", e);
            }
        } else {
            getLogger().info("BlockBreakListener was null during onDisable, Nuke cleanup skipped (likely due to early disable).");
        }
        getLogger().info("Nuke cleanup finished.");

        getLogger().info("Saving player data...");
        if (playerDataManager != null) { // Check if manager was initialized
            try {
                playerDataManager.stopAutoSaveTask(); // If you have an auto-save task
                playerDataManager.saveAllPlayerData(true); // true for synchronous save on disable
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving player data during disable", e);
            }
        } else {
            getLogger().info("PlayerDataManager was null during onDisable, player data saving skipped (likely due to early disable).");
        }
        getLogger().info("Player data saving process finished.");

        getLogger().info("Cleaning up resources...");
        this.configManager = null;
        this.messageManager = null;
        this.enchantManager = null;
        this.pickaxeConfig = null;
        this.playerDataManager = null;
        this.pickaxeManager = null; // Nullify the used PickaxeManager instance
        this.enchantRegistry = null;
        this.autoSellConfig = null;
        this.vaultHook = null;
        this.papiHook = null;
        this.worldGuardHook = null;
        this.passiveEffectTask = null;
        this.blockBreakListener = null;
        // No specific license object to nullify as StrikesLicenseManager was instantiated locally in onEnable
        instance = null;
        getLogger().info("Cleanup complete.");
        getLogger().info("=== EnchantCore Disabled ===");
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();

        // Ensure dependencies for BlockBreakListener are available
        if (worldGuardHook == null) {
            getLogger().warning("WorldGuardHook is null during listener registration attempt. BlockBreakListener might not function correctly with WorldGuard.");
        }
        if (autoSellConfig == null) {
            getLogger().warning("AutoSellConfig is null during listener registration attempt. AutoSell features in BlockBreakListener might not work.");
        }
        if (configManager == null) { // Assuming ConfigManager might be needed by BBL or its features
            getLogger().warning("ConfigManager is null during listener registration attempt.");
        }


        // Initialize blockBreakListener here if it hasn't been, ensuring its dependencies are ready
        // The constructor used here is based on your initial code.
        if (this.blockBreakListener == null) {
            if (worldGuardHook != null && autoSellConfig != null && this != null && configManager != null) { // Added configManager check
                this.blockBreakListener = new BlockBreakListener(this, worldGuardHook, autoSellConfig);
            } else {
                getLogger().severe("Cannot initialize BlockBreakListener due to missing dependencies (WorldGuardHook, AutoSellConfig, or ConfigManager).");
            }
        }

        if (this.blockBreakListener != null) {
            pm.registerEvents(this.blockBreakListener, this);
        } else {
            // This log might be redundant if the one above was already triggered, but good for clarity.
            getLogger().severe("BlockBreakListener could not be registered as it (or its dependencies) were not initialized!");
        }

        // Register other listeners
        pm.registerEvents(new PlayerInteractListener(this), this);
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new EnchantGUIListener(this), this);
        pm.registerEvents(new ProtectionListeners(this), this);
        pm.registerEvents(new PlayerQuitListener(this), this);
    }

    private void registerCommands() {
        PluginCommand enchantCoreCmd = getCommand("enchantcore");
        if (enchantCoreCmd != null) {
            EnchantCoreCommand enchantCoreExecutor = new EnchantCoreCommand(this);
            enchantCoreCmd.setExecutor(enchantCoreExecutor);
            enchantCoreCmd.setTabCompleter(new EnchantCoreTabCompleter(this)); // Assuming separate tab completer
        } else { getLogger().log(Level.SEVERE, "Command 'enchantcore' not found in plugin.yml!"); }

        PluginCommand sellAllCmd = getCommand("sellall");
        if (sellAllCmd != null) { sellAllCmd.setExecutor(new SellAllCommand(this)); }
        else { getLogger().log(Level.SEVERE, "Command 'sellall' not found in plugin.yml!"); }

        PluginCommand toggleMsgCmd = getCommand("ectoggle");
        if (toggleMsgCmd != null) { toggleMsgCmd.setExecutor(new ToggleMessagesCommand(this)); }
        else { getLogger().log(Level.SEVERE, "Command 'ectoggle' not found in plugin.yml!"); }

        PluginCommand toggleSndCmd = getCommand("togglesounds");
        if (toggleSndCmd != null) { toggleSndCmd.setExecutor(new ToggleSoundsCommand(this)); }
        else { getLogger().log(Level.SEVERE, "Command 'togglesounds' not found in plugin.yml!"); }

        PluginCommand tokensCmd = getCommand("tokens");
        if (tokensCmd != null) {
            TokensCommand tokensExecutor = new TokensCommand(this);
            tokensCmd.setExecutor(tokensExecutor);
            tokensCmd.setTabCompleter(tokensExecutor); // TokensCommand can implement TabCompleter
        } else { getLogger().log(Level.SEVERE, "Command 'tokens' not found in plugin.yml!"); }
    }

    private void initializeMetrics() {
        if (BSTATS_PLUGIN_ID <= 0) {
            getLogger().log(Level.INFO, "bStats Metrics is disabled (Plugin ID <= 0).");
            return;
        }
        try {
            new Metrics(this, BSTATS_PLUGIN_ID);
            getLogger().log(Level.INFO, "bStats Metrics initialized (ID: " + BSTATS_PLUGIN_ID + ").");
        }
        catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to initialize bStats Metrics: " + e.getMessage());
        }
    }

    private void startRepeatingTasks() {
        // Ensure pickaxeManager is initialized before starting tasks that might depend on it
        if (this.pickaxeManager == null) { // Check the instance field
            getLogger().severe("Cannot start PassiveEffectTask: PickaxeManager is null!");
            return;
        }
        long delay = 40L;  // Default delay: 2 seconds (40 ticks)
        long period = 20L; // Default period: 1 second (20 ticks)
        try {
            this.passiveEffectTask = new PassiveEffectTask(this).runTaskTimer(this, delay, period);
            getLogger().log(Level.INFO, "Passive Effect Task started (Interval: " + (period / 20.0) + "s).");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start PassiveEffectTask!", e);
        }
    }

    @NotNull
    public static EnchantCore getInstance() {
        if (instance == null) {
            // This state should ideally not be reached if the plugin is properly managed by Bukkit.
            // It might happen if called after onDisable or before onEnable fully completes.
            throw new IllegalStateException("EnchantCore instance is not available! Plugin might be disabled or not fully enabled yet.");
        }
        return instance;
    }

    // Getters for your managers and hooks
    @Nullable public ConfigManager getConfigManager() { return configManager; }
    @Nullable public EnchantManager getEnchantManager() { return enchantManager; }
    @Nullable public PickaxeConfig getPickaxeConfig() { return pickaxeConfig; }
    @Nullable public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    // This getter refers to the instance field `this.pickaxeManager`.
    // If you have two declarations, ensure this is the one you intend to expose.
    @Nullable public PickaxeManager getPickaxeManager() { return this.pickaxeManager; }
    @Nullable public EnchantRegistry getEnchantRegistry() { return enchantRegistry; }
    @Nullable public VaultHook getVaultHook() { return vaultHook; }
    @Nullable public PapiHook getPapiHook() { return (papiHook != null && papiHook.isHooked()) ? papiHook : null; }
    public boolean isPlaceholderAPIEnabled() { return papiHook != null && papiHook.isHooked(); }
    @Nullable public WorldGuardHook getWorldGuardHook() { return worldGuardHook; }
    @Nullable public AutoSellConfig getAutoSellConfig() { return autoSellConfig; }
    @Nullable public MessageManager getMessageManager() { return messageManager; }
}