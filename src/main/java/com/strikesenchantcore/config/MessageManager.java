package com.strikesenchantcore.config;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.util.ChatUtil; // Required for coloring
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull; // Import NotNull
import org.jetbrains.annotations.Nullable; // For @Nullable annotation
import java.util.Objects;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger; // Import Logger
import java.util.stream.Collectors;

/**
 * Manages loading and retrieving messages from messages.yml.
 * Automatically applies color codes (& and #) to retrieved messages.
 */
public class MessageManager {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger
    private File messagesFile;
    private FileConfiguration messagesConfig;

    public MessageManager(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        setup();
        loadMessages(); // Load messages immediately on initialization
    }

    /**
     * Sets up the messages file object and configuration instance.
     * Creates the default file if it doesn't exist.
     */
    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false); // Copy default from JAR
            logger.info("Created default messages.yml");
        }
        messagesConfig = new YamlConfiguration();
    }

    /**
     * Loads messages from the messages.yml file into the configuration object.
     */
    public void loadMessages() {
        // Ensure file exists, attempt setup if not
        if (!messagesFile.exists()) {
            logger.warning("messages.yml not found! Attempting to recreate...");
            setup(); // This will load if creation is successful
        } else {
            // If file exists, load it
            try {
                messagesConfig.load(messagesFile);
                logger.info("Loaded messages from messages.yml");
            } catch (IOException | InvalidConfigurationException e) {
                logger.log(Level.SEVERE, "Could not load messages.yml! Default messages will be used where possible.", e);
                // Keep the potentially empty/null messagesConfig, getters will use defaults
            }
        }
    }

    /**
     * Reloads messages from the messages.yml file.
     */
    public void reloadMessages() {
        // Ensure file exists, attempt setup if not
        if (!messagesFile.exists()) {
            logger.warning("messages.yml not found! Cannot reload. Attempting to create default.");
            setup(); // Create default if missing
        }
        // Attempt to reload from disk
        try {
            messagesConfig.load(messagesFile); // Reload from disk
            logger.info("Reloaded messages.yml");
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Could not reload messages.yml!", e);
            // Keep using previously loaded messages if reload fails
        }
    }

    /**
     * Gets a message string from the config, applies color codes, and returns it.
     * If the key is not found, returns the provided default message (also colored).
     *
     * @param key            The configuration key (e.g., "common.no_permission").
     * @param defaultMessage The default message to return if the key is not found.
     * @return The formatted message string. Never returns null if defaultMessage is not null.
     */
    @NotNull
    public String getMessage(@NotNull String key, @NotNull String defaultMessage) {
        // Get string from config, using defaultMessage if key is missing
        String message = messagesConfig.getString(key, defaultMessage);
        // Ensure we don't return null if defaultMessage was somehow null (shouldn't happen with @NotNull)
        String nonNullMessage = (message != null) ? message : (defaultMessage != null ? defaultMessage : "");
        // Apply color codes (& and #)
        return ChatUtil.color(nonNullMessage);
    }

    /**
     * Gets a message string from the config, applies color codes.
     * Returns an empty string "" if the key is not found.
     *
     * @param key The configuration key (e.g., "common.no_permission").
     * @return The formatted message string or an empty string. Never null.
     */
    @NotNull
    public String getMessage(@NotNull String key) {
        // Call the main getter with an empty string as the default
        return getMessage(key, "");
    }


    /**
     * Gets a list of strings from the config, applies color codes to each line.
     * If the key is not found or the list is empty, returns the provided default list (also colored).
     *
     * @param key         The configuration key for the list.
     * @param defaultList The default list to return if the key is not found or list is empty.
     * @return A new list containing formatted message strings. Never returns null if defaultList is not null.
     */
    @NotNull
    public List<String> getMessageList(@NotNull String key, @NotNull List<String> defaultList) {
        // Get list from config
        List<String> list = messagesConfig.getStringList(key);
        // Use default list if config returned null or empty list
        if (list == null || list.isEmpty()) {
            list = defaultList;
        }
        // Ensure the chosen list is not null before streaming (shouldn't happen with @NotNull defaultList)
        if (list == null) {
            list = Collections.emptyList();
        }
        // Apply color codes (& and #) to each line in the list
        return list.stream()
                .filter(Objects::nonNull) // Ensure lines within the list aren't null
                .map(ChatUtil::color)
                .collect(Collectors.toList());
    }

    /**
     * Gets a list of strings from the config, applies color codes to each line.
     * Returns an empty list if the key is not found or the list is empty.
     *
     * @param key The configuration key for the list.
     * @return The list of formatted message strings or an empty list. Never null.
     */
    @NotNull
    public List<String> getMessageList(@NotNull String key) {
        // Call the main getter with an empty list as the default
        return getMessageList(key, Collections.emptyList());
    }

    /**
     * Provides direct access to the loaded messages.yml FileConfiguration.
     * Use cautiously; prefer specific getMessage/getMessageList methods.
     * @return The FileConfiguration for messages.yml, or null if loading failed critically.
     */
    @Nullable
    public FileConfiguration getConfig() {
        return messagesConfig;
    }
}