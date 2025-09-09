package com.strikesenchantcore.commands;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.util.ChatUtil;
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PointsCommand implements CommandExecutor, TabCompleter {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager;
    private final MessageManager messageManager;
    private final Logger logger;
    private final NumberFormat pointFormat = NumberFormat.getNumberInstance(Locale.US);

    // Default messages
    private static final String DEF_NO_PERM = "&cYou do not have permission.";
    private static final String DEF_PLAYER_ONLY = "&cThis command can only be run by a player.";
    private static final String DEF_PLAYER_NOT_FOUND = "&cPlayer '%player%' not found.";
    private static final String DEF_INVALID_AMOUNT = "&cInvalid amount specified.";
    private static final String DEF_BALANCE_SELF = "&aYour Point Balance: &e%balance%";
    private static final String DEF_BALANCE_OTHER = "&a%player%'s Point Balance: &e%balance%";
    private static final String DEF_GIVE_SUCCESS = "&aGave &e%amount% Points &ato &f%player%.";
    private static final String DEF_TAKE_SUCCESS = "&cTook &e%amount% Points &cfrom &f%player%.";
    private static final String DEF_SET_SUCCESS = "&aSet &f%player%'s &aPoint balance to &e%amount%.";
    private static final String DEF_DATA_ERROR = "&cCould not load player data for %player%.";
    private static final String DEF_TAKE_FAIL = "&cFailed to take points: Player only has %balance% Points.";
    private static final String DEF_UNKNOWN_SUBCOMMAND = "&cUnknown subcommand. Use /points help.";
    private static final String DEF_USAGE_BALANCE = "&cUsage: /points balance [player]";
    private static final String DEF_USAGE_GIVE = "&cUsage: /points give <player> <amount>";
    private static final String DEF_USAGE_TAKE = "&cUsage: /points take <player> <amount>";
    private static final String DEF_USAGE_SET = "&cUsage: /points set <player> <amount>";
    private static final List<String> DEF_HELP_MESSAGE = Arrays.asList(
            "&m-----------------&r &eRebirth Points Help &m-----------------",
            "&e/points &7- Check your point balance.",
            "&e/points balance [player] &7- Check balance (requires permission for others).",
            "&e/points give <player> <amount> &7- Give points to a player (Admin).",
            "&e/points take <player> <amount> &7- Take points from a player (Admin).",
            "&e/points set <player> <amount> &7- Set a player's point balance (Admin).",
            "&e/points help &7- Shows this help message.",
            "&m----------------------------------------------------"
    );

    public PointsCommand(@NotNull EnchantCore plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (dataManager == null || messageManager == null) {
            ChatUtil.sendMessage(sender, "&cCommand error: Core components not initialized.");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.player_only", DEF_PLAYER_ONLY));
                return true;
            }
            if (!player.hasPermission("enchantcore.points.balance")) {
                ChatUtil.sendMessage(player, messageManager.getMessage("commands.points.no_permission", DEF_NO_PERM));
                return true;
            }
            PlayerData data = dataManager.getPlayerData(player.getUniqueId());
            if (data == null) data = dataManager.loadPlayerData(player.getUniqueId());
            if (data == null) {
                ChatUtil.sendMessage(player, messageManager.getMessage("commands.points.data_error", DEF_DATA_ERROR).replace("%player%", "you"));
                return true;
            }

            ChatUtil.sendMessage(player, messageManager.getMessage("commands.points.balance_self", DEF_BALANCE_SELF)
                    .replace("%balance%", pointFormat.format(data.getPoints())));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help": sendHelp(sender); break;
            case "balance": handleBalance(sender, args); break;
            case "give": handleModify(sender, args, "give"); break;
            case "take", "remove": handleModify(sender, args, "take"); break;
            case "set": handleModify(sender, args, "set"); break;
            default:
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.unknown_subcommand", DEF_UNKNOWN_SUBCOMMAND));
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.player_only", DEF_PLAYER_ONLY));
                return;
            }
            if (!player.hasPermission("enchantcore.points.balance")) {
                ChatUtil.sendMessage(player, messageManager.getMessage("commands.points.no_permission", DEF_NO_PERM));
                return;
            }
            PlayerData data = dataManager.getPlayerData(player.getUniqueId());
            if (data == null) data = dataManager.loadPlayerData(player.getUniqueId());
            if (data == null) {
                ChatUtil.sendMessage(player, messageManager.getMessage("commands.points.data_error", DEF_DATA_ERROR).replace("%player%", "you"));
                return;
            }
            ChatUtil.sendMessage(player, messageManager.getMessage("commands.points.balance_self", DEF_BALANCE_SELF)
                    .replace("%balance%", pointFormat.format(data.getPoints())));

        } else if (args.length == 2) {
            if (!sender.hasPermission("enchantcore.points.balance.others")) {
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.no_permission", DEF_NO_PERM));
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.player_not_found", DEF_PLAYER_NOT_FOUND).replace("%player%", args[1]));
                return;
            }
            PlayerData data = dataManager.loadPlayerData(target.getUniqueId());
            if (data == null) {
                ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.data_error", DEF_DATA_ERROR).replace("%player%", target.getName() != null ? target.getName() : args[1]));
                return;
            }
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.balance_other", DEF_BALANCE_OTHER)
                    .replace("%player%", target.getName() != null ? target.getName() : args[1])
                    .replace("%balance%", pointFormat.format(data.getPoints())));
        } else {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.usage_balance", DEF_USAGE_BALANCE));
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
                permission = "enchantcore.points.modify.give";
                usageMessageKey = "commands.points.usage_give";
                usageDefault = DEF_USAGE_GIVE;
                successMessageKey = "commands.points.give_success";
                successDefault = DEF_GIVE_SUCCESS;
                break;
            case "take":
                permission = "enchantcore.points.modify.take";
                usageMessageKey = "commands.points.usage_take";
                usageDefault = DEF_USAGE_TAKE;
                successMessageKey = "commands.points.take_success";
                successDefault = DEF_TAKE_SUCCESS;
                break;
            case "set":
                permission = "enchantcore.points.modify.set";
                usageMessageKey = "commands.points.usage_set";
                usageDefault = DEF_USAGE_SET;
                successMessageKey = "commands.points.set_success";
                successDefault = DEF_SET_SUCCESS;
                break;
            default: return;
        }

        if (!sender.hasPermission(permission)) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.no_permission", DEF_NO_PERM));
            return;
        }
        if (args.length < 3) {
            ChatUtil.sendMessage(sender, messageManager.getMessage(usageMessageKey, usageDefault));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.player_not_found", DEF_PLAYER_NOT_FOUND).replace("%player%", args[1]));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : args[1];

        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 0) throw new NumberFormatException("Amount cannot be negative");
        } catch (NumberFormatException e) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.invalid_amount", DEF_INVALID_AMOUNT));
            return;
        }

        PlayerData targetData = dataManager.loadPlayerData(target.getUniqueId());
        if (targetData == null) {
            ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.data_error", DEF_DATA_ERROR).replace("%player%", targetName));
            return;
        }

        long currentBal = targetData.getPoints();
        String formattedAmount = pointFormat.format(amount);

        switch (type) {
            case "give":
                targetData.addPoints(amount);
                ChatUtil.sendMessage(sender, messageManager.getMessage(successMessageKey, successDefault)
                        .replace("%amount%", formattedAmount)
                        .replace("%player%", targetName));
                break;
            case "take":
                if (!targetData.removePoints(amount)) {
                    ChatUtil.sendMessage(sender, messageManager.getMessage("commands.points.take_fail_insufficient", DEF_TAKE_FAIL)
                            .replace("%balance%", pointFormat.format(currentBal)));
                    return;
                }
                ChatUtil.sendMessage(sender, messageManager.getMessage(successMessageKey, successDefault)
                        .replace("%amount%", formattedAmount)
                        .replace("%player%", targetName));
                break;
            case "set":
                targetData.setPoints(amount);
                ChatUtil.sendMessage(sender, messageManager.getMessage(successMessageKey, successDefault)
                        .replace("%amount%", formattedAmount)
                        .replace("%player%", targetName));
                break;
        }

        dataManager.savePlayerData(targetData, true);
    }

    private void sendHelp(CommandSender sender){
        List<String> helpLines = messageManager.getMessageList("commands.points.help", DEF_HELP_MESSAGE);
        for(String line : helpLines){
            sender.sendMessage(line);
        }
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> possibilities = new ArrayList<>();

        if (args.length == 1) {
            possibilities.addAll(Arrays.asList("balance", "give", "take", "set", "help"));
            StringUtil.copyPartialMatches(args[0], possibilities, completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("balance") && sender.hasPermission("enchantcore.points.balance.others") ||
                    sub.equals("give") && sender.hasPermission("enchantcore.points.modify.give") ||
                    sub.equals("take") && sender.hasPermission("enchantcore.points.modify.take") ||
                    sub.equals("set") && sender.hasPermission("enchantcore.points.modify.set"))
            {
                possibilities.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                StringUtil.copyPartialMatches(args[1], possibilities, completions);
            }
        }
        Collections.sort(completions);
        return completions;
    }
}