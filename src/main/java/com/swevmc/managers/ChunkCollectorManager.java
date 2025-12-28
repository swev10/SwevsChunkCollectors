package com.swevmc.managers;

import com.swevmc.scc;
import com.swevmc.models.ChunkCollector;
import com.swevmc.utils.DataManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkCollectorManager {

    private final scc plugin;
    private final DataManager dataManager;
    private final Map<UUID, ChunkCollector> collectors;
    private final Map<String, UUID> chunkCollectors;
    private final Map<UUID, Integer> playerCollectorCount;
    private BukkitTask collectionTask;
    private BukkitTask tickTask;
    private int particleTickCounter = 0;

    public ChunkCollectorManager(scc plugin) {
        this.plugin = plugin;
        this.dataManager = new DataManager(plugin);
        this.collectors = new ConcurrentHashMap<>();
        this.chunkCollectors = new ConcurrentHashMap<>();
        this.playerCollectorCount = new ConcurrentHashMap<>();
    }

    public void startTasks() {
        collectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                collectItems();
            }
        }.runTaskTimer(plugin, 0, plugin.getConfigManager().getCollectionSpeed());

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickCollectors();
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    public void shutdown() {
        if (collectionTask != null) {
            collectionTask.cancel();
        }
        if (tickTask != null) {
            tickTask.cancel();
        }

        for (ChunkCollector collector : collectors.values()) {
            collector.removeHologram();
        }
    }

    public boolean placeCollector(Player player, Location location) {
        if (!player.hasPermission("chunkcollector.use")) {
            return false;
        }

        if (!player.hasPermission("chunkcollector.bypass")) {
            int currentCount = playerCollectorCount.getOrDefault(player.getUniqueId(), 0);
            int maxCollectors = getMaxCollectorsForPlayer(player);
            if (currentCount >= maxCollectors) {
                return false;
            }
        }

        String chunkKey = getChunkKey(location);
        if (chunkCollectors.containsKey(chunkKey)) {
            return false;
        }

        ChunkCollector collector = new ChunkCollector(
                player.getUniqueId(),
                player.getName(),
                location);

        Material blockMaterial = getCollectorBlockMaterial();
        Material previousBlockType = location.getBlock().getType();

        plugin.getLogger().info("Placing collector at " + location.getWorld().getName() +
                " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                " - Previous block: " + previousBlockType + ", Setting to: " + blockMaterial);

        location.getBlock().setType(blockMaterial);

        collectors.put(collector.getUuid(), collector);
        chunkCollectors.put(chunkKey, collector.getUuid());
        playerCollectorCount.merge(player.getUniqueId(), 1, Integer::sum);

        collector.createHologram();

        try {
            location.getWorld().playSound(location,
                    org.bukkit.Sound.valueOf(plugin.getConfigManager().getSound("collector-place")),
                    1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.0f);
        }

        location.getWorld().spawnParticle(
                org.bukkit.Particle.valueOf(plugin.getConfigManager().getEffect("collector-place-particle")),
                location.clone().add(0.5, 1, 0.5), 20, 0.5, 0.5, 0.5, 0.1);

        return true;
    }

    public boolean removeCollector(Player player, Location location) {
        String chunkKey = getChunkKey(location);
        UUID collectorUuid = chunkCollectors.get(chunkKey);

        if (collectorUuid == null) {
            return false;
        }

        ChunkCollector collector = collectors.get(collectorUuid);
        if (collector == null) {
            return false;
        }

        if (!collector.isOwner(player)) {
            return false;
        }

        collectors.remove(collectorUuid);
        chunkCollectors.remove(chunkKey);
        playerCollectorCount.merge(player.getUniqueId(), -1, Integer::sum);

        collector.removeHologram();
        plugin.getHologramManager().removeHologramByLocation(location);

        location.getBlock().setType(Material.AIR);

        ItemStack collectorItem = createCollectorItem(1);
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), collectorItem);
        } else {
            player.getInventory().addItem(collectorItem);
        }

        try {
            location.getWorld().playSound(location,
                    org.bukkit.Sound.valueOf(plugin.getConfigManager().getSound("collector-remove")),
                    1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);

        location.getWorld().spawnParticle(
                org.bukkit.Particle.valueOf(plugin.getConfigManager().getEffect("collector-break-particle")),
                location.clone().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0.1);

        return true;
    }

    public ChunkCollector getCollector(Location location) {
        String chunkKey = getChunkKey(location);
        UUID collectorUuid = chunkCollectors.get(chunkKey);
        return collectorUuid != null ? collectors.get(collectorUuid) : null;
    }

    public ChunkCollector getCollector(UUID uuid) {
        return collectors.get(uuid);
    }

    public Collection<ChunkCollector> getCollectors() {
        return collectors.values();
    }

    public Collection<ChunkCollector> getPlayerCollectors(UUID playerUuid) {
        return collectors.values().stream()
                .filter(collector -> collector.getOwnerUuid().equals(playerUuid))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public int getPlayerCollectorCount(UUID playerUuid) {
        return playerCollectorCount.getOrDefault(playerUuid, 0);
    }

    private void collectItems() {
        for (ChunkCollector collector : collectors.values()) {
            if (!collector.isActive())
                continue;

            Location location = collector.getLocation();
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;

            if (!location.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            Chunk chunk = location.getChunk();

            for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
                if (!(entity instanceof Item))
                    continue;

                Item item = (Item) entity;
                Location itemLocation = item.getLocation();

                if (item.getOwner() != null)
                    continue;

                int y = itemLocation.getBlockY();
                if (y < plugin.getConfigManager().getMinCollectionHeight() ||
                        y > plugin.getConfigManager().getMaxCollectionHeight()) {
                    continue;
                }

                Material material = item.getItemStack().getType();
                if (!plugin.getConfigManager().getCollectibleItems().contains(material)) {
                    continue;
                }

                collector.collectItem(item.getItemStack(), itemLocation);
                item.remove();
            }
        }
    }

    private void tickCollectors() {
        particleTickCounter++;

        for (ChunkCollector collector : collectors.values()) {
            collector.tick();

            int frequency = plugin.getConfigManager().getHappyVillagerParticleFrequency();
            if (collector.isActive() && particleTickCounter % frequency == 0) {
                Location loc = collector.getLocation();
                if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    continue;
                }

                loc.getWorld().spawnParticle(
                        org.bukkit.Particle.HAPPY_VILLAGER,
                        loc.clone().add(0.5, 1.5, 0.5),
                        3,
                        0.8, 0.5, 0.8,
                        0.0);
            }
        }
    }

    private String getChunkKey(Location location) {
        return location.getWorld().getName() + ":" + (location.getBlockX() >> 4) + ":" + (location.getBlockZ() >> 4);
    }

    private Material getCollectorBlockMaterial() {
        String blockType = plugin.getConfigManager().getBlockType();

        switch (blockType) {
            case "BEACON":
            default:
                return Material.BEACON;
        }
    }

    public void loadCollectors() {
        List<ChunkCollector> loadedCollectors = dataManager.loadCollectors();

        plugin.getLogger().info("Loading " + loadedCollectors.size() + " collectors...");

        for (ChunkCollector collector : loadedCollectors) {
            collectors.put(collector.getUuid(), collector);
            chunkCollectors.put(getChunkKey(collector.getLocation()), collector.getUuid());
            playerCollectorCount.merge(collector.getOwnerUuid(), 1, Integer::sum);

            Location location = collector.getLocation();
            Material blockMaterial = getCollectorBlockMaterial();
            Material currentBlockType = location.getBlock().getType();

            plugin.getLogger().info("Loading collector at " + location.getWorld().getName() +
                    " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                    " - Current block: " + currentBlockType + ", Setting to: " + blockMaterial);

            if (currentBlockType != blockMaterial) {
                location.getBlock().setType(blockMaterial);
                plugin.getLogger().info("Changed block from " + currentBlockType + " to " + blockMaterial);
            } else {
                plugin.getLogger().info("Block already correct type: " + blockMaterial);
            }

            collector.createHologram();
        }

        startTasks();
    }

    public void saveCollectors() {
        dataManager.saveCollectors(new ArrayList<>(collectors.values()));
    }

    private ItemStack createCollectorItem(int amount) {
        Material itemMaterial = getCollectorItemMaterial();
        ItemStack item = new ItemStack(itemMaterial, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6§lChunk Collector");

        List<String> lore = new ArrayList<>();
        lore.add("§7Right-click to place this collector");
        lore.add("§7Collects items automatically in the chunk");
        lore.add("§7Default charge time: §e" + plugin.getConfigManager().getDefaultChargeMinutes() + " minutes");
        double costPerHour = plugin.getConfigManager().getRechargeCostPerMinute() * 60;
        lore.add("§7Recharge cost: §a$" + String.format("%.2f", costPerHour) + " per hour");
        lore.add("");
        lore.add("§8§l[CLICK TO PLACE]");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private Material getCollectorItemMaterial() {
        String blockType = plugin.getConfigManager().getBlockType();

        switch (blockType) {
            case "BEACON":
            default:
                return Material.BEACON;
        }
    }

    public int getMaxCollectorsForPlayer(Player player) {
        if (player.hasPermission("chunkcollectors.max.unlimited")) {
            return Integer.MAX_VALUE;
        }

        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("chunkcollectors.max." + i)) {
                return i;
            }
        }

        return plugin.getConfigManager().getMaxCollectorsPerPlayer();
    }
}
