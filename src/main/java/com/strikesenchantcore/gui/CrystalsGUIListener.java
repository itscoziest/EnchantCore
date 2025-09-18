package com.strikesenchantcore.gui;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.managers.CrystalManager;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.ColorUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound;

import java.util.HashMap;
import java.util.Map;

public class CrystalsGUIListener implements Listener {
    private final EnchantCore plugin;
    private final CrystalManager crystalManager;
    private final Map<Player, CrystalsGUI> openGUIs;

    public CrystalsGUIListener(EnchantCore plugin) {
        this.plugin = plugin;
        this.crystalManager = plugin.getCrystalManager();
        this.openGUIs = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        String expectedTitle = ColorUtils.translateColors("&f\uE5A1\uE0AC");
        if (!title.contains("Crystal Storage") && !title.equals(expectedTitle)) return;

        event.setCancelled(true);

        CrystalsGUI gui = openGUIs.get(player);
        if (gui == null) {
            ChatUtil.sendMessage(player, "&cGUI registration lost! Please close and reopen the menu.");
            return;
        }

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        handleSlotClick(player, gui, slot, event.getClick());
    }

    private void handleSlotClick(Player player, CrystalsGUI gui, int slot, ClickType clickType) {
        switch (slot) {
            case 45: // Back button
                handleBackButton(player);
                break;
            // Equipped slots: 28, 29, 30, 31, 32, 33, 34
            case 28: handleEquippedClick(player, gui, 0); break;
            case 29: handleEquippedClick(player, gui, 1); break;
            case 30: handleEquippedClick(player, gui, 2); break;
            case 31: handleEquippedClick(player, gui, 3); break;
            case 32: handleEquippedClick(player, gui, 4); break;
            case 33: handleEquippedClick(player, gui, 5); break;
            case 34: handleEquippedClick(player, gui, 6); break;
            // Storage slots: 10, 11, 12, 13, 14, 15, 17
            case 10: handleStorageClick(player, gui, 0, clickType); break;
            case 11: handleStorageClick(player, gui, 1, clickType); break;
            case 12: handleStorageClick(player, gui, 2, clickType); break;
            case 13: handleStorageClick(player, gui, 3, clickType); break;
            case 14: handleStorageClick(player, gui, 4, clickType); break;
            case 15: handleStorageClick(player, gui, 5, clickType); break;
            case 17: handleStorageClick(player, gui, 6, clickType); break;
        }
    }

    private void handleBackButton(Player player) {
        try {
            final com.strikesenchantcore.data.PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (playerData != null) {
                ItemStack pickaxe = null;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.hasItemMeta() &&
                            item.getItemMeta().getPersistentDataContainer()
                                    .has(new org.bukkit.NamespacedKey(plugin, "enchantcore_pickaxe"),
                                            org.bukkit.persistence.PersistentDataType.BYTE)) {
                        pickaxe = item;
                        break;
                    }
                }

                final ItemStack finalPickaxe = pickaxe;
                final Player finalPlayer = player;

                if (finalPickaxe != null) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        new EnchantGUI(plugin, finalPlayer, playerData, finalPickaxe).open();
                    }, 1L);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    ChatUtil.sendMessage(player, "&cCould not find your EnchantCore pickaxe!");
                }
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                ChatUtil.sendMessage(player, "&cError loading player data!");
            }
        } catch (Exception e) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            ChatUtil.sendMessage(player, "&cError opening main menu!");
        }
    }

    private void handleEquippedClick(Player player, CrystalsGUI gui, int equipIndex) {
        int[] slots = {28, 29, 30, 31, 32, 33, 34};
        ItemStack clicked = gui.getInventory().getItem(slots[equipIndex]);

        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            ChatUtil.sendMessage(player, "&7Empty equipment slot!");
            return;
        }

        if (crystalManager.unequipCrystal(player, equipIndex)) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            ChatUtil.sendMessage(player, "&aSuccessfully unequipped crystal!");
            gui.refresh();
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            ChatUtil.sendMessage(player, "&cFailed to unequip crystal!");
        }
    }

    private void handleStorageClick(Player player, CrystalsGUI gui, int storageIndex, ClickType clickType) {
        Map<String, Integer> storageCrystals = crystalManager.getStorageCrystals(player);

        if (storageIndex >= storageCrystals.size()) {
            ChatUtil.sendMessage(player, "&7No crystal in this slot!");
            return;
        }

        String[] crystalKeys = storageCrystals.keySet().toArray(new String[0]);
        String crystalKey = crystalKeys[storageIndex];
        String[] parts = crystalKey.split("_");

        if (parts.length != 2) return;

        String type = parts[0];
        int tier;
        try {
            tier = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }

        int amount = storageCrystals.get(crystalKey);

        if (clickType.isRightClick()) {
            if (tier >= 10) {
                ChatUtil.sendMessage(player, "&cThis crystal is already at maximum tier!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } else if (amount < 3) {
                ChatUtil.sendMessage(player, "&cYou need at least 3 crystals to merge!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } else {
                if (crystalManager.mergeCrystals(player, type, tier)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
                    gui.refresh();
                } else {
                    ChatUtil.sendMessage(player, "&cFailed to merge crystals!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        } else {
            int emptySlot = findEmptyEquippedSlot(player);
            if (emptySlot == -1) {
                ChatUtil.sendMessage(player, "&cAll equipment slots are full! Unequip a crystal first.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } else {
                if (crystalManager.equipCrystal(player, type, tier, emptySlot)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    ChatUtil.sendMessage(player, "&aSuccessfully equipped crystal!");
                    gui.refresh();
                } else {
                    ChatUtil.sendMessage(player, "&cFailed to equip crystal!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }

    private int findEmptyEquippedSlot(Player player) {
        Map<Integer, String> equippedCrystals = crystalManager.getEquippedCrystals(player);
        for (int i = 0; i < 7; i++) {
            if (!equippedCrystals.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            openGUIs.remove((Player) event.getPlayer());
        }
    }

    public void registerGUI(Player player, CrystalsGUI gui) {
        openGUIs.put(player, gui);
    }
}