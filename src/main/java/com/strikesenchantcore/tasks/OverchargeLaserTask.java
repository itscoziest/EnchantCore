package com.strikesenchantcore.tasks;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.AutoSellConfig;
import com.strikesenchantcore.config.ConfigManager;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.listeners.PinataListener;
import com.strikesenchantcore.util.PDCUtil;
import com.strikesenchantcore.util.VaultHook;
import com.strikesenchantcore.util.WorldGuardHook;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class OverchargeLaserTask extends BukkitRunnable {

    private final Player player;
    private final PlayerData playerData;
    private final ItemStack pickaxe;
    private final int maxLength;
    private final EnchantCore plugin;
    private final WorldGuardHook worldGuardHook;
    private final VaultHook vaultHook;
    private final AutoSellConfig autoSellConfig;
    private final ConfigManager configManager;

    private final Location startLocation;
    private final Vector direction;
    private double distanceTraveled = 0;
    private final Set<Block> alreadyBroken = new HashSet<>();

    public OverchargeLaserTask(Player player, PlayerData playerData, ItemStack pickaxe, int maxLength, EnchantCore plugin) {
        this.player = player;
        this.playerData = playerData;
        this.pickaxe = pickaxe;
        this.maxLength = maxLength;
        this.plugin = plugin;

        this.worldGuardHook = plugin.getWorldGuardHook();
        this.vaultHook = plugin.getVaultHook();
        this.autoSellConfig = plugin.getAutoSellConfig();
        this.configManager = plugin.getConfigManager();

        this.startLocation = player.getEyeLocation();
        this.direction = player.getEyeLocation().getDirection().normalize();
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            this.cancel();
            return;
        }

        for (double i = 0; i < 2.0; i += 0.5) {
            Location currentPoint = startLocation.clone().add(direction.clone().multiply(distanceTraveled + i));

            Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 1.2F);
            player.getWorld().spawnParticle(Particle.REDSTONE, currentPoint, 15, 0.3, 0.3, 0.3, 0, dustOptions);
            player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, currentPoint, 2, 0.1, 0.1, 0.1, 0);

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block block = currentPoint.getBlock().getRelative(x, y, z);

                        if (alreadyBroken.contains(block) || !isBreakable(block)) {
                            continue;
                        }

                        if (worldGuardHook != null && !worldGuardHook.isEnchantAllowed(block.getLocation())) {
                            this.cancel();
                            return;
                        }

                        breakBlockWithLogic(block);
                        alreadyBroken.add(block);
                    }
                }
            }
        }

        distanceTraveled += 2.0;

        if (distanceTraveled >= maxLength) {
            this.cancel();
        }
    }

    private void breakBlockWithLogic(Block block) {
        Material originalMaterial = block.getType();
        boolean blockSold = false;
        boolean dropsCancelled = false;

        boolean autoSellEnabled = configManager.getConfig().getBoolean("AutoSell.Enabled", false);
        if (autoSellEnabled && vaultHook != null && vaultHook.isEnabled() && player.hasPermission("enchantcore.autosell")) {
            double price = autoSellConfig.getSellPrice(originalMaterial);
            if (price > 0) {
                Collection<ItemStack> drops = block.getDrops(pickaxe, player);
                int amount = drops.stream().mapToInt(ItemStack::getAmount).sum();
                if (amount == 0 && originalMaterial.isItem()) amount = 1;

                if (amount > 0) {
                    double boosterMultiplier = playerData.getBlockBoosterMultiplier();
                    double totalPrice = price * amount * boosterMultiplier;
                    if (vaultHook.deposit(player, totalPrice)) {
                        blockSold = true;
                        dropsCancelled = true;
                    }
                }
            }
        }

        if (!blockSold) {
            boolean autoPickupEnabled = configManager.getConfig().getBoolean("AutoPickup.Enabled", false);
            if (autoPickupEnabled && player.hasPermission("enchantcore.autopickup")) {
                Collection<ItemStack> drops = block.getDrops(pickaxe, player);
                if (!drops.isEmpty()) {
                    PlayerInventory inv = player.getInventory();
                    Map<Integer, ItemStack> leftovers = inv.addItem(drops.toArray(new ItemStack[0]));
                    if (!leftovers.isEmpty()) {
                        Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);
                        for (ItemStack leftoverItem : leftovers.values()) {
                            player.getWorld().dropItemNaturally(dropLocation, leftoverItem);
                        }
                    }
                    dropsCancelled = true;
                }
            }
        }

        if (dropsCancelled) {
            block.setType(Material.AIR);
        } else {
            block.breakNaturally(pickaxe);
        }

        playerData.addBlocksMined(1L);
        PDCUtil.setPickaxeBlocksMined(pickaxe, playerData.getBlocksMined());
    }

    private boolean isBreakable(Block block) {
        if (block == null) return false;
        if (PinataListener.activePinatas.containsKey(block.getLocation())) return false;
        Material t = block.getType();
        if (t.isAir() || t == Material.BEDROCK || t == Material.END_PORTAL_FRAME || t == Material.END_GATEWAY || t == Material.BARRIER) {
            return false;
        }
        return true;
    }
}