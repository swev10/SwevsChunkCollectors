package com.swevmc.gui;

import com.swevmc.scc;
import com.swevmc.models.ChunkCollector;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CollectorGUI implements Listener {

    private final scc plugin;

    public CollectorGUI(scc plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openGUI(Player player, ChunkCollector collector) {
        Inventory gui = Bukkit.createInventory(null, 27, plugin.getConfigManager().getGuiTitle());

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);

        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glass);
        }

        gui.setItem(13, collector.getGuiItem());

        ItemStack chargeButton = createChargeButton(collector, player);
        gui.setItem(12, chargeButton);

        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§cClose");
        closeButton.setItemMeta(closeMeta);
        gui.setItem(15, closeButton);

        player.openInventory(gui);

        try {

            @SuppressWarnings("deprecation")
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(plugin.getConfigManager().getSound("gui-open"));
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }

    private ItemStack createChargeButton(ChunkCollector collector, Player player) {
        ItemStack button = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = button.getItemMeta();

        List<String> lore = new ArrayList<>();

        if (collector.getTimeRemaining() < collector.getMaxDuration(player)) {
            meta.setDisplayName(plugin.getConfigManager().getGuiRechargeButton());
            lore.add("§7Click to add charge");

            long defaultChargeMinutes = plugin.getConfigManager().getDefaultChargeMinutes();
            double cost = defaultChargeMinutes * plugin.getConfigManager().getRechargeCostPerMinute();

            lore.add("§7Add: §e" + defaultChargeMinutes + " minutes");
            lore.add("§7Cost: §a$" + String.format("%.2f", cost));

            long maxDuration = collector.getMaxDuration(player);
            long timeRemaining = collector.getTimeRemaining();
            long space = maxDuration - timeRemaining;

            if (space < defaultChargeMinutes * 60) {
                lore.add("§e(Will fill to max)");
            }
        } else {
            meta.setDisplayName("§cCollector Full");
            lore.add("§7This collector is fully charged.");
        }

        meta.setLore(lore);
        button.setItemMeta(meta);

        return button;
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0)
            return String.format("%dh %dm", hours, minutes);
        return String.format("%dm", minutes);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.equals(plugin.getConfigManager().getGuiTitle())) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (clickedItem.getType() == Material.GOLD_INGOT) {
            ChunkCollector collector = null;
            for (ChunkCollector c : plugin.getChunkCollectorManager().getPlayerCollectors(player.getUniqueId())) {
                if (c.getLocation().distance(player.getLocation()) < 10) {
                    collector = c;
                    break;
                }
            }

            if (collector == null) {
                player.sendMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("collector-not-found"));
                return;
            }

            if (collector.addCharge(player)) {
                openGUI(player, collector);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        String title = event.getView().getTitle();
        if (title.equals(plugin.getConfigManager().getGuiTitle())) {
            event.setCancelled(true);
        }
    }
}
