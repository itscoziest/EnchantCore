package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.gui.EnchantGUI;
import com.strikesenchantcore.gui.EnchantGUIListener;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.ColorUtils;
import com.strikesenchantcore.util.PDCUtil;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerInteractListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager;
    private final MessageManager messageManager;
    private final Logger logger;

    public PlayerInteractListener(EnchantCore plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger();

        if (this.dataManager == null || this.messageManager == null) {
            logger.severe("PlayerDataManager or MessageManager is null in PlayerInteractListener! GUI opening may fail.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (dataManager == null || messageManager == null) {
            return;
        }

        Player player = event.getPlayer();
        Action action = event.getAction();

        if ((action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // *** FIX: DO NOT OPEN GUI IF SNEAKING (for Overcharge) ***
        if (player.isSneaking()) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (PDCUtil.isEnchantCorePickaxe(item)) {
            if (BlockBreakListener.nukeActivePlayers.contains(player.getUniqueId())) {
                ChatUtil.sendMessage(player, messageManager.getMessage("listeners.nuke.interact_denied", "&cYou cannot use pickaxe abilities while Nuke is active!"));
                event.setCancelled(true);
                return;
            }

            if (action == Action.RIGHT_CLICK_BLOCK) {
                Block clickedBlock = event.getClickedBlock();
                if (clickedBlock != null && clickedBlock.getType().isInteractable()) {
                    if (!player.isSneaking()) {
                        if (isCommonInteractable(clickedBlock.getType())) {
                            return;
                        }
                    }
                }
            }

            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);

            if (!player.hasPermission("enchantcore.gui.open")) {
                ChatUtil.sendMessage(player, messageManager.getMessage("gui.no_permission", "&cNo permission to open enchant menu."));
                return;
            }

            PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
            if (playerData == null) {
                playerData = dataManager.loadPlayerData(player.getUniqueId());
                if (playerData == null) {
                    ChatUtil.sendMessage(player, messageManager.getMessage("gui.data_error", "&cCould not load your data to open the menu."));
                    logger.warning("Failed to load PlayerData for " + player.getName() + " when opening GUI.");
                    return;
                }
            }

            try {
                EnchantGUI gui = new EnchantGUI(plugin, player, playerData, item);
                gui.open();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error opening EnchantGUI for player " + player.getName(), e);
                ChatUtil.sendMessage(player, messageManager.getMessage("gui.open_error", "&cAn error occurred while opening the enchant menu."));
            }
        }
    }

    private boolean isCommonInteractable(Material material) {
        if (material == null || !material.isInteractable()) {
            return false;
        }
        String name = material.toString();

        if (name.contains("CHEST") || name.equals("BARREL") || name.contains("SHULKER_BOX") ||
                name.equals("CRAFTING_TABLE") || name.equals("FURNACE") || name.equals("BLAST_FURNACE") ||
                name.equals("SMOKER") || name.equals("ANVIL") || name.equals("ENCHANTING_TABLE") ||
                name.equals("BREWING_STAND") || name.equals("GRINDSTONE") || name.equals("LOOM") ||
                name.equals("CARTOGRAPHY_TABLE") || name.equals("STONECUTTER") || name.equals("SMITHING_TABLE") ||
                name.equals("LECTERN") || name.equals("BEACON") || name.equals("JUKEBOX") || name.equals("NOTE_BLOCK") ||
                name.contains("DOOR") || name.contains("GATE") || name.contains("TRAPDOOR") ||
                name.contains("BUTTON") || name.equals("LEVER") || name.contains("PRESSURE_PLATE") ||
                name.equals("COMPARATOR") || name.equals("REPEATER") || name.equals("DAYLIGHT_DETECTOR") ||
                name.equals("TRIPWIRE_HOOK") || name.contains("_BED") || name.equals("BELL") ||
                name.equals("RESPAWN_ANCHOR") || name.equals("COMPOSTER") || name.equals("CAULDRON"))
        {
            return true;
        } else {
            return false;
        }
    }
}