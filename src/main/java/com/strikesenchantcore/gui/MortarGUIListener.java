package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class MortarGUIListener implements Listener {

    private final EnchantCore plugin;

    public MortarGUIListener(EnchantCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        InventoryHolder holder = event.getInventory().getHolder();

        if (!(holder instanceof MortarGUI)) {
            return;
        }

        event.setCancelled(true); // Cancel all clicks in mortar GUI

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) {
            return; // Clicked outside the GUI or in player inventory
        }

        MortarGUI mortarGUI = (MortarGUI) holder;
        int slot = event.getSlot();

        try {
            mortarGUI.handleClick(slot);
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling MortarGUI click for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}