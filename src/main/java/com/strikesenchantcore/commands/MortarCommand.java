package com.strikesenchantcore.commands;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.gui.MortarGUI;
import com.strikesenchantcore.managers.MortarManager;
import com.strikesenchantcore.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MortarCommand implements CommandExecutor, TabCompleter {

    private final EnchantCore plugin;
    private final MortarManager mortarManager;

    public MortarCommand(EnchantCore plugin) {
        this.plugin = plugin;
        this.mortarManager = plugin.getMortarManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            ChatUtil.sendMessage(sender, "&cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Open Mortar GUI
            openMortarGUI(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "gui":
                openMortarGUI(player);
                break;
            case "upgrade":
                handleUpgrade(player);
                break;
            case "stats":
                showStats(player);
                break;
            case "testsave":
                if (plugin.getMortarManager() != null) {
                    com.strikesenchantcore.managers.MortarManager.MortarData mortarData = plugin.getMortarManager().getMortarData(player.getUniqueId());
                    mortarData.setLevel(3);
                    ChatUtil.sendMessage(player, "&aMortar level set to 3 in manager");

                    // Force save
                    com.strikesenchantcore.data.PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                    if (pd != null) {
                        plugin.getPlayerDataManager().savePlayerData(pd, false);
                        ChatUtil.sendMessage(player, "&aForced player data save");
                    }
                }
                break;
            default:
                sendUsage(player);
                break;
        }

        return true;
    }

    private void openMortarGUI(Player player) {
        try {
            new MortarGUI(plugin, player).open();
        } catch (Exception e) {
            plugin.getLogger().severe("Error opening MortarGUI for " + player.getName() + ": " + e.getMessage());
            ChatUtil.sendMessage(player, "&cError opening mortar menu!");
        }
    }

    private void handleUpgrade(Player player) {
        boolean success = mortarManager.upgradeMortar(player);
        if (!success) {
            ChatUtil.sendMessage(player, "&cCannot upgrade mortar! Check requirements in the GUI.");
        }
    }

    private void showStats(Player player) {
        UUID playerId = player.getUniqueId();
        MortarManager.MortarData mortarData = mortarManager.getMortarData(playerId);

        ChatUtil.sendMessage(player, "&6&l=== MORTAR STATS ===");
        ChatUtil.sendMessage(player, "&eLevel: &6" + mortarData.getLevel());

        if (mortarData.getLevel() == 0) {
            ChatUtil.sendMessage(player, "&cNo mortar unlocked yet!");
            return;
        }

        // Cooldown info
        if (mortarManager.isOnCooldown(playerId)) {
            long remaining = mortarManager.getCooldownRemaining(playerId);
            ChatUtil.sendMessage(player, "&eCooldown: &c" + formatTime(remaining));
        } else {
            ChatUtil.sendMessage(player, "&eCooldown: &aReady!");
        }

        // Last activation
        if (mortarData.getLastActivation() > 0) {
            long timeSince = System.currentTimeMillis() - mortarData.getLastActivation();
            ChatUtil.sendMessage(player, "&eLast Activation: &6" + formatTime(timeSince) + " ago");
        } else {
            ChatUtil.sendMessage(player, "&eLast Activation: &7Never");
        }

        // Active boost
        if (mortarManager.hasActiveBoost(playerId)) {
            double multiplier = mortarManager.getActiveBoostMultiplier(playerId);
            long boostRemaining = mortarData.getBoostEndTime() - System.currentTimeMillis();
            ChatUtil.sendMessage(player, "&eActive Boost: &6" + String.format("%.1f", multiplier) + "x &efor &6" + formatTime(boostRemaining));
        } else {
            ChatUtil.sendMessage(player, "&eActive Boost: &7None");
        }

        // Upgrades
        ChatUtil.sendMessage(player, "&6&lUpgrades:");
        for (MortarManager.MortarUpgrade upgrade : MortarManager.MortarUpgrade.values()) {
            int level = mortarData.getUpgradeLevel(upgrade);
            ChatUtil.sendMessage(player, "&e" + upgrade.getDisplayName() + ": &6" + level + "&7/&6" + upgrade.getMaxLevel());
        }
    }

    private void sendUsage(Player player) {
        ChatUtil.sendMessage(player, "&6&lMortar Commands:");
        ChatUtil.sendMessage(player, "&e/mortar &7- Open Mortar GUI");
        ChatUtil.sendMessage(player, "&e/mortar gui &7- Open Mortar GUI");
        ChatUtil.sendMessage(player, "&e/mortar upgrade &7- Upgrade mortar level");
        ChatUtil.sendMessage(player, "&e/mortar stats &7- Show mortar statistics");
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + "s";
        } else {
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return minutes + "m " + seconds + "s";
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = Arrays.asList("gui", "upgrade", "stats");
            return filterCompletions(completions, args[0]);
        }

        return new ArrayList<>();
    }

    private List<String> filterCompletions(List<String> completions, String input) {
        List<String> filtered = new ArrayList<>();
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(input.toLowerCase())) {
                filtered.add(completion);
            }
        }
        return filtered;
    }
}
