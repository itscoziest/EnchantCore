package com.strikesenchantcore.commands;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.gui.AttachmentsGUI;
import com.strikesenchantcore.managers.AttachmentManager;
import com.strikesenchantcore.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AttachmentsCommand implements CommandExecutor, TabCompleter {

    private final EnchantCore plugin;
    private final AttachmentManager attachmentManager;

    public AttachmentsCommand(EnchantCore plugin) {
        this.plugin = plugin;
        this.attachmentManager = plugin.getAttachmentManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            // Default: open GUI for players
            if (sender instanceof Player) {
                openAttachmentsGUI((Player) sender);
            } else {
                sendUsage(sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "gui":
                if (!(sender instanceof Player)) {
                    ChatUtil.sendMessage(sender, "&cThis command can only be used by players!");
                    return true;
                }
                openAttachmentsGUI((Player) sender);
                break;

            case "mergeall":
                if (!(sender instanceof Player)) {
                    ChatUtil.sendMessage(sender, "&cThis command can only be used by players!");
                    return true;
                }
                attachmentManager.mergeAllPossible((Player) sender);
                break;

            case "give":
                handleGiveCommand(sender, args);
                break;

            case "testsave":
                if (!(sender instanceof Player)) {
                    ChatUtil.sendMessage(sender, "&cThis command can only be used by players!");
                    return true;
                }

                Player p = (Player) sender;
                com.strikesenchantcore.data.PlayerData pd = plugin.getPlayerDataManager().getPlayerData(p.getUniqueId());
                if (pd != null) {
                    // Test mortar save
                    // --- THIS IS THE FIX ---
                    pd.getMortarData().setLevel(5);
                    // --- END FIX ---
                    plugin.getPlayerDataManager().savePlayerData(pd, false); // Force sync save
                    ChatUtil.sendMessage(p, "&aMortar level set to 5 and saved!");
                }
                break;

            case "directsave":
                if (!(sender instanceof Player)) {
                    ChatUtil.sendMessage(sender, "&cThis command can only be used by players!");
                    return true;
                }

                Player player = (Player) sender;
                com.strikesenchantcore.data.PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                if (playerData != null) {
                    // Direct save to player data file
                    File playerFile = new File(plugin.getDataFolder(), "playerdata/" + player.getUniqueId().toString() + ".yml");
                    org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(playerFile);

                    // Manually add attachment data
                    config.set("attachments.stored.tier_1", 5);
                    config.set("attachments.stored.tier_2", 3);
                    config.set("attachments.equipped.slot_0", 2);

                    try {
                        config.save(playerFile);
                        ChatUtil.sendMessage(player, "&aDirect attachment save completed!");
                    } catch (Exception e) {
                        ChatUtil.sendMessage(player, "&cDirect save failed: " + e.getMessage());
                    }
                }
                break;

            case "givebox":
                handleGiveBoxCommand(sender, args);
                break;

            case "stats":
                if (!(sender instanceof Player)) {
                    ChatUtil.sendMessage(sender, "&cThis command can only be used by players!");
                    return true;
                }
                showStats((Player) sender);
                break;

            case "help":
            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void openAttachmentsGUI(Player player) {
        try {
            new AttachmentsGUI(plugin, player).open();
        } catch (Exception e) {
            plugin.getLogger().severe("Error opening AttachmentsGUI for " + player.getName() + ": " + e.getMessage());
            ChatUtil.sendMessage(player, "&cError opening attachments menu!");
        }
    }

    private void handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("enchantcore.attachments.admin")) {
            ChatUtil.sendMessage(sender, "&cYou don't have permission to use this command!");
            return;
        }

        if (args.length < 4) {
            ChatUtil.sendMessage(sender, "&cUsage: /attachments give <player> <tier> <amount>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            ChatUtil.sendMessage(sender, "&cPlayer not found or offline!");
            return;
        }

        int tier;
        int amount;
        try {
            tier = Integer.parseInt(args[2]);
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            ChatUtil.sendMessage(sender, "&cInvalid tier or amount!");
            return;
        }

        if (tier < 1 || tier > AttachmentManager.MAX_TIER) {
            ChatUtil.sendMessage(sender, "&cTier must be between 1 and " + AttachmentManager.MAX_TIER + "!");
            return;
        }

        if (amount < 1 || amount > 1000) {
            ChatUtil.sendMessage(sender, "&cAmount must be between 1 and 1000!");
            return;
        }

        AttachmentManager.AttachmentStorage storage = attachmentManager.getPlayerStorage(target.getUniqueId());
        storage.addAttachment(tier, amount);

        ChatUtil.sendMessage(sender, "&aGave " + amount + "x Tier " + tier + " attachments to " + target.getName());
        ChatUtil.sendMessage(target, "&6You received " + amount + "x Tier " + tier + " attachments!");
    }

    private void handleGiveBoxCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("enchantcore.attachments.admin")) {
            ChatUtil.sendMessage(sender, "&cYou don't have permission to use this command!");
            return;
        }

        if (args.length < 2) {
            ChatUtil.sendMessage(sender, "&cUsage: /attachments givebox <player> [amount]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            ChatUtil.sendMessage(sender, "&cPlayer not found or offline!");
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                ChatUtil.sendMessage(sender, "&cInvalid amount!");
                return;
            }
        }

        if (amount < 1 || amount > 64) {
            ChatUtil.sendMessage(sender, "&cAmount must be between 1 and 64!");
            return;
        }

        attachmentManager.giveAttachmentBox(target, amount);
        ChatUtil.sendMessage(sender, "&aGave " + amount + "x Attachment Box(es) to " + target.getName());
    }

    private void showStats(Player player) {
        AttachmentManager.AttachmentStorage storage = attachmentManager.getPlayerStorage(player.getUniqueId());
        double totalBonus = attachmentManager.getTotalProcBonus(player.getUniqueId());

        ChatUtil.sendMessage(player, "&6&l=== ATTACHMENT STATS ===");
        ChatUtil.sendMessage(player, "&eEquipped: &6" + storage.getEquippedCount() + "&7/&6" + AttachmentManager.MAX_EQUIPPED_ATTACHMENTS);
        ChatUtil.sendMessage(player, "&eTotal Proc Bonus: &6+" + String.format("%.2f", totalBonus * 100) + "%");

        ChatUtil.sendMessage(player, "&6&lEquipped Attachments:");
        for (int slot = 0; slot < AttachmentManager.MAX_EQUIPPED_ATTACHMENTS; slot++) {
            Integer tier = storage.getEquippedAttachment(slot);
            if (tier != null) {
                double bonus = attachmentManager.getProcBonusForTier(tier);
                ChatUtil.sendMessage(player, "&e  Slot " + (slot + 1) + ": &6Tier " + tier + " &7(+" + String.format("%.1f", bonus * 100) + "%)");
            }
        }

        ChatUtil.sendMessage(player, "&6&lStored Attachments:");
        for (int tier = 1; tier <= AttachmentManager.MAX_TIER; tier++) {
            int count = storage.getAttachmentCount(tier);
            if (count > 0) {
                ChatUtil.sendMessage(player, "&e  Tier " + tier + ": &6" + count + " attachments");
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        ChatUtil.sendMessage(sender, "&6&lAttachments Commands:");
        ChatUtil.sendMessage(sender, "&e/attachments &7- Open attachments GUI");
        ChatUtil.sendMessage(sender, "&e/attachments gui &7- Open attachments GUI");
        ChatUtil.sendMessage(sender, "&e/attachments mergeall &7- Merge all possible attachments");
        ChatUtil.sendMessage(sender, "&e/attachments stats &7- Show attachment statistics");

        if (sender.hasPermission("enchantcore.attachments.admin")) {
            ChatUtil.sendMessage(sender, "&c&lAdmin Commands:");
            ChatUtil.sendMessage(sender, "&c/attachments give <player> <tier> <amount> &7- Give attachments");
            ChatUtil.sendMessage(sender, "&c/attachments givebox <player> [amount] &7- Give attachment boxes");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("gui", "mergeall", "stats", "help"));

            if (sender.hasPermission("enchantcore.attachments.admin")) {
                completions.addAll(Arrays.asList("give", "givebox"));
            }

            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2 && sender.hasPermission("enchantcore.attachments.admin")) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("givebox")) {
                return null; // Returns online player names
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("enchantcore.attachments.admin")) {
            List<String> tiers = new ArrayList<>();
            for (int i = 1; i <= AttachmentManager.MAX_TIER; i++) {
                tiers.add(String.valueOf(i));
            }
            return filterCompletions(tiers, args[2]);
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