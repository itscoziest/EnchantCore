package com.strikesenchantcore.commands;

import com.strikesenchantcore.EnchantCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger; // Import Logger
import java.util.stream.Collectors;

/**
 * Handles tab completion for the /enchantcore command.
 */
public class EnchantCoreTabCompleter implements TabCompleter {

    private final EnchantCore plugin;
    private final Logger logger; // Cache logger

    // Subcommands available to administrators
    private static final List<String> SUBCOMMANDS_ADMIN = Arrays.asList(
            "reload", "give", "givemax", "setlevel", "addblocks"
    );
    // Add lists for non-admin commands if any are created later
    // private static final List<String> SUBCOMMANDS_PLAYER = Arrays.asList("help", "gui"); // Example

    public EnchantCoreTabCompleter(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> possibilities = new ArrayList<>();

        // --- Complete Subcommand (args.length == 1) ---
        if (args.length == 1) {
            // Add admin commands if sender has permission
            if (sender.hasPermission("enchantcore.admin")) {
                possibilities.addAll(SUBCOMMANDS_ADMIN);
            }
            // Add player commands here (uncomment and modify if needed)
            // possibilities.addAll(SUBCOMMANDS_PLAYER);

            // Copy partial matches from possibilities to completions
            StringUtil.copyPartialMatches(args[0], possibilities, completions);
        }
        // --- Complete Arguments (args.length >= 2) ---
        else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();

            // Complete player names for relevant admin commands (give, givemax, setlevel, addblocks)
            if (sender.hasPermission("enchantcore.admin") &&
                    (subCommand.equals("give") || subCommand.equals("givemax") || subCommand.equals("setlevel") || subCommand.equals("addblocks")))
            {
                // If completing the second argument (the player name)
                if (args.length == 2) {
                    // Suggest online players
                    possibilities.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                    StringUtil.copyPartialMatches(args[1], possibilities, completions);
                }
                // No suggestions for 3rd+ args (level/amount) for these commands currently
            }
            // Add argument completions for other subcommands here if needed
            // else if (subCommand.equals("someothercommand")) { ... }

        } // End argument completion logic

        Collections.sort(completions); // Sort suggestions alphabetically
        return completions;
    }
}