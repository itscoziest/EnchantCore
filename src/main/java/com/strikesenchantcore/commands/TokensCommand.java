package com.strikesenchantcore.commands;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.MessageManager; // Import MessageManager
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.ColorUtils; // Keep for potential future use

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger; // Import Logger
import java.util.stream.Collectors;

public class TokensCommand implements CommandExecutor, TabCompleter {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager; // Cached
    private final MessageManager messageManager; // Cached
    private final Logger logger; // Cached
    // Use a reusable NumberFormat instance
    private final NumberFormat tokenFormat = NumberFormat.getNumberInstance(Locale.US);

    // Default messages (used if keys missing from messages.yml)
    private static final String DEF_NO_PERM = "&cYou do not have permission.";
    private static final String DEF_PLAYER_ONLY = "&cThis command can only be run by a player.";
    private static final String DEF_PLAYER_NOT_FOUND = "&cPlayer '%player%' not found.";
    private static final String DEF_INVALID_AMOUNT = "&cInvalid amount specified.";
    private static final String DEF_BALANCE_SELF = "&aYour Token Balance: &e%balance%";
    private static final String DEF_BALANCE_OTHER = "&a%player%'s Token Balance: &e%balance%";
    private static final String DEF_GIVE_SUCCESS = "&aGave &e%amount% Tokens &ato &f%player%.";
    private static final String DEF_TAKE_SUCCESS = "&cTook &e%amount% Tokens &cfrom &f%player%.";
    private static final String DEF_SET_SUCCESS = "&aSet &f%player%'s &aToken balance to &e%amount%.";
    private static final String DEF_DATA_ERROR = "&cCould not load player data for %player%.";
    private static final String DEF_TAKE_FAIL = "&cFailed to take tokens: Player only has %balance% Tokens.";
    private static final String DEF_UNKNOWN_SUBCOMMAND = "&cUnknown subcommand. Use /tokens help.";
    private static final String DEF_USAGE_BALANCE = "&cUsage: /tokens balance [player]";
    private static final String DEF_USAGE_GIVE = "&cUsage: /tokens give <player> <amount>";
    private static final String DEF_USAGE_TAKE = "&cUsage: /tokens take <player> <amount>";
    private static final String DEF_USAGE_SET = "&cUsage: /tokens set <player> <amount>";
    private static final List<String> DEF_HELP_MESSAGE = Arrays.asList(
            "&m-----------------&r &6EnchantCore Tokens Help &m-----------------",
            "&e/tokens &7- Check your token balance.",
            "&e/tokens balance [player] &7- Check balance (requires permission for others).",
            "&e/tokens give <player> <amount> &7- Give tokens to a player (Admin).",
            "&e/tokens take <player> <amount> &7- Take tokens from a player (Admin).",
            "&e/tokens set <player> <amount> &7- Set a player's token balance (Admin).",
            "&e/tokens help &7- Shows this help message.",
            "&m----------------------------------------------------"
    );

