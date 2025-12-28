package com.swevmc.listeners;

import com.swevmc.scc;
import com.swevmc.gui.CollectorGUI;
import com.swevmc.models.ChunkCollector;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ChunkCollectorListener implements Listener {

    private final scc plugin;
    private final CollectorGUI gui;

    public ChunkCollectorListener(scc plugin) {
        this.plugin = plugin;
        this.gui = new CollectorGUI(plugin);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (isCollectorItem(item)) {

            if (!player.hasPermission("chunkcollector.use")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("no-permission"));
                return;
            }

            if (plugin.getChunkCollectorManager().placeCollector(player, event.getBlock().getLocation())) {
                player.sendMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("collector-placed"));
            } else {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("collector-already-exists"));
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (isCollectorBlock(event.getBlock())) {
            ChunkCollector collector = plugin.getChunkCollectorManager().getCollector(event.getBlock().getLocation());

            if (collector != null) {
                if (!collector.isOwner(player)) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("collector-not-yours"));
                    return;
                }

                if (plugin.getChunkCollectorManager().removeCollector(player, event.getBlock().getLocation())) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("collector-removed"));
                } else {
                    plugin.getHologramManager().removeHologramByLocation(event.getBlock().getLocation());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();

        if (isCollectorBlock(event.getClickedBlock())) {
            ChunkCollector collector = plugin.getChunkCollectorManager()
                    .getCollector(event.getClickedBlock().getLocation());

            if (collector != null) {
                if (!collector.isOwner(player)) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("collector-not-yours"));
                    return;
                }

                gui.openGUI(player, collector);
                event.setCancelled(true);
            }
        }
    }

    private boolean isCollectorBlock(Material material) {
        String blockType = plugin.getConfigManager().getBlockType();

        switch (blockType) {
            case "BEACON":
            default:
                return material == Material.BEACON;
        }
    }

    private boolean isCollectorBlock(Block block) {
        return isCollectorBlock(block.getType());
    }

    private boolean isCollectorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String displayName = item.getItemMeta().getDisplayName();
        if (!displayName.contains("Chunk Collector")) {
            return false;
        }

        String blockType = plugin.getConfigManager().getBlockType();

        switch (blockType) {
            case "BEACON":
            default:
                return item.getType() == Material.BEACON;
        }
    }
}
