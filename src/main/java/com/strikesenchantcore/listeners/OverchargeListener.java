package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.tasks.OverchargeLaserTask;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.PDCUtil;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.TimeUnit;

public class OverchargeListener implements Listener {

    private final EnchantCore plugin;

    public OverchargeListener(EnchantCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Check for Shift + Right-Click
        if (!player.isSneaking() || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!PDCUtil.isEnchantCorePickaxe(item)) {
            return;
        }

        int level = plugin.getPickaxeManager().getEnchantLevel(item, "overcharge");
        if (level <= 0) {
            return;
        }

        event.setCancelled(true);

        EnchantmentWrapper enchant = plugin.getEnchantRegistry().getEnchant("overcharge");
        if (enchant == null || !enchant.isEnabled()) {
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return;
        }

        if (System.currentTimeMillis() < playerData.getOverchargeFireCooldownEnd()) {
            return;
        }

        ConfigurationSection settings = enchant.getCustomSettings();
        int base = settings.getInt("BlocksToChargeBase", 500);
        int decrease = settings.getInt("BlocksToChargeDecreasePerLevel", 10);
        int required = Math.max(1, base - (decrease * (level - 1)));

        if (playerData.getOverchargeCharge() < required) {
            return;
        }

        int beamLength = settings.getInt("BeamLength", 100);

        // This is the corrected constructor call that matches the new Laser Task
        new OverchargeLaserTask(player, playerData, item, beamLength, plugin).runTaskTimer(plugin, 0L, 1L);

        playerData.setOverchargeCharge(0);
        long cooldownMillis = TimeUnit.SECONDS.toMillis(settings.getInt("CooldownSeconds", 60));
        playerData.setOverchargeFireCooldownEnd(System.currentTimeMillis() + cooldownMillis);

        if (playerData.isShowEnchantMessages()) {
            ChatUtil.sendMessage(player, settings.getString("ActivationMessage", "&c&lOVERCHARGE! &7Fired a laser beam!"));
        }
        if (playerData.isShowEnchantSounds()) {
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
        }
    }
}