    public TokensCommand(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        // Cache managers
        this.dataManager = plugin.getPlayerDataManager();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger();

        // Validate
        if (this.dataManager == null || this.messageManager == null) {
            logger.severe("PlayerDataManager or MessageManager is null in TokensCommand! Command may not function correctly.");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Manager check
        if (dataManager == null || messageManager == null) {
            ChatUtil.sendMessage(sender, "&cCommand error: Core components not initialized.");
            return true;
        }

        if (args.length == 0) {
            // /tokens - Show own balance
            if (!(sender instanceof Player player)) { // Use pattern variable binding (Java 16+)
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.player_only", DEF_PLAYER_ONLY));
                return true;
            }
            if (!player.hasPermission("enchantcore.tokens.balance")) {
                ChatUtil.sendMessage(player, messageManager.getMessage("commands.tokens.no_permission", DEF_NO_PERM));
                return true;
            }
            PlayerData data = dataManager.getPlayerData(player.getUniqueId());
            if (data == null) data = dataManager.loadPlayerData(player.getUniqueId()); // Try loading if not cached
            if (data == null) {
                ChatUtil.sendMessage(player, messageManager.getMessage("commands.tokens.data_error", DEF_DATA_ERROR).replace("%player%", "you"));
                return true;
            }

            ChatUtil.sendMessage(player, messageManager.getMessage("commands.tokens.balance_self", DEF_BALANCE_SELF)
                    .replace("%balance%", tokenFormat.format(data.getTokens())));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelp(sender);
                break;
            case "balance":
                handleBalance(sender, args);
                break;
            case "give":
                handleModify(sender, args, "give");
                break;
            case "take":
            case "remove": // Added alias
                handleModify(sender, args, "take");
                break;
            case "set":
                handleModify(sender, args, "set");
                break;
            default:
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.unknown_subcommand", DEF_UNKNOWN_SUBCOMMAND));
                sendHelp(sender); // Show help on unknown subcommand
                break;
        }
        return true;
    }

    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length == 1) { // /tokens balance (self)
            if (!(sender instanceof Player player)) {
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.player_only", DEF_PLAYER_ONLY));
                return;
            }
            if (!player.hasPermission("enchantcore.tokens.balance")) {
                ChatUtil.sendMessage(player, messageManager.getMessage("commands.tokens.no_permission", DEF_NO_PERM));
                return;
            }
            PlayerData data = dataManager.getPlayerData(player.getUniqueId());
            if (data == null) data = dataManager.loadPlayerData(player.getUniqueId());
            if (data == null) {
                ChatUtil.sendMessage(player, messageManager.getMessage("commands.tokens.data_error", DEF_DATA_ERROR).replace("%player%", "you"));
                return;
            }
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.tokens.balance_self", DEF_BALANCE_SELF)
                    .replace("%balance%", tokenFormat.format(data.getTokens())));

        } else if (args.length == 2) { // /tokens balance <player>
            if (!sender.hasPermission("enchantcore.tokens.balance.others")) {
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.no_permission", DEF_NO_PERM));
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); // Allow offline check
            if (!target.hasPlayedBefore() && !target.isOnline()) { // Check if player exists
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.player_not_found", DEF_PLAYER_NOT_FOUND).replace("%player%", args[1]));
                return;
            }
            PlayerData data = dataManager.loadPlayerData(target.getUniqueId()); // Load data for target
            if (data == null) {
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.data_error", DEF_DATA_ERROR).replace("%player%", target.getName() != null ? target.getName() : args[1]));
                return;
            }
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.balance_other", DEF_BALANCE_OTHER)
                    .replace("%player%", target.getName() != null ? target.getName() : args[1])
                    .replace("%balance%", tokenFormat.format(data.getTokens())));
        } else {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.usage_balance", DEF_USAGE_BALANCE));
        }
    }

    private void handleModify(CommandSender sender, String[] args, String type) {
        String permission;
        String usageMessageKey;
        String usageDefault;
        String successMessageKey;
        String successDefault;

        switch (type) {
            case "give":
                permission = "enchantcore.tokens.modify.give";
                usageMessageKey = "commands.tokens.usage_give";
                usageDefault = DEF_USAGE_GIVE;
                successMessageKey = "commands.tokens.give_success";
                successDefault = DEF_GIVE_SUCCESS;
                break;
            case "take":
                permission = "enchantcore.tokens.modify.take";
                usageMessageKey = "commands.tokens.usage_take";
                usageDefault = DEF_USAGE_TAKE;
                successMessageKey = "commands.tokens.take_success";
                successDefault = DEF_TAKE_SUCCESS;
                break;
            case "set":
                permission = "enchantcore.tokens.modify.set";
                usageMessageKey = "commands.tokens.usage_set";
                usageDefault = DEF_USAGE_SET;
                successMessageKey = "commands.tokens.set_success";
                successDefault = DEF_SET_SUCCESS;
                break;
            default: return; // Should not happen
        }

        if (!sender.hasPermission(permission)) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.no_permission", DEF_NO_PERM));
            return;
        }
        if (args.length < 3) {
            ChatUtil.sendMessage(sender, messageManager.getMessage(usageMessageKey, usageDefault));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.player_not_found", DEF_PLAYER_NOT_FOUND).replace("%player%", args[1]));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : args[1]; // Use stored name if available

        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 0) throw new NumberFormatException("Amount cannot be negative");
        } catch (NumberFormatException e) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.invalid_amount", DEF_INVALID_AMOUNT));
            return;
        }

        // Load target data (important to modify the correct object)
        PlayerData targetData = dataManager.loadPlayerData(target.getUniqueId());
        if (targetData == null) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.data_error", DEF_DATA_ERROR).replace("%player%", targetName));
            return;
        }

        long currentBal = targetData.getTokens();
        String formattedAmount = tokenFormat.format(amount); // Format amount for messages

        switch (type) {
            case "give":
                targetData.addTokens(amount);
                ChatUtil.sendMessage(sender, messageManager.getMessage(successMessageKey, successDefault)
                        .replace("%amount%", formattedAmount)
                        .replace("%player%", targetName));
                break;
            case "take":
                if (!targetData.removeTokens(amount)) { // removeTokens handles check and deduction
                    ChatUtil.sendMessage(sender, messageManager.getMessage("commands.tokens.take_fail_insufficient", DEF_TAKE_FAIL)
                            .replace("%balance%", tokenFormat.format(currentBal)));
                    return; // Don't save if failed
                }
                ChatUtil.sendMessage(sender, messageManager.getMessage(successMessageKey, successDefault)
                        .replace("%amount%", formattedAmount)
                        .replace("%player%", targetName));
                break;
            case "set":
                targetData.setTokens(amount);
                ChatUtil.sendMessage(sender, messageManager.getMessage(successMessageKey, successDefault)
                        .replace("%amount%", formattedAmount)
                        .replace("%player%", targetName));
                break;
        }

        // Save data after successful modification (asynchronously)
        dataManager.savePlayerData(targetData, true);
    }

    private void sendHelp(CommandSender sender){
        // Get help message list from MessageManager
        List<String> helpLines = messageManager.getMessageList("commands.tokens.help", DEF_HELP_MESSAGE);
        for(String line : helpLines){
            // MessageManager handles coloring
            sender.sendMessage(line);
        }
    }

    // --- Tab Completion ---
    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> possibilities = new ArrayList<>();

        if (args.length == 1) { // Complete subcommand
            possibilities.addAll(Arrays.asList("balance", "give", "take", "set", "help"));
            // Filter based on permissions? Optional, can expose commands but execution will fail.
            // Example:
            // if (sender.hasPermission("enchantcore.tokens.balance")) possibilities.add("balance");
            // if (sender.hasPermission("enchantcore.tokens.modify.give")) possibilities.add("give");
            // etc.
            StringUtil.copyPartialMatches(args[0], possibilities, completions);
        } else if (args.length == 2) { // Complete player name for relevant subcommands
            String sub = args[0].toLowerCase();
            // Check if the subcommand requires a player argument and sender has permission
            if (sub.equals("balance") && sender.hasPermission("enchantcore.tokens.balance.others") ||
                    sub.equals("give") && sender.hasPermission("enchantcore.tokens.modify.give") ||
                    sub.equals("take") && sender.hasPermission("enchantcore.tokens.modify.take") ||
                    sub.equals("set") && sender.hasPermission("enchantcore.tokens.modify.set"))
            {
                // Suggest online players
                possibilities.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                StringUtil.copyPartialMatches(args[1], possibilities, completions);
            }
        } else if (args.length == 3) { // Suggest amount for modify commands?
            String sub = args[0].toLowerCase();
            if ((sub.equals("give") && sender.hasPermission("enchantcore.tokens.modify.give")) ||
                    (sub.equals("take") && sender.hasPermission("enchantcore.tokens.modify.take")) ||
                    (sub.equals("set") && sender.hasPermission("enchantcore.tokens.modify.set")))
            {
                // Suggest common amounts? Optional.
                // possibilities.addAll(Arrays.asList("1", "10", "100", "1000", "10000"));
                // StringUtil.copyPartialMatches(args[2], possibilities, completions);
            }
        }

        Collections.sort(completions); // Sort alphabetically
        return completions;
    }
}