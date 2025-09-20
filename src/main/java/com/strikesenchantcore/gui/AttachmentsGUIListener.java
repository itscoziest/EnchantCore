package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class AttachmentsGUIListener implements Listener {

    private final EnchantCore plugin;

    public AttachmentsGUIListener(EnchantCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        InventoryHolder holder = event.getInventory().getHolder();

        if (!(holder instanceof AttachmentsGUI)) {
            return;
        }

        event.setCancelled(true); // Cancel all clicks in attachments GUI

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) {
            return; // Clicked outside the GUI or in player inventory
        }

        AttachmentsGUI attachmentsGUI = (AttachmentsGUI) holder;
        int slot = event.getSlot();

        // Determine click type
        boolean isShiftClick = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
        boolean isRightClick = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT;

        try {
            attachmentsGUI.handleClick(slot, isShiftClick, isRightClick);
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling AttachmentsGUI click for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}