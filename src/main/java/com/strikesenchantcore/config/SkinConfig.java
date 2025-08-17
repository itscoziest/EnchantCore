package com.strikesenchantcore.config;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.util.ColorUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SkinConfig {

    private final EnchantCore plugin;
    private final Logger logger;
    private final File configFile;
    private FileConfiguration config;

    // GUI Settings
    private String guiTitle;
    private int guiSize;
    private boolean fillerEnabled;
    private Material fillerMaterial;
    private String fillerName;
    private int fillerModelData;
    private int backButtonSlot;
    private Material backButtonMaterial;
    private String backButtonName;
    private List<String> backButtonLore;
    private int backButtonModelData;

    // Skins
    private final Map<String, SkinData> skins = new LinkedHashMap<>();

    // Messages
    private final Map<String, String> messages = new HashMap<>();

    // Sounds
    private final Map<String, SoundData> sounds = new HashMap<>();

    public SkinConfig(EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "skins.yml");

        // Create default config if it doesn't exist
        if (!configFile.exists()) {
            plugin.saveResource("skins.yml", false);
        }

        loadConfig();
    }

    /**
     * Loads the skins configuration
     */
    public void loadConfig() {
        try {
            this.config = YamlConfiguration.loadConfiguration(configFile);
            loadGuiSettings();
            loadSkins();
            loadMessages();
            loadSounds();
            logger.info("Loaded skins.yml configuration successfully. Title: " + guiTitle);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load skins.yml configuration!", e);
        }
    }

    /**
     * Loads GUI settings from config
     */
    private void loadGuiSettings() {
        ConfigurationSection guiSection = config.getConfigurationSection("gui");
        if (guiSection == null) {
            logger.warning("GUI section not found in skins.yml, using defaults.");
            setDefaultGuiSettings();
            return;
        }

        this.guiTitle = guiSection.getString("title", "&6&lPickaxe Skins");
        this.guiSize = guiSection.getInt("size", 54);

        // Validate GUI size
        if (guiSize % 9 != 0 || guiSize < 9 || guiSize > 54) {
            logger.warning("Invalid GUI size in skins.yml: " + guiSize + ". Using default: 54");
            this.guiSize = 54;
        }

        // Filler settings
        ConfigurationSection fillerSection = guiSection.getConfigurationSection("filler");
        if (fillerSection != null) {
            this.fillerEnabled = fillerSection.getBoolean("enabled", true);
            String materialName = fillerSection.getString("material", "GRAY_STAINED_GLASS_PANE");
            try {
                this.fillerMaterial = Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid filler material in skins.yml: " + materialName + ". Using GRAY_STAINED_GLASS_PANE");
                this.fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
            }
            this.fillerName = fillerSection.getString("name", "&7");
            this.fillerModelData = fillerSection.getInt("custom-model-data", 0);
        } else {
            this.fillerEnabled = true;
            this.fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
            this.fillerName = "&7";
            this.fillerModelData = 0;
        }

        // Back button settings
        ConfigurationSection backSection = guiSection.getConfigurationSection("back-button");
        if (backSection != null) {
            this.backButtonSlot = backSection.getInt("slot", 49);
            String materialName = backSection.getString("material", "ARROW");
            try {
                this.backButtonMaterial = Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid back button material in skins.yml: " + materialName + ". Using ARROW");
                this.backButtonMaterial = Material.ARROW;
            }
            this.backButtonName = backSection.getString("name", "&c&lBack");
            this.backButtonLore = backSection.getStringList("lore");
            this.backButtonModelData = backSection.getInt("custom-model-data", 0);
        } else {
            this.backButtonSlot = 49;
            this.backButtonMaterial = Material.ARROW;
            this.backButtonName = "&c&lBack";
            this.backButtonLore = Arrays.asList("&7Click to return to", "&7the enchant menu");
            this.backButtonModelData = 0;
        }
    }

    /**
     * Sets default GUI settings
     */
    private void setDefaultGuiSettings() {
        this.guiTitle = "&6&lPickaxe Skins";
        this.guiSize = 54;
        this.fillerEnabled = true;
        this.fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
        this.fillerName = "&7";
        this.fillerModelData = 0;
        this.backButtonSlot = 49;
        this.backButtonMaterial = Material.ARROW;
        this.backButtonName = "&c&lBack";
        this.backButtonLore = Arrays.asList("&7Click to return to", "&7the enchant menu");
        this.backButtonModelData = 0;
    }

    /**
     * Loads skin configurations
     */
    private void loadSkins() {
        skins.clear();
        ConfigurationSection skinsSection = config.getConfigurationSection("skins");
        if (skinsSection == null) {
            logger.warning("Skins section not found in skins.yml!");
            return;
        }

        for (String skinId : skinsSection.getKeys(false)) {
            ConfigurationSection skinSection = skinsSection.getConfigurationSection(skinId);
            if (skinSection == null) continue;

            try {
                SkinData skinData = loadSkinData(skinId, skinSection);
                if (skinData != null) {
                    skins.put(skinId, skinData);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load skin: " + skinId, e);
            }
        }

        logger.info("Loaded " + skins.size() + " pickaxe skins from configuration.");
    }

    /**
     * Loads individual skin data
     */
    @Nullable
    private SkinData loadSkinData(String skinId, ConfigurationSection section) {
        if (!section.getBoolean("enabled", true)) {
            return null; // Skip disabled skins
        }

        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= guiSize) {
            logger.warning("Invalid slot for skin " + skinId + ": " + slot);
            return null;
        }

        String materialName = section.getString("material", "DIAMOND_PICKAXE");
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid material for skin " + skinId + ": " + materialName);
            return null;
        }

        int customModelData = section.getInt("custom-model-data", 0);
        String itemsAdderID = section.getString("itemsadder-id", null);
        String name = section.getString("name", "&7" + skinId);
        List<String> lore = section.getStringList("lore");
        String permission = section.getString("permission", null);

        return new SkinData(skinId, slot, material, customModelData, itemsAdderID, name, lore, permission);
    }

    /**
     * Loads messages from config
     */
    private void loadMessages() {
        messages.clear();
        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection == null) {
            setDefaultMessages();
            return;
        }

        for (String key : messagesSection.getKeys(false)) {
            String message = messagesSection.getString(key);
            if (message != null) {
                messages.put(key, ColorUtils.translateColors(message));
            }
        }

        // Ensure all required messages exist
        setDefaultMessages();
    }

    /**
     * Sets default messages for any missing ones
     */
    private void setDefaultMessages() {
        messages.putIfAbsent("skin-applied", ColorUtils.translateColors("&a&lSkin Applied! &7Your pickaxe now has the %skin_name% &7skin!"));
        messages.putIfAbsent("skin-already-selected", ColorUtils.translateColors("&c&lAlready Selected! &7This skin is already applied to your pickaxe."));
        messages.putIfAbsent("no-permission", ColorUtils.translateColors("&c&lNo Permission! &7You don't have permission to use this skin."));
        messages.putIfAbsent("error-applying", ColorUtils.translateColors("&c&lError! &7Failed to apply the skin. Please try again."));
        messages.putIfAbsent("pickaxe-context-lost", ColorUtils.translateColors("&cError: Pickaxe context lost or invalid. Please reopen the menu."));
        messages.putIfAbsent("data-load-error", ColorUtils.translateColors("&cError: Could not load your data. Please try again."));
        messages.putIfAbsent("internal-error", ColorUtils.translateColors("&cAn internal error occurred processing your click."));
    }

    /**
     * Loads sounds from config
     */
    private void loadSounds() {
        sounds.clear();
        ConfigurationSection soundsSection = config.getConfigurationSection("sounds");
        if (soundsSection == null) {
            setDefaultSounds();
            return;
        }

        for (String key : soundsSection.getKeys(false)) {
            ConfigurationSection soundSection = soundsSection.getConfigurationSection(key);
            if (soundSection != null) {
                try {
                    String soundName = soundSection.getString("sound", "UI_BUTTON_CLICK");
                    Sound sound = Sound.valueOf(soundName);
                    float volume = (float) soundSection.getDouble("volume", 1.0);
                    float pitch = (float) soundSection.getDouble("pitch", 1.0);
                    sounds.put(key, new SoundData(sound, volume, pitch));
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid sound for " + key + " in skins.yml: " + soundSection.getString("sound"));
                }
            }
        }

        setDefaultSounds();
    }

    /**
     * Sets default sounds for any missing ones
     */
    private void setDefaultSounds() {
        sounds.putIfAbsent("skin-applied", new SoundData(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f));
        sounds.putIfAbsent("skin-already-selected", new SoundData(Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f));
        sounds.putIfAbsent("no-permission", new SoundData(Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f));
        sounds.putIfAbsent("back-button", new SoundData(Sound.UI_BUTTON_CLICK, 1.0f, 1.0f));
    }

    /**
     * Saves the configuration
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save skins.yml!", e);
        }
    }

    // Getters
    public String getGuiTitle() { return ColorUtils.translateColors(guiTitle); }
    public int getGuiSize() { return guiSize; }
    public boolean isFillerEnabled() { return fillerEnabled; }
    public Material getFillerMaterial() { return fillerMaterial; }
    public String getFillerName() { return ColorUtils.translateColors(fillerName); }
    public int getFillerModelData() { return fillerModelData; }
    public int getBackButtonSlot() { return backButtonSlot; }
    public Material getBackButtonMaterial() { return backButtonMaterial; }
    public String getBackButtonName() { return ColorUtils.translateColors(backButtonName); }
    public List<String> getBackButtonLore() {
        return backButtonLore.stream()
                .map(ColorUtils::translateColors)
                .collect(java.util.stream.Collectors.toList());
    }
    public int getBackButtonModelData() { return backButtonModelData; }

    @NotNull
    public Map<String, SkinData> getSkins() { return new HashMap<>(skins); }

    @Nullable
    public SkinData getSkin(String skinId) { return skins.get(skinId); }

    @NotNull
    public String getMessage(String key) { return messages.getOrDefault(key, "&cMessage not found: " + key); }

    @Nullable
    public SoundData getSound(String key) { return sounds.get(key); }

    /**
     * Data class for skin information
     */
    public static class SkinData {
        private final String id;
        private final int slot;
        private final Material material;
        private final int customModelData;
        private final String itemsAdderID;
        private final String name;
        private final List<String> lore;
        private final String permission;

        public SkinData(String id, int slot, Material material, int customModelData,
                        String itemsAdderID, String name, List<String> lore, String permission) {
            this.id = id;
            this.slot = slot;
            this.material = material;
            this.customModelData = customModelData;
            this.itemsAdderID = itemsAdderID;
            this.name = name;
            this.lore = new ArrayList<>(lore);
            this.permission = permission;
        }

        public String getId() { return id; }
        public int getSlot() { return slot; }
        public Material getMaterial() { return material; }
        public int getCustomModelData() { return customModelData; }
        public String getItemsAdderID() { return itemsAdderID; }
        public boolean hasItemsAdderID() { return itemsAdderID != null && !itemsAdderID.trim().isEmpty(); }
        public String getName() { return ColorUtils.translateColors(name); }
        public List<String> getLore() {
            return lore.stream()
                    .map(ColorUtils::translateColors)
                    .collect(java.util.stream.Collectors.toList());
        }
        public String getPermission() { return permission; }
        public boolean hasPermission() { return permission != null && !permission.isEmpty(); }
    }

    /**
     * Data class for sound information
     */
    public static class SoundData {
        private final Sound sound;
        private final float volume;
        private final float pitch;

        public SoundData(Sound sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }

        public Sound getSound() { return sound; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
    }
}