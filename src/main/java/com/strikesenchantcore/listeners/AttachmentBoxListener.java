package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.managers.AttachmentManager;
import com.strikesenchantcore.util.ChatUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AttachmentBoxListener implements Listener {

    private final EnchantCore plugin;
    private final AttachmentManager attachmentManager;

    public AttachmentBoxListener(EnchantCore plugin) {
        this.plugin = plugin;
        this.attachmentManager = plugin.getAttachmentManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isAttachmentBox(item)) {
            return;
        }

        event.setCancelled(true);

        try {
            attachmentManager.openAttachmentBox(player, item);
        } catch (Exception e) {
            plugin.getLogger().severe("Error opening attachment box for " + player.getName() + ": " + e.getMessage());
            ChatUtil.sendMessage(player, "&cError opening attachment box!");
        }
    }

    private boolean isAttachmentBox(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_CHEST) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        String displayName = ChatColor.stripColor(meta.getDisplayName());
        return displayName.equals("Attachment Box");
    }
}