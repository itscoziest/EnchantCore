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
import com.strikesenchantcore.util.ItemsAdderUtil;
import com.strikesenchantcore.gui.PickaxeSkinsGUIListener;
import com.strikesenchantcore.gui.PickaxeSkinsGUI;
import com.strikesenchantcore.managers.BlackholeManager;
import com.strikesenchantcore.commands.CrystalsCommand;
import com.strikesenchantcore.managers.CrystalManager;
import com.strikesenchantcore.managers.MortarManager;
import com.strikesenchantcore.gui.MortarGUIListener;
import com.strikesenchantcore.commands.MortarCommand;
// --- ADDED IMPORTS ---
import com.strikesenchantcore.managers.AttachmentManager;
import com.strikesenchantcore.gui.AttachmentsGUIListener;
import com.strikesenchantcore.commands.AttachmentsCommand;
import com.strikesenchantcore.listeners.AttachmentBoxListener;

// +++ Import for local StrikesLicenseManager (COMMENTED OUT FOR DEV) +++
// import com.strikesenchantcore.util.StrikesLicenseManager;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bstats.bukkit.Metrics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.strikesenchantcore.gui.CrystalsGUIListener;

import java.nio.charset.StandardCharsets; // For encoding
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EnchantCore extends JavaPlugin {

    final String GREEN = "\u001B[32m";
    final String RESET = "\u001B[0m";
    private static EnchantCore instance;
    // Managers and other fields
    private ConfigManager configManager;
    private EnchantManager enchantManager;
    private PickaxeConfig pickaxeConfig;
    private PlayerDataManager playerDataManager;
    private PickaxeManager pickaxeManager;
    private ItemsAdderUtil itemsAdderUtil;
    private EnchantRegistry enchantRegistry;
    private AutoSellConfig autoSellConfig;
    private MessageManager messageManager;
    private VaultHook vaultHook;
    private PapiHook papiHook;
    private WorldGuardHook worldGuardHook;
    private BukkitTask passiveEffectTask;
    private BlockBreakListener blockBreakListener;
    private static final int BSTATS_PLUGIN_ID = 25927;
    private SkinConfig skinConfig;
    private BlackholeManager blackholeManager;
    private CrystalManager crystalManager;
    private CrystalsGUIListener crystalsGUIListener;
    private MortarManager mortarManager;
    private AttachmentManager attachmentManager; // ADDED FIELD

    // +++ License Configuration (TEMPORARILY DISABLED FOR DEVELOPMENT) +++
    // All license-related code is commented out below

    @Override
    public void onLoad() {
        instance = this; // Set instance early
        // WorldGuard flag registration
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            getLogger().info("WorldGuard found, attempting to register flags...");
            try {
                Class.forName("com.strikesenchantcore.util.WorldGuardHook");
                WorldGuardHook.registerFlag();
            } catch (ClassNotFoundException e) {
                getLogger().log(Level.WARNING, "WorldGuardHook class not found during onLoad. Flag registration deferred or failed.");
            } catch (LinkageError | Exception e) {
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
        log.info("=== Enabling EnchantCore v" + getDescription().getVersion() + " (DEV MODE - NO LICENSE) ===");

        saveDefaultConfig();

        // --- STEP 1: INITIALIZE ALL MANAGERS FIRST ---
        log.info("Initializing configuration managers...");
        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);
        this.enchantManager = new EnchantManager(this);
        this.pickaxeConfig = new PickaxeConfig(this);
        this.autoSellConfig = new AutoSellConfig(this);
        this.itemsAdderUtil = new ItemsAdderUtil(this);
        this.skinConfig = new SkinConfig(this);
        this.blackholeManager = new BlackholeManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.crystalManager = new CrystalManager(this);
        this.mortarManager = new MortarManager(this);
        this.attachmentManager = new AttachmentManager(this); // ADDED INITIALIZATION

        // Load all .yml files into the managers
        log.info("Loading all configurations...");
        this.configManager.loadConfigs();

        // Now initialize the core logic managers that depend on the configs
        log.info("Initializing core managers...");
        // --- FIXED: Removed redundant PlayerDataManager initialization ---
        this.pickaxeManager = new PickaxeManager(this);
        this.enchantRegistry = new EnchantRegistry(this);
        this.enchantRegistry.loadEnchantsFromConfig();

        // --- STEP 2: SETUP HOOKS ---
        log.info("Setting up hooks...");
        this.vaultHook = new VaultHook(this);
        vaultHook.setupEconomy();

        this.papiHook = new PapiHook(this);
        if (papiHook.setupPlaceholderAPI()) {
            papiHook.registerPlaceholders();
            log.info("PlaceholderAPI hooked and placeholders registered.");
        } else {
            log.info("PlaceholderAPI not found, placeholders disabled.");
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            this.worldGuardHook = new WorldGuardHook(this);
            this.worldGuardHook.initializeWorldGuardInstance();
        }

        // --- STEP 3: REGISTER COMMANDS AND LISTENERS ---
        // Now that all managers and hooks exist, we can safely register commands and listeners
        log.info("Registering listeners and commands...");
        registerListeners();
        registerCommands();
        log.info("Listeners and commands registered.");

        // --- STEP 4: START TASKS AND OTHER LOGIC ---
        initializeMetrics();
        startRepeatingTasks();

        playerDataManager.loadOnlinePlayers();

        log.info("=== EnchantCore Enabled Successfully (DEV MODE) ===");
    }

    @Override
    public void onDisable() {
        getLogger().info("=== Disabling EnchantCore v" + getDescription().getVersion() + " ===");

        // --- ADD THIS BLOCK FOR ARMOR STAND CLEANUP ---
        getLogger().info("Cleaning up stray armor stands...");
        if (blockBreakListener != null) {
            try {
                blockBreakListener.cleanupBlackholeStands();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error during Blackhole armor stand cleanup", e);
            }
        }
        getLogger().info("Cancelling tasks...");
        if (passiveEffectTask != null && !passiveEffectTask.isCancelled()) {
            try { passiveEffectTask.cancel(); } catch (Exception e) { getLogger().warning("Error cancelling PassiveEffectTask: " + e.getMessage()); }
        }
        try { Bukkit.getScheduler().cancelTasks(this); } catch (Exception e) { getLogger().warning("Error cancelling general plugin tasks: " + e.getMessage()); }
        getLogger().info("Tasks cancelled.");

        getLogger().info("Performing Nuke cleanup...");
        if (blockBreakListener != null) {
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

        if (mortarManager != null) {
            // MortarManager cleanup if needed
        }
        this.mortarManager = null;

        // ADDED ATTACHMENT MANAGER CLEANUP
        if (attachmentManager != null) {
            // AttachmentManager cleanup if needed
        }
        this.attachmentManager = null;

        if (blackholeManager != null) {
            blackholeManager.cleanupAllBlackholes();
        }

        if (playerDataManager != null) {
            try {
                playerDataManager.stopAutoSaveTask();
                playerDataManager.saveAllPlayerData(true);
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving player data during disable", e);
            }
        } else {
            getLogger().info("PlayerDataManager was null during onDisable, player data saving skipped (likely due to early disable).");
        }
        getLogger().info("Player data saving process finished.");

        getLogger().info("Cleaning up resources...");
        this.skinConfig = null;
        this.configManager = null;
        this.messageManager = null;
        this.enchantManager = null;
        this.pickaxeConfig = null;
        this.playerDataManager = null;
        this.pickaxeManager = null;
        this.enchantRegistry = null;
        this.autoSellConfig = null;
        this.vaultHook = null;
        this.papiHook = null;
        this.worldGuardHook = null;
        this.passiveEffectTask = null;
        this.blockBreakListener = null;
        this.blackholeManager = null;
        this.crystalManager = null;
        this.crystalsGUIListener = null;
        instance = null;
        getLogger().info("Cleanup complete.");
        getLogger().info("=== EnchantCore Disabled ===");
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();

        if (worldGuardHook == null) {
            getLogger().warning("WorldGuardHook is null during listener registration attempt. BlockBreakListener might not function correctly with WorldGuard.");
        }
        if (autoSellConfig == null) {
            getLogger().warning("AutoSellConfig is null during listener registration attempt. AutoSell features in BlockBreakListener might not work.");
        }
        if (configManager == null) {
            getLogger().warning("ConfigManager is null during listener registration attempt.");
        }

        if (this.blockBreakListener == null) {
            if (worldGuardHook != null && autoSellConfig != null && this != null && configManager != null) {
                this.blockBreakListener = new BlockBreakListener(this, worldGuardHook, autoSellConfig);
            } else {
                getLogger().severe("Cannot initialize BlockBreakListener due to missing dependencies (WorldGuardHook, AutoSellConfig, or ConfigManager).");
            }
        }

        if (this.blockBreakListener != null) {
            pm.registerEvents(this.blockBreakListener, this);
        } else {
            getLogger().severe("BlockBreakListener could not be registered as it (or its dependencies) were not initialized!");
        }

        pm.registerEvents(new PlayerInteractListener(this), this);
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new EnchantGUIListener(this), this);
        pm.registerEvents(new PickaxeSkinsGUIListener(this), this); // Add this line
        pm.registerEvents(new ProtectionListeners(this), this);
        pm.registerEvents(new PlayerQuitListener(this), this);
        pm.registerEvents(new PinataListener(this), this);
        pm.registerEvents(new OverchargeListener(this), this);
        crystalsGUIListener = new CrystalsGUIListener(this);
        pm.registerEvents(crystalsGUIListener, this);
        pm.registerEvents(new MortarGUIListener(this), this);
        // ADDED ATTACHMENT LISTENERS
        pm.registerEvents(new AttachmentsGUIListener(this), this);
        pm.registerEvents(new AttachmentBoxListener(this), this);
    }

    private void registerCommands() {
        getCommand("toggleanimations").setExecutor(new ToggleAnimationsCommand(this));
        PluginCommand enchantCoreCmd = getCommand("enchantcore");
        if (enchantCoreCmd != null) {
            EnchantCoreCommand enchantCoreExecutor = new EnchantCoreCommand(this);
            enchantCoreCmd.setExecutor(enchantCoreExecutor);
            enchantCoreCmd.setTabCompleter(new EnchantCoreTabCompleter(this));
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
            tokensCmd.setTabCompleter(tokensExecutor);
        } else { getLogger().log(Level.SEVERE, "Command 'tokens' not found in plugin.yml!"); }

        PluginCommand gemsCmd = getCommand("gems");
        if (gemsCmd != null) {
            GemsCommand gemsExecutor = new GemsCommand(this);
            gemsCmd.setExecutor(gemsExecutor);
            gemsCmd.setTabCompleter(gemsExecutor);
        } else { getLogger().log(Level.SEVERE, "Command 'gems' not found in plugin.yml!"); }

        PluginCommand mortarCmd = getCommand("mortar");
        if (mortarCmd != null) {
            MortarCommand mortarExecutor = new MortarCommand(this);
            mortarCmd.setExecutor(mortarExecutor);
            mortarCmd.setTabCompleter(mortarExecutor);
        } else {
            getLogger().log(Level.SEVERE, "Command 'mortar' not found in plugin.yml!");
        }

        // ADDED ATTACHMENTS COMMAND
        PluginCommand attachmentsCmd = getCommand("attachments");
        if (attachmentsCmd != null) {
            AttachmentsCommand attachmentsExecutor = new AttachmentsCommand(this);
            attachmentsCmd.setExecutor(attachmentsExecutor);
            attachmentsCmd.setTabCompleter(attachmentsExecutor);
        } else {
            getLogger().log(Level.SEVERE, "Command 'attachments' not found in plugin.yml!");
        }

        // --- ADDED: The new Points command is now registered here in the correct order ---
        PluginCommand pointsCmd = getCommand("points");
        if (pointsCmd != null) {
            PointsCommand pointsExecutor = new PointsCommand(this);
            pointsCmd.setExecutor(pointsExecutor);
            pointsCmd.setTabCompleter(pointsExecutor);
        } else { getLogger().log(Level.SEVERE, "Command 'points' not found in plugin.yml!"); }
        PluginCommand crystalsCmd = getCommand("crystals");
        if (crystalsCmd != null) {
            CrystalsCommand crystalsExecutor = new CrystalsCommand(this);
            crystalsCmd.setExecutor(crystalsExecutor);
            crystalsCmd.setTabCompleter(crystalsExecutor);
        } else { getLogger().log(Level.SEVERE, "Command 'crystals' not found in plugin.yml!"); }
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
        if (this.pickaxeManager == null) {
            getLogger().severe("Cannot start PassiveEffectTask: PickaxeManager is null!");
            return;
        }
        long delay = 40L;
        long period = 20L;
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
            throw new IllegalStateException("EnchantCore instance is not available! Plugin might be disabled or not fully enabled yet.");
        }
        return instance;
    }

    // Getters for your managers and hooks
    @Nullable public ConfigManager getConfigManager() { return configManager; }
    @Nullable public EnchantManager getEnchantManager() { return enchantManager; }
    @Nullable public PickaxeConfig getPickaxeConfig() { return pickaxeConfig; }
    public ItemsAdderUtil getItemsAdderUtil() {
        return itemsAdderUtil;
    }
    @Nullable public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    @Nullable public PickaxeManager getPickaxeManager() { return this.pickaxeManager; }
    @Nullable public EnchantRegistry getEnchantRegistry() { return enchantRegistry; }
    @Nullable public VaultHook getVaultHook() { return vaultHook; }
    @Nullable public PapiHook getPapiHook() { return (papiHook != null && papiHook.isHooked()) ? papiHook : null; }
    public boolean isPlaceholderAPIEnabled() { return papiHook != null && papiHook.isHooked(); }
    @Nullable public WorldGuardHook getWorldGuardHook() { return worldGuardHook; }
    @Nullable public AutoSellConfig getAutoSellConfig() { return autoSellConfig; }
    @Nullable public MessageManager getMessageManager() { return messageManager; }
    public BlackholeManager getBlackholeManager() {
        return blackholeManager;
    }
    public CrystalManager getCrystalManager() {
        return crystalManager;
    }
    public CrystalsGUIListener getCrystalsGUIListener() {
        return crystalsGUIListener;
    }

    @Nullable
    public SkinConfig getSkinConfig() {
        return skinConfig;
    }

    @Nullable
    public MortarManager getMortarManager() {
        return mortarManager;
    }

    // ADDED GETTER
    @Nullable
    public AttachmentManager getAttachmentManager() {
        return attachmentManager;
    }
}