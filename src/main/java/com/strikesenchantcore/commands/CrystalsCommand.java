package com.strikesenchantcore.commands;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.managers.CrystalManager;
import com.strikesenchantcore.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.strikesenchantcore.gui.CrystalsGUI;
import com.strikesenchantcore.gui.CrystalsGUIListener;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CrystalsCommand implements CommandExecutor, TabCompleter {
    private final EnchantCore plugin;
    private final CrystalManager crystalManager;

    public CrystalsCommand(EnchantCore plugin) {
        this.plugin = plugin;
        this.crystalManager = plugin.getCrystalManager();
        if (this.crystalManager == null) {
            plugin.getLogger().severe("CrystalManager is null in CrystalsCommand!");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            ChatUtil.sendMessage(sender, "&cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Open crystals GUI
            new CrystalsGUI(plugin, player).open();
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                return handleHelp(player);

            case "info":
            case "stats":
                return handleInfo(player);

            case "give":
                if (!player.hasPermission("enchantcore.crystals.admin")) {
                    ChatUtil.sendMessage(player, "&cYou don't have permission to use this command!");
                    return true;
                }
                return handleGive(player, args);

            case "merge":
                return handleMerge(player, args);

            default:
                ChatUtil.sendMessage(player, "&cUnknown subcommand! Use &e/crystals help &cfor available commands.");
                return true;
        }
    }

    private boolean handleHelp(Player player) {
        ChatUtil.sendMessage(player, "&8&m--------------------------------");
        ChatUtil.sendMessage(player, "&b&lCRYSTALS HELP");
        ChatUtil.sendMessage(player, "&8&m--------------------------------");
        ChatUtil.sendMessage(player, "&e/crystals &7- Open crystals GUI");
        ChatUtil.sendMessage(player, "&e/crystals info &7- View your crystal statistics");
        ChatUtil.sendMessage(player, "&e/crystals merge <type> <tier> &7- Merge crystals");

        if (player.hasPermission("enchantcore.crystals.admin")) {
            ChatUtil.sendMessage(player, "&c/crystals give <player> <type> <tier> <amount> &7- Give crystals");
        }

        ChatUtil.sendMessage(player, "&8&m--------------------------------");
        ChatUtil.sendMessage(player, "&7Crystal Types: &eToken&7, &bGem&7, &dProc&7, &aRank&7, &6Pickaxe_XP&7, &9Pet&7, &cSalvage");
        ChatUtil.sendMessage(player, "&7Tiers: &f1-10 &7(Higher tiers = stronger bonuses)");
        ChatUtil.sendMessage(player, "&7Merging: &e3 crystals &7→ &e1 higher tier crystal");
        return true;
    }

    private boolean handleInfo(Player player) {
        double tokenBoost = crystalManager.getTokenMultiplier(player) * 100;
        double gemBoost = crystalManager.getGemMultiplier(player) * 100;
        double procBoost = crystalManager.getProcMultiplier(player) * 100;
        double rankBoost = crystalManager.getRankMultiplier(player) * 100;
        double pickaxeXpBoost = crystalManager.getPickaxeXpMultiplier(player) * 100;
        double petBoost = crystalManager.getPetMultiplier(player) * 100;
        double salvageBoost = crystalManager.getSalvageMultiplier(player) * 100;

        ChatUtil.sendMessage(player, "&8&m--------------------------------");
        ChatUtil.sendMessage(player, "&b&lCRYSTAL STATISTICS");
        ChatUtil.sendMessage(player, "&8&m--------------------------------");
        ChatUtil.sendMessage(player, "&eToken Boost: &a+" + String.format("%.1f", tokenBoost) + "%");
        ChatUtil.sendMessage(player, "&bGem Boost: &a+" + String.format("%.1f", gemBoost) + "%");
        ChatUtil.sendMessage(player, "&dProc Boost: &a+" + String.format("%.1f", procBoost) + "%");
        ChatUtil.sendMessage(player, "&aRank Boost: &a+" + String.format("%.1f", rankBoost) + "%");
        ChatUtil.sendMessage(player, "&6Pickaxe XP Boost: &a+" + String.format("%.1f", pickaxeXpBoost) + "%");
        ChatUtil.sendMessage(player, "&9Pet Boost: &a+" + String.format("%.1f", petBoost) + "%");
        ChatUtil.sendMessage(player, "&cSalvage Boost: &a+" + String.format("%.1f", salvageBoost) + "%");
        ChatUtil.sendMessage(player, "&8&m--------------------------------");

        return true;
    }

    private boolean handleGive(Player player, String[] args) {
        if (args.length < 5) {
            ChatUtil.sendMessage(player, "&cUsage: /crystals give <player> <type> <tier> <amount>");
            ChatUtil.sendMessage(player, "&cTypes: TOKEN, GEM, PROC, RANK, PICKAXE_XP, PET, SALVAGE");
            ChatUtil.sendMessage(player, "&cTiers: 1-10");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            ChatUtil.sendMessage(player, "&cPlayer &e" + args[1] + " &cnot found!");
            return true;
        }

        String type = args[2].toUpperCase();
        if (!crystalManager.isValidCrystalType(type)) {
            ChatUtil.sendMessage(player, "&cInvalid crystal type! Valid types: TOKEN, GEM, PROC, RANK, PICKAXE_XP, PET, SALVAGE");
            return true;
        }

        try {
            int tier = Integer.parseInt(args[3]);
            int amount = Integer.parseInt(args[4]);

            if (tier < 1 || tier > 10) {
                ChatUtil.sendMessage(player, "&cTier must be between 1 and 10!");
                return true;
            }

            if (amount < 1) {
                ChatUtil.sendMessage(player, "&cAmount must be at least 1!");
                return true;
            }

            crystalManager.giveCrystal(target, type, tier, amount);
            ChatUtil.sendMessage(player, "&aSuccessfully gave &e" + amount + "&ax &b" +
                    crystalManager.getDisplayName(type) + " &aTier &e" + tier + " &acrystal(s) to &e" + target.getName() + "&a!");

        } catch (NumberFormatException e) {
            ChatUtil.sendMessage(player, "&cTier and amount must be valid numbers!");
        }

        return true;
    }

    private boolean handleMerge(Player player, String[] args) {
        if (args.length < 3) {
            ChatUtil.sendMessage(player, "&cUsage: /crystals merge <type> <tier>");
            ChatUtil.sendMessage(player, "&cExample: /crystals merge TOKEN 5");
            return true;
        }

        String type = args[1].toUpperCase();
        if (!crystalManager.isValidCrystalType(type)) {
            ChatUtil.sendMessage(player, "&cInvalid crystal type! Valid types: TOKEN, GEM, PROC, RANK, PICKAXE_XP, PET, SALVAGE");
            return true;
        }

        try {
            int tier = Integer.parseInt(args[2]);
            if (tier < 1 || tier >= 10) {
                ChatUtil.sendMessage(player, "&cCan only merge crystals from tier 1 to 9!");
                return true;
            }

            if (crystalManager.mergeCrystals(player, type, tier)) {
                // Success message is handled in the manager
            } else {
                ChatUtil.sendMessage(player, "&cYou need at least 3 unequipped &b" +
                        crystalManager.getDisplayName(type) + " &cTier &e" + tier + " &ccrystals to merge!");
            }

        } catch (NumberFormatException e) {
            ChatUtil.sendMessage(player, "&cTier must be a valid number!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(Arrays.asList("info", "merge", "help"));
            if (sender.hasPermission("enchantcore.crystals.admin")) {
                subcommands.add("give");
            }

            for (String subcmd : subcommands) {
                if (subcmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subcmd);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("enchantcore.crystals.admin")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("merge")) {
                for (String type : crystalManager.getValidCrystalTypes()) {
                    if (type.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(type);
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("enchantcore.crystals.admin")) {
                for (String type : crystalManager.getValidCrystalTypes()) {
                    if (type.toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(type);
                    }
                }
            } else if (args[0].equalsIgnoreCase("merge")) {
                for (int i = 1; i <= 9; i++) {
                    if (String.valueOf(i).startsWith(args[2])) {
                        completions.add(String.valueOf(i));
                    }
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give") && sender.hasPermission("enchantcore.crystals.admin")) {
            for (int i = 1; i <= 10; i++) {
                if (String.valueOf(i).startsWith(args[3])) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        return completions;
    }
}