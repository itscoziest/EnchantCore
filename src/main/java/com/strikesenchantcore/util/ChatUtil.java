package com.strikesenchantcore.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import java.util.regex.Pattern;

/**
 * Utility class for chat-related functions. Uses ColorUtils for unified color handling.
 * This class does NOT use the Adventure API.
 */
public class ChatUtil {

    // Pattern for stripping common color codes (§ and &)
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)[" + ChatColor.COLOR_CHAR + "&][0-9A-FK-ORX]");
    // Pattern to help strip the §x hex sequence (may not be perfect for edge cases)
    private static final Pattern STRIP_HEX_PATTERN = Pattern.compile("(?i)" + ChatColor.COLOR_CHAR + "x(?:[" + ChatColor.COLOR_CHAR + "][0-9A-F]){6}");


    /**
     * Translates a string using ColorUtils (handles legacy & and hex #).
     * @param text The string to translate.
     * @return The translated string with Bukkit color codes (§).
     */
    public static String color(String text) {
        // Delegate ALL color translation (legacy & hex) to ColorUtils
        return ColorUtils.translateColors(text);
    }

    /**
     * Sends a color-formatted message to a CommandSender.
     * @param sender The CommandSender to send the message to.
     * @param message The message string (will be color-formatted).
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            sender.sendMessage(color(message)); // Use internal color method
        }
    }

    /**
     * Removes standard Bukkit/legacy color codes and attempts to remove hex sequences.
     * @param input The string to strip colors from.
     * @return The string without color codes.
     */
    public static String stripColor(String input) {
        if (input == null) return null;
        // Strip standard codes first
        String stripped = STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
        // Then attempt to strip hex sequences
        return STRIP_HEX_PATTERN.matcher(stripped).replaceAll("");
    }
}