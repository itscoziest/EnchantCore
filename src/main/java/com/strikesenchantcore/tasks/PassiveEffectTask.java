package com.strikesenchantcore.tasks;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.PDCUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.concurrent.TimeUnit;


public class PassiveEffectTask extends BukkitRunnable {

    private final EnchantCore plugin;

    public PassiveEffectTask(EnchantCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // --- Other passive effects like speed/haste would go here ---


            // --- Overcharge Action Bar Logic ---
            handleOverchargeActionBar(player);
        }
    }

    private void handleOverchargeActionBar(Player player) {
        // Essential manager checks
        if (plugin.getPlayerDataManager() == null || plugin.getPickaxeManager() == null || plugin.getEnchantRegistry() == null) {
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return;
        }

        // *** FIX: Check the item in the player's main hand ***
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Check if the item in hand is a pickaxe with the Overcharge enchant
        if (!PDCUtil.isEnchantCorePickaxe(itemInHand)) {
            return; // Exit if not holding an EnchantCore pickaxe
        }

        int level = plugin.getPickaxeManager().getEnchantLevel(itemInHand, "overcharge");
        if (level <= 0) {
            return; // Exit if the pickaxe in hand doesn't have Overcharge
        }

        EnchantmentWrapper ench = plugin.getEnchantRegistry().getEnchant("overcharge");
        if (ench == null || !ench.isEnabled()) {
            return;
        }

        ConfigurationSection s = ench.getCustomSettings();
        int base = s.getInt("BlocksToChargeBase", 500);
        int decrease = s.getInt("BlocksToChargeDecreasePerLevel", 10);
        int required = Math.max(1, base - (decrease * (level - 1)));
        int current = playerData.getOverchargeCharge();

        String message;
        long cooldownEnd = playerData.getOverchargeFireCooldownEnd();

        if (System.currentTimeMillis() < cooldownEnd) {
            // On Cooldown
            long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(cooldownEnd - System.currentTimeMillis()) + 1;
            message = s.getString("ActionBarCooldown", "&cOn Cooldown &7(%time%s)");
            message = message.replace("%time%", String.valueOf(remainingSeconds));
        } else if (current >= required) {
            // Ready to fire
            message = s.getString("ActionBarReady", "&c&lOVERCHARGE READY!");
        } else {
            // Charging
            message = s.getString("ActionBarCharging", "&eCharge &6[%bar%&6] &7(&e%current%&7/&6%required%&7)");
            double ratio = Math.min(1.0, (double) current / required);
            int progressChars = (int) (ratio * 10);
            String bar = "&a" + String.join("", Collections.nCopies(progressChars, "❚")) +
                    "&7" + String.join("", Collections.nCopies(10 - progressChars, "❚"));

            message = message.replace("%bar%", bar)
                    .replace("%current%", String.valueOf(current))
                    .replace("%required%", String.valueOf(required));
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatUtil.color(message)));
    }
}