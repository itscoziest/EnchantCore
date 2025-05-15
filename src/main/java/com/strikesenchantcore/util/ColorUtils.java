package com.strikesenchantcore.util;

import org.bukkit.ChatColor;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for translating color codes, including legacy (&) and hex (#RRGGBB).
 * Uses corrected regex for hex codes followed by other characters.
 * This class does NOT use the Adventure API.
 */
public class ColorUtils {

    // Corrected Regex: Finds #RRGGBB regardless of following character
    private static final Pattern HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");

    /**
     * Translates a hex code like #RRGGBB into the Spigot §x§R§R§G§G§B§B format.
     * Internal use.
     * @param hex The hex code string (e.g., "#FF00FF")
     * @return The Spigot internal format string.
     */
    private static String hexToSpigotFormat(String hex) {
        if (hex == null || !hex.startsWith("#") || hex.length() != 7) return hex;
        try { Integer.parseInt(hex.substring(1), 16); } catch (NumberFormatException e) { return hex; } // Validate hex

        char[] chars = hex.substring(1).toCharArray();
        StringBuilder builder = new StringBuilder(ChatColor.COLOR_CHAR + "x"); // Use '§'
        for (char c : chars) {
            builder.append(ChatColor.COLOR_CHAR).append(Character.toLowerCase(c));
        }
        return builder.toString();
    }

    /**
     * Translates a string containing hex codes (#RRGGBB) and legacy codes (&c,&l,etc.)
     * into a string using Spigot's internal color codes (§c, §l, §x§R§R§G§G§B§B).
     * This is the main method you should call for any text needing color.
     * @param text The input string with potential color codes like "&cHello #FF00FFWorld".
     * @return The translated string ready for sending via standard Bukkit/Spigot methods.
     */
    public static String translateColors(String text) {
        if (text == null || text.isEmpty()) return text;

        // First, translate legacy codes (&) to Spigot codes (§)
        String legacyTranslated = ChatColor.translateAlternateColorCodes('&', text);

        // Then, find and replace hex codes (#RRGGBB) using the corrected pattern
        Matcher matcher = HEX_PATTERN.matcher(legacyTranslated);
        StringBuffer buffer = new StringBuffer(legacyTranslated.length()); // Use StringBuffer for matcher replacement

        while (matcher.find()) {
            String hexCode = matcher.group(0); // Includes the '#' e.g., #FF00AA
            String spigotHex = hexToSpigotFormat(hexCode);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(spigotHex)); // Escape replacement string
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    /**
     * Translates colors for every string in a list. Useful for item lore.
     * @param list The list of strings to translate.
     * @return A new list with translated strings. Returns null if input list is null.
     */
    public static List<String> translateColors(List<String> list) {
        if (list == null) return null;
        if (list.isEmpty()) return new ArrayList<>(); // Return empty mutable list
        return list.stream()
                .map(ColorUtils::translateColors) // Translate each string
                .collect(Collectors.toList());
    }
}