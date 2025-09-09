package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.PDCUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnchantGUIListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager;
    private final Logger logger;

    public EnchantGUIListener(EnchantCore plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
        this.logger = plugin.getLogger();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder == null) {
            return;
        }

        // --- THIS LINE IS NOW FIXED ---
        if (holder instanceof EnchantGUI || holder instanceof GemsGUI || holder instanceof RebirthGUI) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                ItemStack pickaxeContext = null;
                if (holder instanceof EnchantGUI gui) {
                    pickaxeContext = gui.getPickaxe();
                } else if (holder instanceof GemsGUI gemsGUI) {
                    pickaxeContext = gemsGUI.getPickaxe();
                } else if (holder instanceof RebirthGUI rebirthGUI) { // ADDED
                    pickaxeContext = rebirthGUI.getPickaxe();     // ADDED
                }

                if (pickaxeContext == null || pickaxeContext.getType() == Material.AIR || !PDCUtil.isEnchantCorePickaxe(pickaxeContext)) {
                    ChatUtil.sendMessage(player, "&cError: Pickaxe context lost or invalid. Please reopen the menu.");
                    player.closeInventory();
                    return;
                }

                PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
                if (playerData == null && (playerData = dataManager.loadPlayerData(player.getUniqueId())) == null) {
                    ChatUtil.sendMessage(player, "&cError: Could not load your data. Please try again.");
                    player.closeInventory();
                    return;
                }

                try {
                    if (holder instanceof EnchantGUI gui) {
                        gui.handleClick(player, event.getRawSlot(), event.getCurrentItem(), pickaxeContext, event.getClick());
                    } else if (holder instanceof GemsGUI gemsGUI) {
                        gemsGUI.handleClick(player, event.getRawSlot(), event.getClick());
                    } else if (holder instanceof RebirthGUI rebirthGUI) { // ADDED
                        rebirthGUI.handleClick(player, event.getRawSlot(), event.getClick()); // ADDED
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error handling GUI click for " + player.getName(), e);
                    ChatUtil.sendMessage(player, "&cAn internal error occurred.");
                    player.closeInventory();
                }
            }
        }
    }
}